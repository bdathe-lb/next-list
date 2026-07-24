import {after, before, beforeEach, test} from "node:test";
import {
  assertFails,
  assertSucceeds,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  getBytes,
  ref,
  uploadBytes,
  uploadString,
} from "firebase/storage";
import {doc, setDoc, updateDoc} from "firebase/firestore";
import {createRulesTestEnvironment} from "./rulesTestEnvironment";

let testEnvironment: RulesTestEnvironment;

before(async () => {
  testEnvironment = await createRulesTestEnvironment();
});

beforeEach(async () => {
  await testEnvironment.clearStorage();
  await testEnvironment.clearFirestore();
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    await setDoc(doc(firestore, "groups/group-1"), {
      name: "周末去哪",
      adminId: "alice",
      status: "active",
      memberCount: 2,
      ideaCount: 0,
      scheduledCount: 0,
      completedCount: 0,
    });
    await setDoc(doc(firestore, "groups/group-1/members/alice"), {
      userId: "alice",
      groupId: "group-1",
      role: "admin",
      status: "active",
    });
    await setDoc(doc(firestore, "groups/group-1/members/bob"), {
      userId: "bob",
      groupId: "group-1",
      role: "member",
      status: "active",
    });
  });
});

after(async () => {
  await testEnvironment.cleanup();
});

test("a user can upload an image to their own avatar path", async () => {
  const storage = testEnvironment.authenticatedContext("alice").storage();
  const avatar = ref(storage, "users/alice/avatar/current.webp");

  await assertSucceeds(
    uploadString(avatar, "local-emulator-image", "raw", {
      contentType: "image/webp",
    }),
  );
});

test("a user cannot upload an avatar for someone else", async () => {
  const storage = testEnvironment.authenticatedContext("alice").storage();
  const avatar = ref(storage, "users/bob/avatar/current.webp");

  await assertFails(
    uploadString(avatar, "local-emulator-image", "raw", {
      contentType: "image/webp",
    }),
  );
});

test("unauthenticated uploads are denied", async () => {
  const storage = testEnvironment.unauthenticatedContext().storage();
  const avatar = ref(storage, "users/alice/avatar/current.webp");

  await assertFails(
    uploadString(avatar, "local-emulator-image", "raw", {
      contentType: "image/webp",
    }),
  );
});

test("avatar uploads reject non-WebP content and invalid file names", async () => {
  const storage = testEnvironment.authenticatedContext("alice").storage();

  await assertFails(
    uploadString(
      ref(storage, "users/alice/avatar/current.webp"),
      "not-an-image",
      "raw",
      {contentType: "text/plain"},
    ),
  );
  await assertFails(
    uploadString(
      ref(storage, "users/alice/avatar/current.jpg"),
      "image-bytes",
      "raw",
      {contentType: "image/webp"},
    ),
  );
});

test("avatar uploads at the 10 MB hard limit are denied", async () => {
  const storage = testEnvironment.authenticatedContext("alice").storage();
  const avatar = ref(storage, "users/alice/avatar/large.webp");

  await assertFails(
    uploadBytes(
      avatar,
      new Uint8Array(10 * 1024 * 1024),
      {contentType: "image/webp"},
    ),
  );
});

test("unauthenticated users cannot read avatars", async () => {
  const ownerStorage = testEnvironment.authenticatedContext("alice").storage();
  const avatar = ref(ownerStorage, "users/alice/avatar/current.webp");
  await assertSucceeds(
    uploadString(avatar, "local-emulator-image", "raw", {
      contentType: "image/webp",
    }),
  );

  const publicStorage = testEnvironment.unauthenticatedContext().storage();
  await assertFails(
    getBytes(ref(publicStorage, "users/alice/avatar/current.webp")),
  );
});

test("active members can upload and read a valid idea WebP cover", async () => {
  const aliceStorage = testEnvironment.authenticatedContext("alice").storage();
  const cover = ref(
    aliceStorage,
    "groups/group-1/ideas/idea-1/cover/cover_1.webp",
  );
  await assertSucceeds(
    uploadString(cover, "processed-webp", "raw", {contentType: "image/webp"}),
  );
  const bobStorage = testEnvironment.authenticatedContext("bob").storage();
  await assertSucceeds(
    getBytes(
      ref(
        bobStorage,
        "groups/group-1/ideas/idea-1/cover/cover_1.webp",
      ),
    ),
  );
});

test("non-members and removed members cannot access idea covers", async () => {
  const aliceStorage = testEnvironment.authenticatedContext("alice").storage();
  const path = "groups/group-1/ideas/idea-1/cover/cover_1.webp";
  await assertSucceeds(
    uploadString(ref(aliceStorage, path), "processed-webp", "raw", {
      contentType: "image/webp",
    }),
  );
  const charlieStorage = testEnvironment.authenticatedContext("charlie").storage();
  await assertFails(getBytes(ref(charlieStorage, path)));
  await assertFails(
    uploadString(ref(charlieStorage, path), "forged", "raw", {
      contentType: "image/webp",
    }),
  );

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "groups/group-1/members/bob"), {
      status: "removed",
    });
  });
  const removedStorage = testEnvironment.authenticatedContext("bob").storage();
  await assertFails(getBytes(ref(removedStorage, path)));
});

test("idea covers reject invalid MIME names paths and oversized files", async () => {
  const storage = testEnvironment.authenticatedContext("alice").storage();
  await assertFails(
    uploadString(
      ref(storage, "groups/group-1/ideas/idea-1/cover/cover.jpg"),
      "image",
      "raw",
      {contentType: "image/webp"},
    ),
  );
  await assertFails(
    uploadString(
      ref(storage, "groups/group-1/ideas/idea-1/cover/cover.webp"),
      "image",
      "raw",
      {contentType: "image/jpeg"},
    ),
  );
  await assertFails(
    uploadString(
      ref(storage, "groups/group-1/ideas/idea-1/completion/photo.webp"),
      "image",
      "raw",
      {contentType: "image/webp"},
    ),
  );
  await assertFails(
    uploadBytes(
      ref(storage, "groups/group-1/ideas/idea-1/cover/large.webp"),
      new Uint8Array(2 * 1024 * 1024 + 1),
      {contentType: "image/webp"},
    ),
  );
});
