import assert from "node:assert/strict";
import {after, before, beforeEach, test} from "node:test";
import {
  assertFails,
  assertSucceeds,
  RulesTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  collectionGroup,
  doc,
  getDoc,
  getDocs,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
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

function validGroup(overrides: Record<string, unknown> = {}) {
  return {
    name: "周末去哪",
    adminId: "alice",
    status: "active",
    memberCount: 2,
    ideaCount: 0,
    scheduledCount: 0,
    completedCount: 0,
    activeInviteId: null,
    createdBy: "alice",
    lastActivityAt: new Date("2026-07-24T00:00:00Z"),
    createdAt: new Date("2026-07-24T00:00:00Z"),
    updatedAt: new Date("2026-07-24T00:00:00Z"),
    dissolvedAt: null,
    schemaVersion: 1,
    ...overrides,
  };
}

function validMember(
  userId: string,
  role: "admin" | "member",
  overrides: Record<string, unknown> = {},
) {
  return {
    userId,
    groupId: "group-1",
    role,
    status: "active",
    profileSnapshot: {
      nickname: userId === "alice" ? "小林" : "小周",
      avatarPath: null,
    },
    joinedAt: new Date("2026-07-24T00:00:00Z"),
    updatedAt: new Date("2026-07-24T00:00:00Z"),
    leftAt: null,
    removedAt: null,
    removedBy: null,
    schemaVersion: 1,
    ...overrides,
  };
}

async function seedGroup(
  options: {
    group?: Record<string, unknown>;
    alice?: Record<string, unknown>;
    bob?: Record<string, unknown>;
  } = {},
) {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    const firestore = context.firestore();
    await setDoc(doc(firestore, "groups/group-1"), validGroup(options.group));
    await setDoc(
      doc(firestore, "groups/group-1/members/alice"),
      validMember("alice", "admin", options.alice),
    );
    await setDoc(
      doc(firestore, "groups/group-1/members/bob"),
      validMember("bob", "member", options.bob),
    );
  });
}

test("unauthenticated users cannot read group business data", async () => {
  await seedGroup();
  const firestore = testEnvironment.unauthenticatedContext().firestore();
  await assertFails(getDoc(doc(firestore, "groups/group-1")));
  await assertFails(getDocs(collection(firestore, "groups/group-1/members")));
});

test("active members can read their group and member list", async () => {
  await seedGroup();
  const firestore = testEnvironment.authenticatedContext("bob").firestore();
  await assertSucceeds(getDoc(doc(firestore, "groups/group-1")));
  const members = await assertSucceeds(
    getDocs(collection(firestore, "groups/group-1/members")),
  );
  assert.equal(members.size, 2);
});

test("non-members cannot read group details or members", async () => {
  await seedGroup();
  const firestore = testEnvironment.authenticatedContext("charlie").firestore();
  await assertFails(getDoc(doc(firestore, "groups/group-1")));
  await assertFails(getDoc(doc(firestore, "groups/group-1/members/alice")));
});

test("users can collection-group query only their own membership", async () => {
  await seedGroup({
    bob: {
      status: "left",
      leftAt: new Date("2026-07-24T01:00:00Z"),
    },
  });
  const bobFirestore = testEnvironment.authenticatedContext("bob").firestore();
  const ownMemberships = await assertSucceeds(
    getDocs(
      query(
        collectionGroup(bobFirestore, "members"),
        where("userId", "==", "bob"),
      ),
    ),
  );
  assert.equal(ownMemberships.size, 1);

  const aliceFirestore = testEnvironment.authenticatedContext("alice").firestore();
  await assertFails(
    getDocs(
      query(
        collectionGroup(aliceFirestore, "members"),
        where("status", "==", "active"),
      ),
    ),
  );
});

test("removed members immediately lose group access", async () => {
  await seedGroup({
    bob: {
      status: "removed",
      removedAt: new Date("2026-07-24T01:00:00Z"),
      removedBy: "alice",
    },
  });
  const firestore = testEnvironment.authenticatedContext("bob").firestore();
  await assertFails(getDoc(doc(firestore, "groups/group-1")));
  await assertFails(getDocs(collection(firestore, "groups/group-1/members")));
  await assertSucceeds(
    getDoc(doc(firestore, "groups/group-1/members/bob")),
  );
});

