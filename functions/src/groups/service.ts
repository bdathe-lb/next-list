import {createHash} from "node:crypto";
import {getAuth} from "firebase-admin/auth";
import {
  DocumentReference,
  DocumentSnapshot,
  FieldValue,
  getFirestore,
  Timestamp,
  Transaction,
} from "firebase-admin/firestore";
import type {CallableRequest} from "firebase-functions/v2/https";
import {businessError} from "./errors";
import {
  clearInviteAttempts,
  assertInviteAttemptAllowed,
  recordFailedInviteAttempt,
} from "./rateLimit";
import {
  deriveInviteCredentials,
  inviteSecret,
  sha256,
} from "./inviteCrypto";
import {
  MAX_GROUP_MEMBERS,
  normalizeEmail,
  normalizeGroupName,
  requireInviteCredential,
  requireRequestId,
  requireString,
} from "./validation";

const INVITE_LIFETIME_MILLIS = 7 * 24 * 60 * 60 * 1000;
const SECRET_VERSION = "emulator-v1";

export interface UserSnapshot {
  nickname: string;
  avatarPath: string | null;
}

interface GroupData {
  name: string;
  adminId: string;
  status: "active" | "dissolved";
  memberCount: number;
  activeInviteId: string | null;
}

interface MemberData {
  userId: string;
  groupId: string;
  role: "admin" | "member";
  status: "active" | "left" | "removed";
}

interface InviteData {
  groupId: string;
  tokenHash: string;
  codeHash: string;
  secretVersion: string;
  status: "active" | "revoked" | "expired";
  createdBy: string;
  expiresAt: Timestamp;
  useCount: number;
}

interface DirectInviteData {
  groupId: string;
  status: "pending" | "accepted" | "declined" | "expired" | "revoked";
  expiresAt: Timestamp;
}

export interface InviteResult {
  groupId: string;
  inviteId: string;
  token: string;
  code: string;
  expiresAtMillis: number;
}

function requestData(request: CallableRequest<unknown>): Record<string, unknown> {
  if (!request.data || typeof request.data !== "object" || Array.isArray(request.data)) {
    throw businessError("VALIDATION");
  }
  return request.data as Record<string, unknown>;
}

function requireAuth(request: CallableRequest<unknown>): string {
  if (!request.auth) {
    throw businessError("UNAUTHENTICATED");
  }
  return request.auth.uid;
}

function requireVerifiedAuth(request: CallableRequest<unknown>): string {
  const uid = requireAuth(request);
  if (request.auth?.token.email_verified !== true) {
    throw businessError("EMAIL_NOT_VERIFIED");
  }
  return uid;
}

function asGroup(snapshot: DocumentSnapshot): GroupData {
  if (!snapshot.exists) {
    throw businessError("NOT_FOUND");
  }
  return snapshot.data() as GroupData;
}

function asMember(snapshot: DocumentSnapshot): MemberData | null {
  return snapshot.exists ? snapshot.data() as MemberData : null;
}

function assertActiveGroup(group: GroupData): void {
  if (group.status !== "active") {
    throw businessError("GROUP_DISSOLVED");
  }
}

function assertAdmin(group: GroupData, member: MemberData | null, uid: string): void {
  if (
    group.adminId !== uid ||
    member?.status !== "active" ||
    member.role !== "admin"
  ) {
    throw businessError("NOT_ADMIN");
  }
}

function profileSnapshot(snapshot: DocumentSnapshot): UserSnapshot {
  if (!snapshot.exists) {
    throw businessError("NOT_FOUND");
  }
  const data = snapshot.data() ?? {};
  if (typeof data.nickname !== "string") {
    throw businessError("NOT_FOUND");
  }
  return {
    nickname: data.nickname,
    avatarPath: typeof data.avatarPath === "string" ? data.avatarPath : null,
  };
}

function deterministicId(prefix: string, material: string): string {
  const digest = createHash("sha256").update(material, "utf8").digest("hex");
  return `${prefix}_${digest.slice(0, 32)}`;
}

