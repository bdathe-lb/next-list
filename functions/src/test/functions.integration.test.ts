import assert from "node:assert/strict";
import {after, before, test} from "node:test";
import {
  deleteApp,
  FirebaseApp,
  initializeApp as initializeClientApp,
} from "firebase/app";
import {
  Auth,
  connectAuthEmulator,
  createUserWithEmailAndPassword,
  getAuth as getClientAuth,
  getIdToken,
  reload,
} from "firebase/auth";
import {
  connectFunctionsEmulator,
  Functions,
  getFunctions,
  httpsCallable,
} from "firebase/functions";
import {
  connectFirestoreEmulator,
  deleteDoc,
  doc,
  Firestore,
  getDoc,
  getFirestore as getClientFirestore,
  runTransaction,
  serverTimestamp,
  setDoc,
  updateDoc,
} from "firebase/firestore";
import {
  App,
  deleteApp as deleteAdminApp,
  initializeApp as initializeAdminApp,
} from "firebase-admin/app";
import {getAuth as getAdminAuth} from "firebase-admin/auth";
import {getFirestore, Timestamp} from "firebase-admin/firestore";
import {
  maintainCommentCount,
  maintainReactionCounts,
  maintainRsvpCounts,
} from "../ideas/aggregates";
import {
  deliverPush,
  PushRequest,
  PushResult,
} from "../notifications/service";
import {sendUpcomingReminders} from "../notifications/reminders";

const projectId = "demo-nextlist";
const testRunId = Date.now().toString(36);
const clients: TestClient[] = [];
let adminApp: App;

interface TestClient {
  app: FirebaseApp;
  auth: Auth;
  functions: Functions;
  firestore: Firestore;
  uid: string;
}

before(() => {
  adminApp = initializeAdminApp({projectId});
});

after(async () => {
  await Promise.all(clients.map((client) => deleteApp(client.app)));
  await deleteAdminApp(adminApp);
});

async function createClient(index: number, verified = true): Promise<TestClient> {
  const app = initializeClientApp(
    {
      apiKey: "demo-api-key",
      projectId,
      appId: `demo-app-${index}`,
    },
    `client-${index}-${Date.now()}`,
  );
  const auth = getClientAuth(app);
  connectAuthEmulator(auth, "http://127.0.0.1:9099", {disableWarnings: true});
  const functions = getFunctions(app, "asia-east1");
  connectFunctionsEmulator(functions, "127.0.0.1", 5001);
  const firestore = getClientFirestore(app);
  connectFirestoreEmulator(firestore, "127.0.0.1", 8080);
  const email = `m2-user-${index}-${testRunId}@example.com`;
  const credential = await createUserWithEmailAndPassword(
    auth,
    email,
    "nextlist-test-password",
  );
  await getAdminAuth(adminApp).updateUser(credential.user.uid, {
    emailVerified: verified,
    displayName: `成员${index}`,
  });
  await reload(credential.user);
  await getIdToken(credential.user, true);
  await getFirestore(adminApp).collection("users").doc(credential.user.uid).set({
    nickname: `成员${index}`,
    avatarPath: null,
    emailVerified: verified,
    notificationPrefs: {
      groupInvite: true,
      newSchedule: true,
      upcomingReminder: true,
      ideaComment: true,
    },
    status: "active",
    createdAt: new Date(),
    updatedAt: new Date(),
    schemaVersion: 1,
  });
  const client = {app, auth, functions, firestore, uid: credential.user.uid};
  clients.push(client);
  return client;
}

async function call<T>(
  client: TestClient,
  name: string,
  data: Record<string, unknown>,
): Promise<T> {
  const result = await httpsCallable<Record<string, unknown>, T>(
    client.functions,
    name,
  )(data);
  return result.data;
}

function businessCode(error: unknown): string | undefined {
  return (error as {details?: {code?: string}}).details?.code;
}

async function expectBusinessError(
  promise: Promise<unknown>,
  expectedCode: string,
): Promise<void> {
  await assert.rejects(promise, (error: unknown) => {
    assert.equal(businessCode(error), expectedCode);
    return true;
  });
}

async function waitFor(
  predicate: () => Promise<boolean>,
  timeoutMillis = 5_000,
): Promise<void> {
  const deadline = Date.now() + timeoutMillis;
  while (Date.now() < deadline) {
    if (await predicate()) return;
    await new Promise((resolve) => setTimeout(resolve, 100));
  }
  assert.fail("Timed out waiting for the Emulator trigger");
}

async function scheduleWithExpectedRevision(
  client: TestClient,
  groupId: string,
  ideaId: string,
  expectedRevision: number,
  startAt: Date,
): Promise<void> {
  const ideaReference = doc(
    client.firestore,
    `groups/${groupId}/ideas/${ideaId}`,
  );
  const memberReference = doc(
    client.firestore,
    `groups/${groupId}/members/${client.uid}`,
  );
  await runTransaction(client.firestore, async (transaction) => {
    const [member, idea] = await Promise.all([
      transaction.get(memberReference),
      transaction.get(ideaReference),
    ]);
    const current = idea.get("schedule") as Record<string, unknown> | null;
    const revision = typeof current?.revision === "number" ? current.revision : 0;
    const status = idea.get("status");
    if (
      revision !== expectedRevision ||
      (expectedRevision === 0 ? status !== "idea" : status !== "scheduled")
    ) {
      throw new Error("CONFLICT");
    }
    const profile = member.get("profileSnapshot");
    const schedule = expectedRevision === 0 ? {
      startAt,
      timezone: "Asia/Shanghai",
      meetingPoint: "地铁站",
      note: null,
      scheduledBy: client.uid,
      schedulerSnapshot: profile,
      scheduledAt: serverTimestamp(),
      updatedBy: client.uid,
      updatedAt: serverTimestamp(),
      revision: 1,
    } : {
      ...current,
      startAt,
      updatedBy: client.uid,
      updatedAt: serverTimestamp(),
      revision: expectedRevision + 1,
    };
    transaction.update(ideaReference, {
      status: "scheduled",
      schedule,
      lastModifiedBy: client.uid,
      updatedAt: serverTimestamp(),
    });
  });
}

