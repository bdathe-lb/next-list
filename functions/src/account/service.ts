import {getAuth} from "firebase-admin/auth";
import {
  getFirestore,
  Query,
  QueryDocumentSnapshot,
  Timestamp,
} from "firebase-admin/firestore";
import {getStorage} from "firebase-admin/storage";
import type {CallableRequest} from "firebase-functions/v2/https";
import {businessError} from "../groups/errors";

const DELETED_MEMBER_SNAPSHOT = {
  nickname: "已注销成员",
  avatarPath: null,
};

function requireAuth(request: CallableRequest<unknown>): string {
  if (!request.auth) throw businessError("UNAUTHENTICATED");
  return request.auth.uid;
}

export async function deleteAccountHandler(
  request: CallableRequest<unknown>,
): Promise<{deleted: true}> {
  const uid = requireAuth(request);
  const database = getFirestore();
  const userRef = database.collection("users").doc(uid);
  const profile = await userRef.get();
  const avatarPath = safeAvatarPath(uid, profile.get("avatarPath"));

  await leaveActiveMemberships(uid);
  await removePrivateAndTransientData(uid);
  await anonymizeHistoricalSnapshots(uid);
  await revokeCreatedInvites(uid);

  await userRef.set({
    nickname: DELETED_MEMBER_SNAPSHOT.nickname,
    avatarPath: null,
    emailVerified: false,
    notificationPrefs: {
      groupInvite: false,
      newSchedule: false,
      upcomingReminder: false,
      ideaComment: false,
    },
    status: "deleted",
    updatedAt: Timestamp.now(),
    schemaVersion: 1,
  }, {merge: true});

  if (avatarPath) await deleteAvatar(avatarPath);

  // Auth is deleted last so a transient cleanup failure can be retried with
  // the still-valid signed-in session.
  await getAuth().deleteUser(uid);
  return {deleted: true};
}

async function leaveActiveMemberships(uid: string): Promise<void> {
  const database = getFirestore();
  const memberships = await database.collectionGroup("members")
    .where("userId", "==", uid)
    .where("status", "==", "active")
    .get();
  const groupRefs = memberships.docs.map((membership) => {
    const groupRef = membership.ref.parent.parent;
    if (!groupRef) throw businessError("NOT_FOUND");
    return groupRef;
  });
  const groups = groupRefs.length > 0 ?
    await database.getAll(...groupRefs) :
    [];
  const groupsByPath = new Map(
    groups.map((group) => [group.ref.path, group]),
  );

  const ownsActiveGroup = memberships.docs.some((membership) => {
    const groupRef = membership.ref.parent.parent;
    const group = groupRef ? groupsByPath.get(groupRef.path) : undefined;
    return group?.get("status") === "active" &&
      (
        group.get("adminId") === uid ||
        membership.get("role") === "admin"
      );
  });
  if (ownsActiveGroup) throw businessError("ADMIN_CANNOT_LEAVE");

  for (const membership of memberships.docs) {
    const groupRef = membership.ref.parent.parent;
    if (!groupRef) continue;
    await database.runTransaction(async (transaction) => {
      const [currentMembership, currentGroup] = await Promise.all([
        transaction.get(membership.ref),
        transaction.get(groupRef),
      ]);
      if (
        !currentMembership.exists ||
        currentMembership.get("status") !== "active"
      ) {
        return;
      }
      if (
        currentGroup.get("status") === "active" &&
        (
          currentGroup.get("adminId") === uid ||
          currentMembership.get("role") === "admin"
        )
      ) {
        throw businessError("ADMIN_CANNOT_LEAVE");
      }
      const now = Timestamp.now();
      transaction.update(membership.ref, {
        status: "left",
        profileSnapshot: DELETED_MEMBER_SNAPSHOT,
        updatedAt: now,
        leftAt: now,
        removedAt: null,
        removedBy: null,
      });
      if (currentGroup.exists && currentGroup.get("status") === "active") {
        const memberCount = currentGroup.get("memberCount");
        transaction.update(groupRef, {
          memberCount: Math.max(
            0,
            typeof memberCount === "number" ? Math.trunc(memberCount) - 1 : 0,
          ),
          lastActivityAt: now,
          updatedAt: now,
        });
      }
    });
  }
}