function credentialResult(
  inviteId: string,
  invite: InviteData,
): InviteResult {
  const secret = inviteSecret();
  if (invite.secretVersion !== secret.version && invite.secretVersion !== SECRET_VERSION) {
    throw businessError("INVITE_EXPIRED");
  }
  const credentials = deriveInviteCredentials(
    secret.value,
    inviteId,
    invite.groupId,
    invite.expiresAt.toMillis(),
  );
  return {
    groupId: invite.groupId,
    inviteId,
    token: credentials.token,
    code: credentials.code,
    expiresAtMillis: invite.expiresAt.toMillis(),
  };
}

function newInviteData(
  inviteId: string,
  groupId: string,
  createdBy: string,
  now: Timestamp,
): {data: InviteData & Record<string, unknown>; result: InviteResult} {
  const expiresAt = Timestamp.fromMillis(now.toMillis() + INVITE_LIFETIME_MILLIS);
  const secret = inviteSecret();
  const credentials = deriveInviteCredentials(
    secret.value,
    inviteId,
    groupId,
    expiresAt.toMillis(),
  );
  const data: InviteData & Record<string, unknown> = {
    groupId,
    tokenHash: credentials.tokenHash,
    codeHash: credentials.codeHash,
    secretVersion: secret.version,
    status: "active",
    createdBy,
    expiresAt,
    useCount: 0,
    createdAt: now,
    revokedAt: null,
    schemaVersion: 1,
  };
  return {
    data,
    result: {
      groupId,
      inviteId,
      token: credentials.token,
      code: credentials.code,
      expiresAtMillis: expiresAt.toMillis(),
    },
  };
}

async function uniqueInviteProposal(
  groupId: string,
  uid: string,
  deterministicMaterial?: string,
): Promise<{
  ref: DocumentReference;
  data: InviteData & Record<string, unknown>;
  result: InviteResult;
}> {
  const database = getFirestore();
  for (let attempt = 0; attempt < 5; attempt += 1) {
    const ref = deterministicMaterial ?
      database.collection("groupInvites").doc(
        deterministicId("inv", `${deterministicMaterial}:${attempt}`),
      ) :
      database.collection("groupInvites").doc();
    const proposal = newInviteData(ref.id, groupId, uid, Timestamp.now());
    const collision = await database.collection("groupInvites")
      .where("codeHash", "==", proposal.data.codeHash)
      .limit(1)
      .get();
    if (collision.empty || collision.docs[0].id === ref.id) {
      return {ref, ...proposal};
    }
  }
  throw businessError("RATE_LIMITED");
}

export async function createGroupHandler(
  request: CallableRequest<unknown>,
): Promise<{groupId: string}> {
  const uid = requireVerifiedAuth(request);
  const data = requestData(request);
  const name = normalizeGroupName(data.name);
  const requestId = requireRequestId(data.requestId);
  const groupId = deterministicId("grp", `${uid}:${requestId}`);
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(groupId);
  const memberRef = groupRef.collection("members").doc(uid);
  const userRef = database.collection("users").doc(uid);

  await database.runTransaction(async (transaction) => {
    const [existingGroup, userDocument] = await Promise.all([
      transaction.get(groupRef),
      transaction.get(userRef),
    ]);
    if (existingGroup.exists) {
      const group = existingGroup.data() as GroupData;
      if (group.adminId === uid) {
        return;
      }
      throw businessError("PERMISSION_DENIED");
    }
    const snapshot = profileSnapshot(userDocument);
    const now = Timestamp.now();
    transaction.create(groupRef, {
      name,
      adminId: uid,
      status: "active",
      memberCount: 1,
      ideaCount: 0,
      scheduledCount: 0,
      completedCount: 0,
      activeInviteId: null,
      createdBy: uid,
      lastActivityAt: now,
      createdAt: now,
      updatedAt: now,
      dissolvedAt: null,
      schemaVersion: 1,
    });
    transaction.create(memberRef, {
      userId: uid,
      groupId,
      role: "admin",
      status: "active",
      profileSnapshot: snapshot,
      joinedAt: now,
      updatedAt: now,
      leftAt: null,
      removedAt: null,
      removedBy: null,
      schemaVersion: 1,
    });
  });

  return {groupId};
}