test("dissolved groups are immediately inaccessible to former members", async () => {
  await seedGroup({
    group: {
      status: "dissolved",
      memberCount: 0,
      dissolvedAt: new Date("2026-07-24T01:00:00Z"),
    },
  });
  const firestore = testEnvironment.authenticatedContext("alice").firestore();
  await assertFails(getDoc(doc(firestore, "groups/group-1")));
  await assertFails(getDocs(collection(firestore, "groups/group-1/members")));
});

test("all group and membership client writes are denied", async () => {
  await seedGroup();
  const adminFirestore = testEnvironment.authenticatedContext("alice").firestore();
  const memberFirestore = testEnvironment.authenticatedContext("bob").firestore();

  await assertFails(
    updateDoc(doc(adminFirestore, "groups/group-1"), {name: "管理员直写"}),
  );
  await assertFails(
    updateDoc(doc(memberFirestore, "groups/group-1"), {memberCount: 10}),
  );
  await assertFails(
    updateDoc(
      doc(memberFirestore, "groups/group-1/members/bob"),
      {role: "admin"},
    ),
  );
  await assertFails(
    setDoc(
      doc(memberFirestore, "groups/group-1/members/charlie"),
      validMember("charlie", "member"),
    ),
  );
});

test("group invites are never readable or writable by clients", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "groupInvites/invite-1"), {
      groupId: "group-1",
      tokenHash: "secret-hash",
      codeHash: "code-hash",
      status: "active",
    });
  });
  const firestore = testEnvironment.authenticatedContext("alice").firestore();
  await assertFails(getDoc(doc(firestore, "groupInvites/invite-1")));
  await assertFails(
    updateDoc(doc(firestore, "groupInvites/invite-1"), {status: "revoked"}),
  );
});

test("only a direct invitation recipient can read and decline it", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "users/bob/invitations/direct-1"), {
      groupId: "group-1",
      groupNameSnapshot: "周末去哪",
      invitedBy: "alice",
      inviterSnapshot: {nickname: "小林", avatarPath: null},
      status: "pending",
      expiresAt: new Date("2026-07-31T00:00:00Z"),
      createdAt: new Date("2026-07-24T00:00:00Z"),
      respondedAt: null,
      schemaVersion: 1,
    });
  });
  const bobFirestore = testEnvironment.authenticatedContext("bob").firestore();
  const aliceFirestore = testEnvironment.authenticatedContext("alice").firestore();
  const invitation = doc(bobFirestore, "users/bob/invitations/direct-1");

  await assertSucceeds(getDoc(invitation));
  await assertFails(
    getDoc(doc(aliceFirestore, "users/bob/invitations/direct-1")),
  );
  await assertSucceeds(
    updateDoc(invitation, {
      status: "declined",
      respondedAt: serverTimestamp(),
    }),
  );
});

test("clients cannot accept, forge, or modify another direct invitation", async () => {
  await testEnvironment.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), "users/bob/invitations/direct-1"), {
      groupId: "group-1",
      groupNameSnapshot: "周末去哪",
      invitedBy: "alice",
      inviterSnapshot: {nickname: "小林", avatarPath: null},
      status: "pending",
      expiresAt: new Date("2026-07-31T00:00:00Z"),
      createdAt: new Date("2026-07-24T00:00:00Z"),
      respondedAt: null,
      schemaVersion: 1,
    });
  });
  const bobFirestore = testEnvironment.authenticatedContext("bob").firestore();
  const aliceFirestore = testEnvironment.authenticatedContext("alice").firestore();
  await assertFails(
    updateDoc(doc(bobFirestore, "users/bob/invitations/direct-1"), {
      status: "accepted",
      respondedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    updateDoc(doc(bobFirestore, "users/bob/invitations/direct-1"), {
      groupNameSnapshot: "伪造名称",
      status: "declined",
      respondedAt: serverTimestamp(),
    }),
  );
  await assertFails(
    updateDoc(doc(aliceFirestore, "users/bob/invitations/direct-1"), {
      status: "declined",
      respondedAt: serverTimestamp(),
    }),
  );
});