test("M2 callable transactions preserve membership and role invariants", async () => {
  const users = await Promise.all(
    Array.from({length: 11}, (_, index) => createClient(index + 1)),
  );
  const unverified = await createClient(12, false);
  const owner = users[0];
  const created = await call<{groupId: string}>(owner, "createGroup", {
    name: "  周末去哪  ",
    requestId: "create-group-request-0001",
  });
  const duplicateCreate = await call<{groupId: string}>(owner, "createGroup", {
    name: "周末去哪",
    requestId: "create-group-request-0001",
  });
  assert.equal(duplicateCreate.groupId, created.groupId);
  await expectBusinessError(
    call(unverified, "createGroup", {
      name: "未验证小组",
      requestId: "unverified-create-request",
    }),
    "EMAIL_NOT_VERIFIED",
  );

  const database = getFirestore(adminApp);
  const groupRef = database.collection("groups").doc(created.groupId);
  let group = (await groupRef.get()).data();
  assert.equal(group?.name, "周末去哪");
  assert.equal(group?.memberCount, 1);
  assert.equal(group?.adminId, owner.uid);
  await database.collection("users").doc(owner.uid).update({
    nickname: "成员一号",
  });
  await waitFor(async () => {
    const membership = await groupRef.collection("members").doc(owner.uid).get();
    return membership.get("profileSnapshot.nickname") === "成员一号";
  });

  const invite = await call<{
    token: string;
    code: string;
    inviteId: string;
  }>(owner, "getOrCreateInvite", {groupId: created.groupId});
  const sameInvite = await call<{
    token: string;
    code: string;
    inviteId: string;
  }>(owner, "getOrCreateInvite", {groupId: created.groupId});
  assert.deepEqual(sameInvite, invite);

  const rotated = await call<{
    token: string;
    code: string;
    inviteId: string;
  }>(owner, "rotateInvite", {
    groupId: created.groupId,
    requestId: "rotate-invite-request-0001",
  });
  const duplicateRotate = await call<{
    token: string;
    code: string;
    inviteId: string;
  }>(owner, "rotateInvite", {
    groupId: created.groupId,
    requestId: "rotate-invite-request-0001",
  });
  assert.deepEqual(duplicateRotate, rotated);
  assert.notEqual(rotated.inviteId, invite.inviteId);
  await expectBusinessError(
    call(unverified, "acceptInvite", {kind: "token", value: rotated.token}),
    "EMAIL_NOT_VERIFIED",
  );
  await expectBusinessError(
    call(users[1], "previewInvite", {kind: "token", value: invite.token}),
    "INVITE_INVALID",
  );

  const joined = await call<{groupId: string; alreadyMember: boolean}>(
    users[1],
    "acceptInvite",
    {kind: "code", value: rotated.code.toLowerCase()},
  );
  assert.equal(joined.groupId, created.groupId);
  assert.equal(joined.alreadyMember, false);
  const joinedAgain = await call<{alreadyMember: boolean}>(
    users[1],
    "acceptInvite",
    {kind: "code", value: rotated.code},
  );
  assert.equal(joinedAgain.alreadyMember, true);
  assert.equal((await groupRef.get()).get("memberCount"), 2);

  await expectBusinessError(
    call(users[1], "updateGroupName", {
      groupId: created.groupId,
      name: "越权改名",
    }),
    "NOT_ADMIN",
  );
  await expectBusinessError(
    call(users[1], "getOrCreateInvite", {groupId: created.groupId}),
    "NOT_ADMIN",
  );
  await expectBusinessError(
    call(users[1], "removeMember", {
      groupId: created.groupId,
      userId: owner.uid,
    }),
    "NOT_ADMIN",
  );
  await expectBusinessError(
    call(users[1], "transferAdmin", {
      groupId: created.groupId,
      userId: users[2].uid,
    }),
    "NOT_ADMIN",
  );
  await expectBusinessError(
    call(users[1], "dissolveGroup", {
      groupId: created.groupId,
      confirmationName: "周末去哪",
    }),
    "NOT_ADMIN",
  );
  await expectBusinessError(
    call(owner, "leaveGroup", {groupId: created.groupId}),
    "ADMIN_CANNOT_LEAVE",
  );

  const targetEmail = `m2-user-3-${testRunId}@example.com`;
  assert.deepEqual(
    await call(owner, "sendDirectInvite", {
      groupId: created.groupId,
      email: targetEmail.toUpperCase(),
    }),
    {delivered: true},
  );
  assert.deepEqual(
    await call(owner, "sendDirectInvite", {
      groupId: created.groupId,
      email: "not-registered@example.com",
    }),
    {delivered: true},
  );
  const directInvitations = await database.collection("users")
    .doc(users[2].uid)
    .collection("invitations")
    .get();
  assert.equal(directInvitations.size, 1);
  const directData = directInvitations.docs[0].data();
  assert.equal(directData.groupId, created.groupId);
  assert.equal("email" in directData, false);
  await waitFor(async () => {
    const feed = await database.collection("users").doc(users[2].uid)
      .collection("feed")
      .where("type", "==", "group_invited")
      .get();
    return feed.size === 1;
  });
  const invitationFeed = await database.collection("users").doc(users[2].uid)
    .collection("feed")
    .where("type", "==", "group_invited")
    .get();
  assert.equal(
    invitationFeed.docs[0].get("invitationId"),
    directInvitations.docs[0].id,
  );
  assert.equal(
    (
      await database.collection("users").doc(owner.uid).collection("feed").get()
    ).size,
    0,
  );
  await call(users[2], "previewInvite", {
    kind: "direct",
    value: directInvitations.docs[0].id,
  });
  await call(users[2], "acceptDirectInvite", {
    invitationId: directInvitations.docs[0].id,
  });

  for (const user of users.slice(3, 9)) {
    await call(user, "acceptInvite", {kind: "token", value: rotated.token});
  }
  assert.equal((await groupRef.get()).get("memberCount"), 9);

  const finalAttempts = await Promise.allSettled(
    users.slice(9, 11).map((user) =>
      call(user, "acceptInvite", {kind: "token", value: rotated.token}),
    ),
  );
  assert.equal(finalAttempts.filter((result) => result.status === "fulfilled").length, 1);
  const rejected = finalAttempts.find((result) => result.status === "rejected");
  assert.equal(
    businessCode((rejected as PromiseRejectedResult).reason),
    "GROUP_FULL",
  );
  group = (await groupRef.get()).data();
  assert.equal(group?.memberCount, 10);
  const activeAtCapacity = await groupRef.collection("members")
    .where("status", "==", "active")
    .get();
  assert.equal(activeAtCapacity.size, 10);

  await call(users[3], "leaveGroup", {groupId: created.groupId});
  await call(users[3], "leaveGroup", {groupId: created.groupId});
  assert.equal((await groupRef.get()).get("memberCount"), 9);
  await call(owner, "removeMember", {
    groupId: created.groupId,
    userId: users[4].uid,
  });
  await call(owner, "removeMember", {
    groupId: created.groupId,
    userId: users[4].uid,
  });
  assert.equal((await groupRef.get()).get("memberCount"), 8);

  const race = await Promise.allSettled([
    call(owner, "transferAdmin", {
      groupId: created.groupId,
      userId: users[2].uid,
    }),
    call(owner, "leaveGroup", {groupId: created.groupId}),
  ]);
  assert.equal(race[0].status, "fulfilled");
  if (race[1].status === "rejected") {
    assert.equal(businessCode(race[1].reason), "ADMIN_CANNOT_LEAVE");
  }
  group = (await groupRef.get()).data();
  assert.equal(group?.adminId, users[2].uid);
  const activeMembers = await groupRef.collection("members")
    .where("status", "==", "active")
    .get();
  const activeAdmins = activeMembers.docs.filter((document) => {
    return document.get("role") === "admin";
  });
  assert.equal(activeAdmins.length, 1);
  assert.equal(activeAdmins[0].id, users[2].uid);
  await call(owner, "transferAdmin", {
    groupId: created.groupId,
    userId: users[2].uid,
  });

  await call(users[2], "updateGroupName", {
    groupId: created.groupId,
    name: "夏日计划",
  });
  await call(users[2], "dissolveGroup", {
    groupId: created.groupId,
    confirmationName: "夏日计划",
  });
  await call(users[2], "dissolveGroup", {
    groupId: created.groupId,
    confirmationName: "夏日计划",
  });
  group = (await groupRef.get()).data();
  assert.equal(group?.status, "dissolved");
  assert.equal(group?.memberCount, 0);
  assert.equal(
    (await groupRef.collection("members").where("status", "==", "active").get()).size,
    0,
  );
  await expectBusinessError(
    call(users[2], "getOrCreateInvite", {groupId: created.groupId}),
    "GROUP_DISSOLVED",
  );
});