export async function updateGroupNameHandler(
  request: CallableRequest<unknown>,
): Promise<{groupId: string; name: string}> {
  const uid = requireAuth(request);
  const data = requestData(request);
  const groupId = requireString(data.groupId, 1, 128);
  const name = normalizeGroupName(data.name);
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(groupId);
  const memberRef = groupRef.collection("members").doc(uid);

  await database.runTransaction(async (transaction) => {
    const [groupDocument, memberDocument] = await Promise.all([
      transaction.get(groupRef),
      transaction.get(memberRef),
    ]);
    const group = asGroup(groupDocument);
    assertActiveGroup(group);
    assertAdmin(group, asMember(memberDocument), uid);
    const now = Timestamp.now();
    transaction.update(groupRef, {
      name,
      updatedAt: now,
      lastActivityAt: now,
    });
  });

  return {groupId, name};
}

async function getOrCreateInvite(
  uid: string,
  groupId: string,
): Promise<InviteResult> {
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(groupId);
  const memberRef = groupRef.collection("members").doc(uid);
  const proposal = await uniqueInviteProposal(groupId, uid);

  return database.runTransaction(async (transaction) => {
    const [groupDocument, memberDocument] = await Promise.all([
      transaction.get(groupRef),
      transaction.get(memberRef),
    ]);
    const group = asGroup(groupDocument);
    assertActiveGroup(group);
    assertAdmin(group, asMember(memberDocument), uid);
    if (group.memberCount >= MAX_GROUP_MEMBERS) {
      throw businessError("GROUP_FULL");
    }

    if (group.activeInviteId) {
      const activeRef = database.collection("groupInvites").doc(group.activeInviteId);
      const activeDocument = await transaction.get(activeRef);
      if (activeDocument.exists) {
        const active = activeDocument.data() as InviteData;
        if (active.status === "active" && active.expiresAt.toMillis() > Date.now()) {
          return credentialResult(activeDocument.id, active);
        }
        transaction.update(activeRef, {
          status: active.status === "active" ? "expired" : active.status,
        });
      }
    }

    const now = Timestamp.now();
    transaction.create(proposal.ref, proposal.data);
    transaction.update(groupRef, {
      activeInviteId: proposal.ref.id,
      updatedAt: now,
    });
    return proposal.result;
  });
}

export async function getOrCreateInviteHandler(
  request: CallableRequest<unknown>,
): Promise<InviteResult> {
  const uid = requireAuth(request);
  const groupId = requireString(requestData(request).groupId, 1, 128);
  return getOrCreateInvite(uid, groupId);
}

export async function rotateInviteHandler(
  request: CallableRequest<unknown>,
): Promise<InviteResult> {
  const uid = requireAuth(request);
  const data = requestData(request);
  const groupId = requireString(data.groupId, 1, 128);
  const requestId = requireRequestId(data.requestId);
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(groupId);
  const memberRef = groupRef.collection("members").doc(uid);
  const proposal = await uniqueInviteProposal(
    groupId,
    uid,
    `${groupId}:${requestId}`,
  );
  const inviteId = proposal.ref.id;
  const nextInviteRef = proposal.ref;

  return database.runTransaction(async (transaction) => {
    const [groupDocument, memberDocument, existingNext] = await Promise.all([
      transaction.get(groupRef),
      transaction.get(memberRef),
      transaction.get(nextInviteRef),
    ]);
    const group = asGroup(groupDocument);
    assertActiveGroup(group);
    assertAdmin(group, asMember(memberDocument), uid);
    if (group.memberCount >= MAX_GROUP_MEMBERS) {
      throw businessError("GROUP_FULL");
    }
    let currentRef: DocumentReference | null = null;
    let currentDocument: DocumentSnapshot | null = null;
    if (group.activeInviteId) {
      currentRef = database.collection("groupInvites").doc(group.activeInviteId);
      currentDocument = await transaction.get(currentRef);
    }
    if (existingNext.exists) {
      if (group.activeInviteId === inviteId) {
        return credentialResult(inviteId, existingNext.data() as InviteData);
      }
      if (currentDocument?.exists) {
        const current = currentDocument.data() as InviteData;
        if (current.status === "active" && current.expiresAt.toMillis() > Date.now()) {
          return credentialResult(currentDocument.id, current);
        }
      }
      throw businessError("INVITE_INVALID");
    }

    const now = Timestamp.now();
    if (currentRef && currentDocument?.exists) {
      transaction.update(currentRef, {
        status: "revoked",
        revokedAt: now,
      });
    }
    transaction.create(nextInviteRef, proposal.data);
    transaction.update(groupRef, {
      activeInviteId: inviteId,
      updatedAt: now,
    });
    return proposal.result;
  });
}

