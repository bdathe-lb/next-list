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
  updateDoc,
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

function validUserProfile(overrides: Record<string, unknown> = {}) {
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
    ...overrides,
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

test("an unverified user can create a matching unverified profile", async () => {
  const firestore = testEnvironment
    .authenticatedContext("new-user", {email_verified: false})
    .firestore();

  await assertSucceeds(
    setDoc(
      doc(firestore, "users/new-user"),
      validUserProfile({emailVerified: false}),
    ),
  );
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

test("a user cannot forge the email verification snapshot", async () => {
  const firestore = testEnvironment
    .authenticatedContext("alice", {email_verified: false})
    .firestore();

  await assertFails(
    setDoc(
      doc(firestore, "users/alice"),
      validUserProfile({emailVerified: true}),
    ),
  );
});

test("profile creation rejects invalid nicknames and avatar paths", async () => {
  const firestore = testEnvironment
    .authenticatedContext("alice", {email_verified: true})
    .firestore();

  await assertFails(
    setDoc(
      doc(firestore, "users/alice"),
      validUserProfile({nickname: "林"}),
    ),
  );
  await assertFails(
    setDoc(
      doc(firestore, "users/alice"),
      validUserProfile({avatarPath: "users/bob/avatar/current.webp"}),
    ),
  );
});

test("profile creation rejects unknown fields", async () => {
  const firestore = testEnvironment
    .authenticatedContext("alice", {email_verified: true})
    .firestore();

  await assertFails(
    setDoc(
      doc(firestore, "users/alice"),
      validUserProfile({email: "alice@example.com"}),
    ),
  );
});

test("a user can update only their editable profile fields", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(
      doc(context.firestore(), "users/alice"),
      validUserProfile(),
    );
  });
  const firestore = testEnvironment
    .authenticatedContext("alice", {email_verified: true})
    .firestore();
  const profile = doc(firestore, "users/alice");

  await assertSucceeds(
    updateDoc(profile, {
      nickname: "小林同学",
      avatarPath: "users/alice/avatar/avatar_1.webp",
      updatedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    updateDoc(profile, {
      status: "deleted",
      updatedAt: serverTimestamp(),
    }),
  );
});

test("email verification can only be synced from the auth token", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(
      doc(context.firestore(), "users/alice"),
      validUserProfile({emailVerified: false}),
    );
  });

  const verifiedFirestore = testEnvironment
    .authenticatedContext("alice", {email_verified: true})
    .firestore();
  await assertSucceeds(
    updateDoc(doc(verifiedFirestore, "users/alice"), {
      emailVerified: true,
      updatedAt: serverTimestamp(),
    }),
  );

  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await updateDoc(doc(context.firestore(), "users/alice"), {
      emailVerified: false,
    });
  });
  const unverifiedFirestore = testEnvironment
    .authenticatedContext("alice", {email_verified: false})
    .firestore();
  await assertFails(
    updateDoc(doc(unverifiedFirestore, "users/alice"), {
      emailVerified: true,
      updatedAt: serverTimestamp(),
    }),
  );
});