test("M6 account deletion blocks admins then anonymizes and removes private data", async () => {
  const owner = await createClient(60);
  const member = await createClient(61);
  const created = await call<{groupId: string}>(owner, "createGroup", {
    name: "注销流程小组",
    requestId: "m6-delete-create-group",
  });
  const invite = await call<{token: string}>(
    owner,
    "getOrCreateInvite",
    {groupId: created.groupId},
  );
  await call(member, "acceptInvite", {kind: "token", value: invite.token});
  await expectBusinessError(call(owner, "deleteAccount", {}), "ADMIN_CANNOT_LEAVE");

  const database = getFirestore(adminApp);
  const groupRef = database.collection("groups").doc(created.groupId);
  const ideaRef = groupRef.collection("ideas").doc("m6-delete-idea");
  const commentRef = ideaRef.collection("comments").doc("m6-delete-comment");
  const memberFeedRef = database.collection("users").doc(member.uid)
    .collection("feed").doc("m6-delete-feed");
  const now = Timestamp.now();
  await Promise.all([
    ideaRef.set({
      groupId: created.groupId,
      title: "保留的小组历史",
      category: "other",
      note: null,
      media: null,
      locationOrLink: null,
      createdBy: owner.uid,
      creatorSnapshot: {nickname: "成员60", avatarPath: null},
      status: "idea",
      schedule: null,
      completion: null,
      reactionCounts: {want: 0, ok: 0, notInterested: 0},
      rsvpCounts: {going: 0, maybe: 0, notGoing: 0},
      commentCount: 1,
      reminderClaimedAt: null,
      reminderSentAt: null,
      reminderSkippedReason: null,
      lastModifiedBy: owner.uid,
      createdAt: now,
      updatedAt: now,
      isDeleted: false,
      deletedAt: null,
      deletedBy: null,
      schemaVersion: 1,
    }),
    database.collection("users").doc(owner.uid).collection("devices")
      .doc("m6-device").set({token: "private-token"}),
    database.collection("users").doc(owner.uid).collection("feed")
      .doc("m6-private-feed").set({type: "idea_created"}),
    database.collection("users").doc(owner.uid).collection("invitations")
      .doc("m6-private-invite").set({status: "pending"}),
    memberFeedRef.set({
      actorId: owner.uid,
      actorSnapshot: {nickname: "成员60", avatarPath: null},
    }),
  ]);
  await commentRef.set({
    content: "这条小组评论需要保留",
    createdBy: owner.uid,
    creatorSnapshot: {nickname: "成员60", avatarPath: null},
    createdAt: now,
    isDeleted: false,
    deletedAt: null,
    deletedBy: null,
    schemaVersion: 1,
  });

  await call(owner, "transferAdmin", {
    groupId: created.groupId,
    userId: member.uid,
  });
  assert.deepEqual(
    await call<{deleted: boolean}>(owner, "deleteAccount", {}),
    {deleted: true},
  );

  await assert.rejects(
    getAdminAuth(adminApp).getUser(owner.uid),
    (error: unknown) => (
      (error as {code?: string}).code === "auth/user-not-found"
    ),
  );
  const deletedProfile = await database.collection("users").doc(owner.uid).get();
  assert.equal(deletedProfile.get("status"), "deleted");
  assert.equal(deletedProfile.get("nickname"), "已注销成员");
  assert.equal(deletedProfile.get("avatarPath"), null);
  assert.equal(
    (await database.collection("users").doc(owner.uid).collection("devices").get()).size,
    0,
  );
  assert.equal(
    (await database.collection("users").doc(owner.uid).collection("feed").get()).size,
    0,
  );
  assert.equal(
    (
      await database.collection("users").doc(owner.uid)
        .collection("invitations").get()
    ).size,
    0,
  );
  const ownerMembership = await groupRef.collection("members").doc(owner.uid).get();
  assert.equal(ownerMembership.get("status"), "left");
  assert.equal(ownerMembership.get("profileSnapshot.nickname"), "已注销成员");
  assert.equal((await groupRef.get()).get("adminId"), member.uid);
  assert.equal((await groupRef.get()).get("memberCount"), 1);
  assert.equal((await ideaRef.get()).get("title"), "保留的小组历史");
  assert.equal((await ideaRef.get()).get("creatorSnapshot.nickname"), "已注销成员");
  assert.equal((await commentRef.get()).get("content"), "这条小组评论需要保留");
  assert.equal(
    (await commentRef.get()).get("creatorSnapshot.nickname"),
    "已注销成员",
  );
  assert.equal((await memberFeedRef.get()).get("actorSnapshot.nickname"), "已注销成员");
});

