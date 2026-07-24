import assert from "node:assert/strict";
import {after, before, beforeEach, test} from "node:test";
import {
  assertFails,
  assertSucceeds,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  doc,
  getDoc,
  serverTimestamp,
  setDoc,
} from "firebase/firestore";
import {createRulesTestEnvironment} from "./rulesTestEnvironment";

let testEnvironment: RulesTestEnvironment;

before(async () => {
  testEnvironment = await createRulesTestEnvironment();
});

beforeEach(async () => {
  await testEnvironment.clearFirestore();
});

after(async () => {
  await testEnvironment.cleanup();
});

function validUserProfile() {
  return {
    nickname: "小林",
    avatarPath: null,
    emailVerified: true,
    notificationPrefs: {
      groupInvite: true,
      newSchedule: true,
      upcomingReminder: true,
      ideaComment: true,
    },
    status: "active",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    schemaVersion: 1,
  };
}

test("unauthenticated users cannot read profiles", async () => {
  const firestore = testEnvironment.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(firestore, "users/alice")));
});

test("a user can create and read their own valid profile", async () => {
  const firestore = testEnvironment
    .authenticatedContext("alice", {email_verified: true})
    .firestore();
  const profile = doc(firestore, "users/alice");

  await assertSucceeds(setDoc(profile, validUserProfile()));
  const snapshot = await assertSucceeds(getDoc(profile));

  assert.equal(snapshot.data()?.nickname, "小林");
});

test("a user cannot read another user's private profile", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(
      doc(context.firestore(), "users/bob"),
      validUserProfile(),
    );
  });

  const firestore = testEnvironment.authenticatedContext("alice").firestore();
  await assertFails(getDoc(doc(firestore, "users/bob")));
});