async function findInviteDocument(
  kind: "token" | "code",
  value: string,
): Promise<DocumentSnapshot | null> {
  const hashField = kind === "token" ? "tokenHash" : "codeHash";
  const hash = sha256(value);
  const result = await getFirestore()
    .collection("groupInvites")
    .where(hashField, "==", hash)
    .limit(1)
    .get();
  return result.empty ? null : result.docs[0];
}

function assertInviteActive(invite: InviteData, inviteId: string, group: GroupData): void {
  if (invite.status !== "active" || group.activeInviteId !== inviteId) {
    throw businessError("INVITE_INVALID");
  }
  if (invite.expiresAt.toMillis() <= Date.now()) {
    throw businessError("INVITE_EXPIRED");
  }
}

async function invitePreview(
  inviteDocument: DocumentSnapshot,
): Promise<{
  groupId: string;
  groupName: string;
  memberCount: number;
  members: UserSnapshot[];
}> {
  const invite = inviteDocument.data() as InviteData;
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(invite.groupId);
  const [groupDocument, memberDocuments] = await Promise.all([
    groupRef.get(),
    groupRef.collection("members")
      .where("status", "==", "active")
      .limit(5)
      .get(),
  ]);
  const group = asGroup(groupDocument);
  assertActiveGroup(group);
  assertInviteActive(invite, inviteDocument.id, group);
  return {
    groupId: invite.groupId,
    groupName: group.name,
    memberCount: group.memberCount,
    members: memberDocuments.docs.map((document) => {
      const snapshot = document.get("profileSnapshot") as UserSnapshot | undefined;
      return {
        nickname: snapshot?.nickname ?? "成员",
        avatarPath: snapshot?.avatarPath ?? null,
      };
    }),
  };
}

export async function previewInviteHandler(
  request: CallableRequest<unknown>,
): Promise<{
  groupId: string;
  groupName: string;
  memberCount: number;
  members: UserSnapshot[];
}> {
  const uid = requireAuth(request);
  const credential = requireInviteCredential(requestData(request));
  if (credential.kind === "direct") {
    const database = getFirestore();
    const invitationDocument = await database.collection("users")
      .doc(uid)
      .collection("invitations")
      .doc(credential.value)
      .get();
    if (!invitationDocument.exists) {
      throw businessError("INVITE_INVALID");
    }
    const invitation = invitationDocument.data() as DirectInviteData;
    if (invitation.status !== "pending") {
      throw businessError("INVITE_INVALID");
    }
    if (invitation.expiresAt.toMillis() <= Date.now()) {
      throw businessError("INVITE_EXPIRED");
    }
    const groupRef = database.collection("groups").doc(invitation.groupId);
    const [groupDocument, memberDocuments] = await Promise.all([
      groupRef.get(),
      groupRef.collection("members")
        .where("status", "==", "active")
        .limit(5)
        .get(),
    ]);
    const group = asGroup(groupDocument);
    assertActiveGroup(group);
    return {
      groupId: invitation.groupId,
      groupName: group.name,
      memberCount: group.memberCount,
      members: memberDocuments.docs.map((document) => {
        const snapshot = document.get("profileSnapshot") as UserSnapshot | undefined;
        return {
          nickname: snapshot?.nickname ?? "成员",
          avatarPath: snapshot?.avatarPath ?? null,
        };
      }),
    };
  }
  if (credential.kind === "code") {
    assertInviteAttemptAllowed(uid);
  }
  const inviteDocument = await findInviteDocument(credential.kind, credential.value);
  if (!inviteDocument) {
    if (credential.kind === "code") {
      recordFailedInviteAttempt(uid);
    }
    throw businessError("INVITE_INVALID");
  }
  try {
    const result = await invitePreview(inviteDocument);
    clearInviteAttempts(uid);
    return result;
  } catch (error) {
    if (credential.kind === "code") {
      recordFailedInviteAttempt(uid);
    }
    throw error;
  }
}