test("M3 triggers keep idea reaction and comment aggregates exact", async () => {
  const owner = await createClient(20);
  const member = await createClient(21);
  const created = await call<{groupId: string}>(owner, "createGroup", {
    name: "M3 实时小组",
    requestId: "m3-create-group-request",
  });
  const invite = await call<{token: string}>(
    owner,
    "getOrCreateInvite",
    {groupId: created.groupId},
  );
  await call(member, "acceptInvite", {kind: "token", value: invite.token});

  const database = getFirestore(adminApp);
  const groupRef = database.collection("groups").doc(created.groupId);
  const ideaRef = groupRef.collection("ideas").doc("m3-idea-1");
  const ownerProfile = {
    nickname: "成员20",
    avatarPath: null,
  };
  const memberProfile = {
    nickname: "成员21",
    avatarPath: null,
  };
  const now = Timestamp.now();
  await ideaRef.set({
    groupId: created.groupId,
    title: "去植物园",
    category: "place",
    note: null,
    media: null,
    locationOrLink: null,
    createdBy: owner.uid,
    creatorSnapshot: ownerProfile,
    status: "idea",
    schedule: null,
    completion: null,
    reactionCounts: {want: 0, ok: 0, notInterested: 0},
    rsvpCounts: {going: 0, maybe: 0, notGoing: 0},
    commentCount: 0,
    reminderClaimedAt: null,
    reminderSentAt: null,
    reminderSkippedReason: null,
    lastModifiedBy: owner.uid,
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    deletedAt: null,
    deletedBy: null,
    schemaVersion: 1,
  });
  await waitFor(async () => (await groupRef.get()).get("ideaCount") === 1);

  const ownerReaction = ideaRef.collection("reactions").doc(owner.uid);
  const memberReaction = ideaRef.collection("reactions").doc(member.uid);
  await Promise.all([
    ownerReaction.set({
      groupId: created.groupId,
      ideaId: ideaRef.id,
      userId: owner.uid,
      value: "want",
      userSnapshot: ownerProfile,
      createdAt: Timestamp.now(),
      updatedAt: Timestamp.now(),
      schemaVersion: 1,
    }),
    memberReaction.set({
      groupId: created.groupId,
      ideaId: ideaRef.id,
      userId: member.uid,
      value: "want",
      userSnapshot: memberProfile,
      createdAt: Timestamp.now(),
      updatedAt: Timestamp.now(),
      schemaVersion: 1,
    }),
  ]);
  await waitFor(async () => {
    const counts = (await ideaRef.get()).get("reactionCounts");
    return counts?.want === 2 && counts?.ok === 0;
  });

  await Promise.all([
    ownerReaction.update({value: "ok", updatedAt: Timestamp.now()}),
    memberReaction.delete(),
  ]);
  await waitFor(async () => {
    const counts = (await ideaRef.get()).get("reactionCounts");
    return counts?.want === 0 && counts?.ok === 1 && counts?.notInterested === 0;
  });
  const duplicateReactionEvent = {
    id: "m3-duplicate-reaction-event",
    params: {
      groupId: created.groupId,
      ideaId: ideaRef.id,
      uid: owner.uid,
    },
  } as unknown as Parameters<typeof maintainReactionCounts>[0];
  await Promise.all([
    maintainReactionCounts(duplicateReactionEvent),
    maintainReactionCounts(duplicateReactionEvent),
  ]);
  assert.equal(
    (
      await database.collection("functionEvents")
        .doc("maintainReactionCounts_m3-duplicate-reaction-event")
        .get()
    ).exists,
    true,
  );
  assert.deepEqual(
    (await ideaRef.get()).get("reactionCounts"),
    {want: 0, ok: 1, notInterested: 0},
  );

  const comments = ideaRef.collection("comments");
  const firstComment = comments.doc("comment-1");
  await Promise.all([
    firstComment.set({
      content: "周六一起去",
      createdBy: member.uid,
      creatorSnapshot: memberProfile,
      createdAt: Timestamp.now(),
      isDeleted: false,
      deletedAt: null,
      deletedBy: null,
      schemaVersion: 1,
    }),
    comments.doc("comment-2").set({
      content: "记得带水",
      createdBy: owner.uid,
      creatorSnapshot: ownerProfile,
      createdAt: Timestamp.now(),
      isDeleted: false,
      deletedAt: null,
      deletedBy: null,
      schemaVersion: 1,
    }),
  ]);
  await waitFor(async () => (await ideaRef.get()).get("commentCount") === 2);
  await firstComment.update({
    isDeleted: true,
    deletedAt: Timestamp.now(),
    deletedBy: member.uid,
  });
  await waitFor(async () => (await ideaRef.get()).get("commentCount") === 1);
  const duplicateCommentEvent = {
    id: "m3-duplicate-comment-event",
    params: {
      groupId: created.groupId,
      ideaId: ideaRef.id,
      commentId: firstComment.id,
    },
  } as unknown as Parameters<typeof maintainCommentCount>[0];
  await Promise.all([
    maintainCommentCount(duplicateCommentEvent),
    maintainCommentCount(duplicateCommentEvent),
  ]);
  assert.equal(
    (
      await database.collection("functionEvents")
        .doc("maintainCommentCount_m3-duplicate-comment-event")
        .get()
    ).exists,
    true,
  );
  assert.equal((await ideaRef.get()).get("commentCount"), 1);

  await memberReaction.set({
    groupId: created.groupId,
    ideaId: ideaRef.id,
    userId: member.uid,
    value: "not_interested",
    userSnapshot: memberProfile,
    createdAt: Timestamp.now(),
    updatedAt: Timestamp.now(),
    schemaVersion: 1,
  });
  await waitFor(async () => {
    return (await ideaRef.get()).get("reactionCounts.notInterested") === 1;
  });
  await call(owner, "removeMember", {
    groupId: created.groupId,
    userId: member.uid,
  });
  await waitFor(async () => !(await memberReaction.get()).exists);
  await waitFor(async () => {
    const counts = (await ideaRef.get()).get("reactionCounts");
    return counts?.want === 0 && counts?.ok === 1 && counts?.notInterested === 0;
  });

  const ownerFeed = await database.collection("users")
    .doc(owner.uid)
    .collection("feed")
    .get();
  const memberFeed = await database.collection("users")
    .doc(member.uid)
    .collection("feed")
    .get();
  assert.equal(ownerFeed.size, 1);
  assert.equal(ownerFeed.docs[0].get("type"), "idea_commented");
  assert.equal(ownerFeed.docs[0].get("ideaId"), ideaRef.id);
  assert.equal(ownerFeed.docs[0].get("expiresAt") instanceof Timestamp, true);
  assert.equal(memberFeed.size, 1);
  assert.equal(memberFeed.docs[0].get("type"), "idea_created");
  assert.equal(memberFeed.docs[0].get("actorId"), owner.uid);

  await ideaRef.update({
    isDeleted: true,
    deletedAt: Timestamp.now(),
    deletedBy: owner.uid,
    lastModifiedBy: owner.uid,
    updatedAt: Timestamp.now(),
  });
  await waitFor(async () => (await groupRef.get()).get("ideaCount") === 0);
  const finalCounts = (await ideaRef.get()).get("reactionCounts");
  assert.deepEqual(finalCounts, {want: 0, ok: 1, notInterested: 0});
  assert.equal((await ideaRef.get()).get("commentCount"), 1);
});

