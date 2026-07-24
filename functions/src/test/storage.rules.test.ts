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
import {createRulesTestEnvironment} from "./rulesTestEnvironment";

let testEnvironment: RulesTestEnvironment;

before(async () => {
  testEnvironment = await createRulesTestEnvironment();
});

beforeEach(async () => {
  await testEnvironment.clearStorage();
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