async function removePrivateAndTransientData(uid: string): Promise<void> {
  const database = getFirestore();
  const userRef = database.collection("users").doc(uid);
  const [deliveries, reactions, rsvps] = await Promise.all([
    database.collectionGroup("deliveries").where("uid", "==", uid).get(),
    database.collectionGroup("reactions").where("userId", "==", uid).get(),
    database.collectionGroup("rsvps").where("userId", "==", uid).get(),
  ]);
  const writer = database.bulkWriter();
  [...deliveries.docs, ...reactions.docs, ...rsvps.docs]
    .forEach((document) => writer.delete(document.ref));
  await writer.close();
  await Promise.all([
    database.recursiveDelete(userRef.collection("devices")),
    database.recursiveDelete(userRef.collection("feed")),
    database.recursiveDelete(userRef.collection("invitations")),
  ]);
}

async function anonymizeHistoricalSnapshots(uid: string): Promise<void> {
  const database = getFirestore();
  const queries: Array<{
    query: Query;
    update: (document: QueryDocumentSnapshot) => Record<string, unknown>;
  }> = [
    {
      query: database.collectionGroup("members").where("userId", "==", uid),
      update: () => ({profileSnapshot: DELETED_MEMBER_SNAPSHOT}),
    },
    {
      query: database.collectionGroup("ideas").where("createdBy", "==", uid),
      update: () => ({creatorSnapshot: DELETED_MEMBER_SNAPSHOT}),
    },
    {
      query: database.collectionGroup("ideas")
        .where("schedule.scheduledBy", "==", uid),
      update: () => ({"schedule.schedulerSnapshot": DELETED_MEMBER_SNAPSHOT}),
    },
    {
      query: database.collectionGroup("ideas")
        .where("completion.completedBy", "==", uid),
      update: () => ({"completion.completerSnapshot": DELETED_MEMBER_SNAPSHOT}),
    },
    {
      query: database.collectionGroup("comments").where("createdBy", "==", uid),
      update: () => ({creatorSnapshot: DELETED_MEMBER_SNAPSHOT}),
    },
    {
      query: database.collectionGroup("feed").where("actorId", "==", uid),
      update: () => ({actorSnapshot: DELETED_MEMBER_SNAPSHOT}),
    },
    {
      query: database.collectionGroup("invitations").where("invitedBy", "==", uid),
      update: (document) => ({
        inviterSnapshot: DELETED_MEMBER_SNAPSHOT,
        ...(document.get("status") === "pending" ? {
          status: "revoked",
          respondedAt: Timestamp.now(),
        } : {}),
      }),
    },
  ];

  for (const operation of queries) {
    const documents = await operation.query.get();
    const writer = database.bulkWriter();
    documents.docs.forEach((document) => {
      writer.update(document.ref, operation.update(document));
    });
    await writer.close();
  }
}

async function revokeCreatedInvites(uid: string): Promise<void> {
  const database = getFirestore();
  const invites = await database.collection("groupInvites")
    .where("createdBy", "==", uid)
    .where("status", "==", "active")
    .get();
  const now = Timestamp.now();
  const writer = database.bulkWriter();
  invites.docs.forEach((invite) => {
    writer.update(invite.ref, {
      status: "revoked",
      revokedAt: now,
    });
  });
  await writer.close();
}

function safeAvatarPath(uid: string, value: unknown): string | null {
  if (typeof value !== "string") return null;
  return value.startsWith(`users/${uid}/avatar/`) &&
    /^[A-Za-z0-9_/-]+[.]webp$/.test(value) ?
    value :
    null;
}

async function deleteAvatar(path: string): Promise<void> {
  try {
    await getStorage().bucket().file(path).delete();
  } catch (error) {
    if ((error as {code?: number}).code !== 404) throw error;
  }
}

export const accountDeletionTestHelpers = {
  safeAvatarPath,
};