test("M4 transitions stay exact while M5 emits only the allowed feed events", async () => {
  const owner = await createClient(30);
  const member = await createClient(31);
  const created = await call<{groupId: string}>(owner, "createGroup", {
    name: "M4 核心链路",
    requestId: "m4-create-group-request",
  });
  const invite = await call<{token: string}>(
    owner,
    "getOrCreateInvite",
    {groupId: created.groupId},
  );
  await call(member, "acceptInvite", {kind: "token", value: invite.token});

  const database = getFirestore(adminApp);
  const groupRef = database.collection("groups").doc(created.groupId);
  const ideaRef = groupRef.collection("ideas").doc("m4-idea-1");
  const expectedPreRemovalFeedTypes = [
    "idea_created",
    "schedule_created",
    "schedule_updated",
    "schedule_updated",
  ].sort();
  const expectedFeedTypes = [
    "idea_completed",
    ...expectedPreRemovalFeedTypes,
  ].sort();
  const readFeedTypes = async (): Promise<unknown[]> => {
    const [ownerFeed, memberFeed] = await Promise.all([
      database.collection("users").doc(owner.uid).collection("feed").get(),
      database.collection("users").doc(member.uid).collection("feed").get(),
    ]);
    return [...ownerFeed.docs, ...memberFeed.docs]
      .map((document) => document.get("type"))
      .sort();
  };
  const ownerSnapshot = {nickname: "成员30", avatarPath: null};
  const memberSnapshot = {nickname: "成员31", avatarPath: null};
  const now = Timestamp.now();
  await ideaRef.set({
    groupId: created.groupId,
    title: "去看日落",
    category: "activity",
    note: null,
    media: null,
    locationOrLink: null,
    createdBy: owner.uid,
    creatorSnapshot: ownerSnapshot,
    status: "idea",
    schedule: null,
    completion: null,
    reactionCounts: {want: 0, ok: 0, notInterested: 0},
    rsvpCounts: {going: 0, maybe: 0, notGoing: 0},
    commentCount: 0,
    reminderClaimedAt: null,
    reminderSentAt: null,
    reminderSkippedReason: null,
    lastModifiedBy: owner.uid,
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    deletedAt: null,
    deletedBy: null,
    schemaVersion: 1,
  });
  await waitFor(async () => (await groupRef.get()).get("ideaCount") === 1);

  const simultaneousSchedule = await Promise.allSettled([
    scheduleWithExpectedRevision(
      owner,
      created.groupId,
      ideaRef.id,
      0,
      new Date("2026-07-27T10:00:00Z"),
    ),
    scheduleWithExpectedRevision(
      member,
      created.groupId,
      ideaRef.id,
      0,
      new Date("2026-07-27T11:00:00Z"),
    ),
  ]);
  assert.equal(
    simultaneousSchedule.filter((result) => result.status === "fulfilled").length,
    1,
  );
  assert.equal(
    simultaneousSchedule.filter((result) => result.status === "rejected").length,
    1,
  );
  await waitFor(async () => {
    const group = await groupRef.get();
    return group.get("ideaCount") === 0 && group.get("scheduledCount") === 1;
  });
  assert.equal((await ideaRef.get()).get("schedule.revision"), 1);

  await scheduleWithExpectedRevision(
    member,
    created.groupId,
    ideaRef.id,
    1,
    new Date("2026-07-28T10:00:00Z"),
  );
  await assert.rejects(
    scheduleWithExpectedRevision(
      owner,
      created.groupId,
      ideaRef.id,
      1,
      new Date("2026-07-29T10:00:00Z"),
    ),
    /CONFLICT/,
  );
  assert.equal((await ideaRef.get()).get("schedule.revision"), 2);

  const ownerRsvp = doc(
    owner.firestore,
    `groups/${created.groupId}/ideas/${ideaRef.id}/rsvps/${owner.uid}`,
  );
  const memberRsvp = doc(
    member.firestore,
    `groups/${created.groupId}/ideas/${ideaRef.id}/rsvps/${member.uid}`,
  );
  await Promise.all([
    setDoc(ownerRsvp, {
      groupId: created.groupId,
      ideaId: ideaRef.id,
      userId: owner.uid,
      value: "going",
      scheduleRevision: 2,
      userSnapshot: ownerSnapshot,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      schemaVersion: 1,
    }),
    setDoc(memberRsvp, {
      groupId: created.groupId,
      ideaId: ideaRef.id,
      userId: member.uid,
      value: "maybe",
      scheduleRevision: 2,
      userSnapshot: memberSnapshot,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
      schemaVersion: 1,
    }),
  ]);
  await waitFor(async () => {
    const counts = (await ideaRef.get()).get("rsvpCounts");
    return counts?.going === 1 && counts?.maybe === 1 && counts?.notGoing === 0;
  });
  await Promise.all([
    updateDoc(ownerRsvp, {
      value: "not_going",
      updatedAt: serverTimestamp(),
    }),
    deleteDoc(memberRsvp),
  ]);
  await waitFor(async () => {
    const counts = (await ideaRef.get()).get("rsvpCounts");
    return counts?.going === 0 && counts?.maybe === 0 && counts?.notGoing === 1;
  });

  const duplicateRsvpEvent = {
    id: "m4-duplicate-rsvp-event",
    params: {
      groupId: created.groupId,
      ideaId: ideaRef.id,
      uid: owner.uid,
    },
  } as unknown as Parameters<typeof maintainRsvpCounts>[0];
  await Promise.all([
    maintainRsvpCounts(duplicateRsvpEvent),
    maintainRsvpCounts(duplicateRsvpEvent),
  ]);
  assert.deepEqual(
    (await ideaRef.get()).get("rsvpCounts"),
    {going: 0, maybe: 0, notGoing: 1},
  );

  await setDoc(memberRsvp, {
    groupId: created.groupId,
    ideaId: ideaRef.id,
    userId: member.uid,
    value: "going",
    scheduleRevision: 2,
    userSnapshot: memberSnapshot,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    schemaVersion: 1,
  });
  await waitFor(async () => (await ideaRef.get()).get("rsvpCounts.going") === 1);
  await scheduleWithExpectedRevision(
    owner,
    created.groupId,
    ideaRef.id,
    2,
    new Date("2026-07-30T10:00:00Z"),
  );
  assert.equal((await getDoc(memberRsvp)).get("scheduleRevision"), 2);
  assert.equal((await ideaRef.get()).get("schedule.revision"), 3);
  await waitFor(async () => {
    const feedTypes = await readFeedTypes();
    return JSON.stringify(feedTypes) ===
      JSON.stringify(expectedPreRemovalFeedTypes);
  }, 15_000);

  await call(owner, "removeMember", {
    groupId: created.groupId,
    userId: member.uid,
  });
  await waitFor(async () => !(await ideaRef.collection("rsvps").doc(member.uid).get()).exists);
  await waitFor(async () => {
    const counts = (await ideaRef.get()).get("rsvpCounts");
    return counts?.going === 0 && counts?.maybe === 0 && counts?.notGoing === 1;
  });
  await call(member, "acceptInvite", {kind: "token", value: invite.token});

  const ownerIdea = doc(
    owner.firestore,
    `groups/${created.groupId}/ideas/${ideaRef.id}`,
  );
  await updateDoc(ownerIdea, {
    status: "completed",
    completion: {
      completedOn: "2026-07-30",
      timezone: "Asia/Shanghai",
      photo: null,
      review: "值得再去",
      rating: 5,
      completedBy: owner.uid,
      completerSnapshot: ownerSnapshot,
      completedAt: serverTimestamp(),
      updatedBy: owner.uid,
      updatedAt: serverTimestamp(),
    },
    lastModifiedBy: owner.uid,
    updatedAt: serverTimestamp(),
  });
  await waitFor(async () => {
    const group = await groupRef.get();
    return group.get("scheduledCount") === 0 && group.get("completedCount") === 1;
  });
  const completion = (await getDoc(ownerIdea)).get("completion");
  await updateDoc(ownerIdea, {
    completion: {
      ...completion,
      review: "修正后的评价",
      rating: 4,
      updatedBy: owner.uid,
      updatedAt: serverTimestamp(),
    },
    lastModifiedBy: owner.uid,
    updatedAt: serverTimestamp(),
  });
  await new Promise((resolve) => setTimeout(resolve, 300));
  assert.equal((await groupRef.get()).get("scheduledCount"), 0);
  assert.equal((await groupRef.get()).get("completedCount"), 1);

  await waitFor(async () => {
    const feedTypes = await readFeedTypes();
    return JSON.stringify(feedTypes) === JSON.stringify(expectedFeedTypes);
  }, 15_000);
  const [ownerFeed, memberFeed] = await Promise.all([
    database.collection("users").doc(owner.uid).collection("feed").get(),
    database.collection("users").doc(member.uid).collection("feed").get(),
  ]);
  const allFeedTypes = [...ownerFeed.docs, ...memberFeed.docs]
    .map((document) => document.get("type"))
    .sort();
  assert.deepEqual(
    allFeedTypes,
    expectedFeedTypes,
  );
});