async function activateMember(
  transaction: Transaction,
  groupRef: DocumentReference,
  group: GroupData,
  uid: string,
  userSnapshot: UserSnapshot,
  now: Timestamp,
): Promise<boolean> {
  const memberRef = groupRef.collection("members").doc(uid);
  const memberDocument = await transaction.get(memberRef);
  const member = asMember(memberDocument);
  if (member?.status === "active") {
    return false;
  }
  if (group.memberCount >= MAX_GROUP_MEMBERS) {
    throw businessError("GROUP_FULL");
  }
  const membership = {
    userId: uid,
    groupId: groupRef.id,
    role: "member",
    status: "active",
    profileSnapshot: userSnapshot,
    joinedAt: memberDocument.exists ?
      memberDocument.get("joinedAt") ?? now :
      now,
    updatedAt: now,
    leftAt: null,
    removedAt: null,
    removedBy: null,
    schemaVersion: 1,
  };
  transaction.set(memberRef, membership);
  transaction.update(groupRef, {
    memberCount: group.memberCount + 1,
    lastActivityAt: now,
    updatedAt: now,
  });
  return true;
}

export async function acceptInviteHandler(
  request: CallableRequest<unknown>,
): Promise<{groupId: string; alreadyMember: boolean}> {
  const uid = requireVerifiedAuth(request);
  const credential = requireInviteCredential(requestData(request));
  if (credential.kind === "direct") {
    throw businessError("VALIDATION");
  }
  if (credential.kind === "code") {
    assertInviteAttemptAllowed(uid);
  }
  const inviteDocument = await findInviteDocument(credential.kind, credential.value);
  if (!inviteDocument) {
    if (credential.kind === "code") recordFailedInviteAttempt(uid);
    throw businessError("INVITE_INVALID");
  }

  const inviteRef = inviteDocument.ref;
  const database = getFirestore();
  const userRef = database.collection("users").doc(uid);
  const groupId = (inviteDocument.data() as InviteData).groupId;
  const groupRef = database.collection("groups").doc(groupId);

  const joined = await database.runTransaction(async (transaction) => {
    const [currentInviteDocument, groupDocument, userDocument, memberDocument] =
      await Promise.all([
        transaction.get(inviteRef),
        transaction.get(groupRef),
        transaction.get(userRef),
        transaction.get(groupRef.collection("members").doc(uid)),
      ]);
    const group = asGroup(groupDocument);
    assertActiveGroup(group);
    const existingMember = asMember(memberDocument);
    if (existingMember?.status === "active") {
      return false;
    }
    if (!currentInviteDocument.exists) {
      throw businessError("INVITE_INVALID");
    }
    const invite = currentInviteDocument.data() as InviteData;
    assertInviteActive(invite, currentInviteDocument.id, group);
    const now = Timestamp.now();
    const changed = await activateMember(
      transaction,
      groupRef,
      group,
      uid,
      profileSnapshot(userDocument),
      now,
    );
    if (changed) {
      transaction.update(inviteRef, {useCount: FieldValue.increment(1)});
    }
    return changed;
  });

  clearInviteAttempts(uid);
  return {groupId, alreadyMember: !joined};
}

export async function sendDirectInviteHandler(
  request: CallableRequest<unknown>,
): Promise<{delivered: true}> {
  const uid = requireAuth(request);
  const data = requestData(request);
  const groupId = requireString(data.groupId, 1, 128);
  const email = normalizeEmail(data.email);
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(groupId);
  const [groupDocument, memberDocument, inviterDocument] = await Promise.all([
    groupRef.get(),
    groupRef.collection("members").doc(uid).get(),
    database.collection("users").doc(uid).get(),
  ]);
  const group = asGroup(groupDocument);
  assertActiveGroup(group);
  assertAdmin(group, asMember(memberDocument), uid);
  if (group.memberCount >= MAX_GROUP_MEMBERS) {
    throw businessError("GROUP_FULL");
  }

  let targetUid: string | null = null;
  try {
    targetUid = (await getAuth().getUserByEmail(email)).uid;
  } catch (error) {
    if ((error as {code?: string}).code !== "auth/user-not-found") {
      throw error;
    }
  }
  if (!targetUid || targetUid === uid) {
    return {delivered: true};
  }

  const invitationId = deterministicId("direct", `${groupId}:${targetUid}`);
  const invitationRef = database.collection("users")
    .doc(targetUid)
    .collection("invitations")
    .doc(invitationId);
  const now = Timestamp.now();
  const inviter = profileSnapshot(inviterDocument);
  await invitationRef.set({
    groupId,
    groupNameSnapshot: group.name,
    invitedBy: uid,
    inviterSnapshot: inviter,
    status: "pending",
    expiresAt: Timestamp.fromMillis(now.toMillis() + INVITE_LIFETIME_MILLIS),
    createdAt: now,
    respondedAt: null,
    schemaVersion: 1,
  });
  return {delivered: true};
}

