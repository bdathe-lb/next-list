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
  App,
  deleteApp as deleteAdminApp,
  initializeApp as initializeAdminApp,
} from "firebase-admin/app";
import {getAuth as getAdminAuth} from "firebase-admin/auth";
import {getFirestore} from "firebase-admin/firestore";

const projectId = "demo-nextlist";
const clients: TestClient[] = [];
let adminApp: App;

interface TestClient {
  app: FirebaseApp;
  auth: Auth;
  functions: Functions;
  uid: string;
}

before(() => {
  adminApp = initializeAdminApp({projectId}, "nextlist-functions-integration");
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
  const email = `m2-user-${index}@example.com`;
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
  const client = {app, auth, functions, uid: credential.user.uid};
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

  const targetEmail = "m2-user-3@example.com";
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