test("M5 push delivery respects preferences idempotency partial retry and invalid tokens", async () => {
  const owner = await createClient(40);
  const member = await createClient(41);
  const created = await call<{groupId: string}>(owner, "createGroup", {
    name: "M5 推送小组",
    requestId: "m5-push-create-group",
  });
  const invite = await call<{token: string}>(
    owner,
    "getOrCreateInvite",
    {groupId: created.groupId},
  );
  await call(member, "acceptInvite", {kind: "token", value: invite.token});
  const database = getFirestore(adminApp);
  const memberDevices = database.collection("users").doc(member.uid)
    .collection("devices");
  await Promise.all([
    memberDevices.doc("installation-device-1").set({
      token: "token-1",
      platform: "android",
      appVersion: "0.1.0",
      locale: "zh-CN",
      createdAt: Timestamp.now(),
      updatedAt: Timestamp.now(),
    }),
    memberDevices.doc("installation-device-2").set({
      token: "token-2",
      platform: "android",
      appVersion: "0.1.0",
      locale: "zh-CN",
      createdAt: Timestamp.now(),
      updatedAt: Timestamp.now(),
    }),
  ]);

  const sent: PushRequest[][] = [];
  let attempt = 0;
  const partialSender = async (requests: PushRequest[]): Promise<PushResult[]> => {
    sent.push(requests);
    attempt += 1;
    return requests.map((request) => ({
      success: attempt > 1 || request.token === "token-1",
      errorCode: attempt > 1 || request.token === "token-1" ?
        undefined :
        "messaging/internal-error",
    }));
  };
  const data = {
    type: "schedule_created" as const,
    groupId: created.groupId,
    ideaId: "idea-1",
  };
  assert.equal(
    await deliverPush(
      "m5_partial_delivery",
      [{uid: member.uid}],
      data,
      partialSender,
    ),
    false,
  );
  assert.equal(sent[0].length, 2);
  assert.equal(
    await deliverPush(
      "m5_partial_delivery",
      [{uid: member.uid}],
      data,
      partialSender,
    ),
    true,
  );
  assert.equal(sent[1].length, 1);
  assert.equal(sent[1][0].token, "token-2");
  await deliverPush(
    "m5_partial_delivery",
    [{uid: member.uid}],
    data,
    partialSender,
  );
  assert.equal(sent.length, 2);

  await database.collection("users").doc(member.uid).update({
    "notificationPrefs.newSchedule": false,
  });
  await deliverPush(
    "m5_preference_off",
    [{uid: member.uid}],
    data,
    partialSender,
  );
  assert.equal(sent.length, 2);

  await database.collection("users").doc(member.uid).update({
    "notificationPrefs.newSchedule": true,
  });
  const invalidSender = async (requests: PushRequest[]): Promise<PushResult[]> =>
    requests.map(() => ({
      success: false,
      errorCode: "messaging/registration-token-not-registered",
    }));
  assert.equal(
    await deliverPush(
      "m5_invalid_tokens",
      [{uid: member.uid}],
      data,
      invalidSender,
    ),
    true,
  );
  assert.equal((await memberDevices.get()).empty, true);
});