export async function acceptDirectInviteHandler(
  request: CallableRequest<unknown>,
): Promise<{groupId: string; alreadyMember: boolean}> {
  const uid = requireVerifiedAuth(request);
  const invitationId = requireString(
    requestData(request).invitationId,
    1,
    128,
  );
  const database = getFirestore();
  const invitationRef = database.collection("users")
    .doc(uid)
    .collection("invitations")
    .doc(invitationId);
  const initialInvitation = await invitationRef.get();
  if (!initialInvitation.exists) {
    throw businessError("NOT_FOUND");
  }
  const groupId = (initialInvitation.data() as DirectInviteData).groupId;
  const groupRef = database.collection("groups").doc(groupId);
  const userRef = database.collection("users").doc(uid);

  const joined = await database.runTransaction(async (transaction) => {
    const [invitationDocument, groupDocument, userDocument, memberDocument] =
      await Promise.all([
        transaction.get(invitationRef),
        transaction.get(groupRef),
        transaction.get(userRef),
        transaction.get(groupRef.collection("members").doc(uid)),
      ]);
    if (!invitationDocument.exists) {
      throw businessError("NOT_FOUND");
    }
    const invitation = invitationDocument.data() as DirectInviteData;
    const group = asGroup(groupDocument);
    assertActiveGroup(group);
    const existingMember = asMember(memberDocument);
    if (existingMember?.status === "active") {
      if (invitation.status === "pending") {
        transaction.update(invitationRef, {
          status: "accepted",
          respondedAt: Timestamp.now(),
        });
      }
      return false;
    }
    if (invitation.status !== "pending") {
      throw businessError("INVITE_INVALID");
    }
    if (invitation.expiresAt.toMillis() <= Date.now()) {
      throw businessError("INVITE_EXPIRED");
    }
    const now = Timestamp.now();
    const changed = await activateMember(
      transaction,
      groupRef,
      group,
      uid,
      profileSnapshot(userDocument),
      now,
    );
    transaction.update(invitationRef, {
      status: "accepted",
      respondedAt: now,
    });
    return changed;
  });

  return {groupId, alreadyMember: !joined};
}

export async function leaveGroupHandler(
  request: CallableRequest<unknown>,
): Promise<{groupId: string}> {
  const uid = requireAuth(request);
  const groupId = requireString(requestData(request).groupId, 1, 128);
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(groupId);
  const memberRef = groupRef.collection("members").doc(uid);

  await database.runTransaction(async (transaction) => {
    const [groupDocument, memberDocument] = await Promise.all([
      transaction.get(groupRef),
      transaction.get(memberRef),
    ]);
    const group = asGroup(groupDocument);
    const member = asMember(memberDocument);
    if (member?.status !== "active") {
      return;
    }
    assertActiveGroup(group);
    if (group.adminId === uid || member.role === "admin") {
      throw businessError("ADMIN_CANNOT_LEAVE");
    }
    const now = Timestamp.now();
    transaction.update(memberRef, {
      status: "left",
      updatedAt: now,
      leftAt: now,
      removedAt: null,
      removedBy: null,
    });
    transaction.update(groupRef, {
      memberCount: Math.max(0, group.memberCount - 1),
      lastActivityAt: now,
      updatedAt: now,
    });
  });
  return {groupId};
}

