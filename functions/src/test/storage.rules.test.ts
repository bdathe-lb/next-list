import {after, before, beforeEach, test} from "node:test";
import {
  assertFails,
  assertSucceeds,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {ref, uploadString} from "firebase/storage";
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