test("M5 reminder excludes not-going handles too-late and is idempotent", async () => {
  const owner = await createClient(50);
  const member = await createClient(51);
  const created = await call<{groupId: string}>(owner, "createGroup", {
    name: "M5 提醒小组",
    requestId: "m5-reminder-create-group",
  });
  const invite = await call<{token: string}>(
    owner,
    "getOrCreateInvite",
    {groupId: created.groupId},
  );
  await call(member, "acceptInvite", {kind: "token", value: invite.token});
  const database = getFirestore(adminApp);
  const now = Timestamp.now();
  const groupRef = database.collection("groups").doc(created.groupId);
  const ideaRef = groupRef.collection("ideas").doc("reminder-eligible");
  const startAt = Timestamp.fromMillis(now.toMillis() + 20 * 60 * 1000);
  await ideaRef.set({
    groupId: created.groupId,
    title: "去看日落",
    category: "activity",
    note: null,
    media: null,
    locationOrLink: null,
    createdBy: owner.uid,
    creatorSnapshot: {nickname: "成员50", avatarPath: null},
    status: "scheduled",
    schedule: {
      startAt,
      timezone: "Asia/Shanghai",
      meetingPoint: null,
      note: null,
      scheduledBy: owner.uid,
      schedulerSnapshot: {nickname: "成员50", avatarPath: null},
      scheduledAt: Timestamp.fromMillis(now.toMillis() - 60 * 60 * 1000),
      updatedBy: owner.uid,
      updatedAt: Timestamp.fromMillis(now.toMillis() - 60 * 60 * 1000),
      revision: 1,
    },
    completion: null,
    reactionCounts: {want: 0, ok: 0, notInterested: 0},
    rsvpCounts: {going: 0, maybe: 0, notGoing: 1},
    commentCount: 0,
    reminderClaimedAt: null,
    reminderSentAt: null,
    reminderSkippedReason: null,
    lastModifiedBy: owner.uid,
    createdAt: now,
    updatedAt: now,
    isDeleted: false,
    deletedAt: null,
    deletedBy: null,
    schemaVersion: 1,
  });
  await ideaRef.collection("rsvps").doc(member.uid).set({
    groupId: created.groupId,
    ideaId: ideaRef.id,
    userId: member.uid,
    value: "not_going",
    scheduleRevision: 1,
    userSnapshot: {nickname: "成员51", avatarPath: null},
    createdAt: now,
    updatedAt: now,
    schemaVersion: 1,
  });
  for (const [uid, token] of [
    [owner.uid, "owner-reminder-token"],
    [member.uid, "member-reminder-token"],
  ]) {
    await database.collection("users").doc(uid).collection("devices")
      .doc(`installation-${uid}`).set({
        token,
        platform: "android",
        appVersion: "0.1.0",
        locale: "zh-CN",
        createdAt: now,
        updatedAt: now,
      });
  }
  const sent: PushRequest[] = [];
  const sender = async (requests: PushRequest[]): Promise<PushResult[]> => {
    sent.push(...requests);
    return requests.map(() => ({success: true}));
  };
  await sendUpcomingReminders(sender, now);
  assert.deepEqual(sent.map((request) => request.token), ["owner-reminder-token"]);
  assert.equal((await ideaRef.get()).get("reminderSentAt") instanceof Timestamp, true);
  await sendUpcomingReminders(sender, Timestamp.fromMillis(now.toMillis() + 1_000));
  assert.equal(sent.length, 1);

  const tooLateRef = groupRef.collection("ideas").doc("reminder-too-late");
  const eligibleData = (await ideaRef.get()).data() ?? {};
  const eligibleSchedule = (await ideaRef.get()).get("schedule") as
    Record<string, unknown>;
  await tooLateRef.set({
    ...eligibleData,
    title: "临时安排",
    schedule: {
      ...eligibleSchedule,
      startAt: Timestamp.fromMillis(now.toMillis() + 25 * 60 * 1000),
      updatedAt: now,
      revision: 2,
    },
    reminderClaimedAt: null,
    reminderSentAt: null,
    reminderSkippedReason: null,
  });
  await sendUpcomingReminders(sender, now);
  assert.equal((await tooLateRef.get()).get("reminderSkippedReason"), "too_late");
  assert.equal(sent.length, 1);
});