export async function removeMemberHandler(
  request: CallableRequest<unknown>,
): Promise<{groupId: string; userId: string}> {
  const uid = requireAuth(request);
  const data = requestData(request);
  const groupId = requireString(data.groupId, 1, 128);
  const targetUserId = requireString(data.userId, 1, 128);
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(groupId);
  const adminRef = groupRef.collection("members").doc(uid);
  const targetRef = groupRef.collection("members").doc(targetUserId);

  await database.runTransaction(async (transaction) => {
    const [groupDocument, adminDocument, targetDocument] = await Promise.all([
      transaction.get(groupRef),
      transaction.get(adminRef),
      transaction.get(targetRef),
    ]);
    const group = asGroup(groupDocument);
    assertActiveGroup(group);
    assertAdmin(group, asMember(adminDocument), uid);
    const target = asMember(targetDocument);
    if (target?.status !== "active") {
      return;
    }
    if (target.role === "admin" || targetUserId === uid) {
      throw businessError("PERMISSION_DENIED");
    }
    const now = Timestamp.now();
    transaction.update(targetRef, {
      status: "removed",
      updatedAt: now,
      leftAt: null,
      removedAt: now,
      removedBy: uid,
    });
    transaction.update(groupRef, {
      memberCount: Math.max(0, group.memberCount - 1),
      lastActivityAt: now,
      updatedAt: now,
    });
  });
  return {groupId, userId: targetUserId};
}

export async function transferAdminHandler(
  request: CallableRequest<unknown>,
): Promise<{groupId: string; adminId: string}> {
  const uid = requireAuth(request);
  const data = requestData(request);
  const groupId = requireString(data.groupId, 1, 128);
  const targetUserId = requireString(data.userId, 1, 128);
  if (targetUserId === uid) {
    throw businessError("VALIDATION");
  }
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(groupId);
  const currentRef = groupRef.collection("members").doc(uid);
  const targetRef = groupRef.collection("members").doc(targetUserId);

  await database.runTransaction(async (transaction) => {
    const [groupDocument, currentDocument, targetDocument] = await Promise.all([
      transaction.get(groupRef),
      transaction.get(currentRef),
      transaction.get(targetRef),
    ]);
    const group = asGroup(groupDocument);
    assertActiveGroup(group);
    const current = asMember(currentDocument);
    const target = asMember(targetDocument);
    if (
      group.adminId === targetUserId &&
      target?.status === "active" &&
      target.role === "admin"
    ) {
      return;
    }
    assertAdmin(group, current, uid);
    if (target?.status !== "active" || target.role !== "member") {
      throw businessError("TARGET_NOT_MEMBER");
    }
    const now = Timestamp.now();
    transaction.update(currentRef, {
      role: "member",
      updatedAt: now,
    });
    transaction.update(targetRef, {
      role: "admin",
      updatedAt: now,
    });
    transaction.update(groupRef, {
      adminId: targetUserId,
      updatedAt: now,
      lastActivityAt: now,
    });
  });
  return {groupId, adminId: targetUserId};
}

export async function dissolveGroupHandler(
  request: CallableRequest<unknown>,
): Promise<{groupId: string}> {
  const uid = requireAuth(request);
  const data = requestData(request);
  const groupId = requireString(data.groupId, 1, 128);
  const confirmationName = normalizeGroupName(data.confirmationName);
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(groupId);
  const adminRef = groupRef.collection("members").doc(uid);

  await database.runTransaction(async (transaction) => {
    const [groupDocument, adminDocument, activeMembers] = await Promise.all([
      transaction.get(groupRef),
      transaction.get(adminRef),
      transaction.get(groupRef.collection("members").where("status", "==", "active")),
    ]);
    const group = asGroup(groupDocument);
    if (group.status === "dissolved" && group.adminId === uid) {
      return;
    }
    assertActiveGroup(group);
    assertAdmin(group, asMember(adminDocument), uid);
    if (group.name !== confirmationName) {
      throw businessError("VALIDATION");
    }
    const now = Timestamp.now();
    for (const memberDocument of activeMembers.docs) {
      transaction.update(memberDocument.ref, {
        status: "removed",
        updatedAt: now,
        leftAt: null,
        removedAt: now,
        removedBy: uid,
      });
    }
    transaction.update(groupRef, {
      status: "dissolved",
      memberCount: 0,
      activeInviteId: null,
      dissolvedAt: now,
      updatedAt: now,
      lastActivityAt: now,
    });
    if (group.activeInviteId) {
      transaction.update(
        database.collection("groupInvites").doc(group.activeInviteId),
        {
          status: "revoked",
          revokedAt: now,
        },
      );
    }
  });
  return {groupId};
}
