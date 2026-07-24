import {getStorage} from "firebase-admin/storage";
import {
  DocumentData,
  DocumentSnapshot,
  getFirestore,
  Timestamp,
} from "firebase-admin/firestore";
import type {
  Change,
  FirestoreEvent,
} from "firebase-functions/v2/firestore";

type WrittenEvent<Params extends Record<string, string>> =
  FirestoreEvent<Change<DocumentSnapshot> | undefined, Params>;

const EVENT_TTL_MILLIS = 8 * 24 * 60 * 60 * 1000;

function numberField(value: unknown): number {
  return typeof value === "number" && Number.isFinite(value) ?
    Math.max(0, Math.trunc(value)) :
    0;
}

function eventRecord(
  functionName: string,
  sourcePath: string,
  now: Timestamp,
): DocumentData {
  return {
    functionName,
    sourcePath,
    processedAt: now,
    expiresAt: Timestamp.fromMillis(now.toMillis() + EVENT_TTL_MILLIS),
    schemaVersion: 1,
  };
}

function ideaBucket(
  data: DocumentData | undefined,
): "ideaCount" | "scheduledCount" | "completedCount" | null {
  if (!data || data.isDeleted === true) return null;
  if (data.status === "idea") return "ideaCount";
  if (data.status === "scheduled") return "scheduledCount";
  if (data.status === "completed") return "completedCount";
  return null;
}

export async function maintainGroupIdeaCounts(
  event: WrittenEvent<{groupId: string; ideaId: string}>,
): Promise<void> {
  const database = getFirestore();
  const functionName = "maintainGroupIdeaCounts";
  const eventRef = database.collection("functionEvents")
    .doc(`${functionName}_${event.id}`);
  const groupRef = database.collection("groups").doc(event.params.groupId);
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  const bucketChanged = ideaBucket(before) !== ideaBucket(after);

  await database.runTransaction(async (transaction) => {
    const [processed, groupDocument] = await Promise.all([
      transaction.get(eventRef),
      transaction.get(groupRef),
    ]);
    if (processed.exists) return;
    const now = Timestamp.now();
    if (groupDocument.exists && bucketChanged) {
      const visibleIdeas = await transaction.get(
        groupRef.collection("ideas").where("isDeleted", "==", false),
      );
      const counts = {
        ideaCount: 0,
        scheduledCount: 0,
        completedCount: 0,
      };
      visibleIdeas.docs.forEach((document) => {
        const bucket = ideaBucket(document.data());
        if (bucket) counts[bucket] += 1;
      });
      transaction.update(groupRef, {
        ...counts,
        lastActivityAt: now,
        updatedAt: now,
      });
    }
    transaction.create(
      eventRef,
      eventRecord(
        functionName,
        `groups/{groupId}/ideas/${event.params.ideaId}`,
        now,
      ),
    );
  });

  await cleanupReplacedCover(event.params.groupId, event.params.ideaId, before, after);
}

function reactionValue(data: DocumentData | undefined): string | null {
  if (!data) return null;
  return ["want", "ok", "not_interested"].includes(data.value) ?
    data.value :
    null;
}

function reactionCountField(value: string): string {
  return value === "not_interested" ? "notInterested" : value;
}

export async function maintainReactionCounts(
  event: WrittenEvent<{groupId: string; ideaId: string; uid: string}>,
): Promise<void> {
  const database = getFirestore();
  const functionName = "maintainReactionCounts";
  const eventRef = database.collection("functionEvents")
    .doc(`${functionName}_${event.id}`);
  const ideaRef = database.collection("groups")
    .doc(event.params.groupId)
    .collection("ideas")
    .doc(event.params.ideaId);

  await database.runTransaction(async (transaction) => {
    const [processed, ideaDocument, reactions] = await Promise.all([
      transaction.get(eventRef),
      transaction.get(ideaRef),
      transaction.get(ideaRef.collection("reactions")),
    ]);
    if (processed.exists) return;
    const now = Timestamp.now();
    if (ideaDocument.exists) {
      const next = {
        want: 0,
        ok: 0,
        notInterested: 0,
      };
      reactions.docs.forEach((document) => {
        const value = reactionValue(document.data());
        if (!value) return;
        const field = reactionCountField(value) as keyof typeof next;
        next[field] += 1;
      });
      transaction.update(ideaRef, {reactionCounts: next});
    }
    transaction.create(
      eventRef,
      eventRecord(
        functionName,
        "groups/{groupId}/ideas/{ideaId}/reactions/{uid}",
        now,
      ),
    );
  });
}

function isVisibleComment(data: DocumentData | undefined): boolean {
  return Boolean(data) && data?.isDeleted === false;
}

export async function maintainCommentCount(
  event: WrittenEvent<{groupId: string; ideaId: string; commentId: string}>,
): Promise<void> {
  const database = getFirestore();
  const functionName = "maintainCommentCount";
  const eventRef = database.collection("functionEvents")
    .doc(`${functionName}_${event.id}`);
  const ideaRef = database.collection("groups")
    .doc(event.params.groupId)
    .collection("ideas")
    .doc(event.params.ideaId);

  await database.runTransaction(async (transaction) => {
    const [processed, ideaDocument, comments] = await Promise.all([
      transaction.get(eventRef),
      transaction.get(ideaRef),
      transaction.get(
        ideaRef.collection("comments").where("isDeleted", "==", false),
      ),
    ]);
    if (processed.exists) return;
    const now = Timestamp.now();
    if (ideaDocument.exists) {
      transaction.update(ideaRef, {
        commentCount: comments.size,
      });
    }
    transaction.create(
      eventRef,
      eventRecord(
        functionName,
        "groups/{groupId}/ideas/{ideaId}/comments/{commentId}",
        now,
      ),
    );
  });
}

export async function cleanupDepartedMemberResponses(
  event: WrittenEvent<{groupId: string; uid: string}>,
): Promise<void> {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (before?.status !== "active" || after?.status === "active") return;
  const cutoff = after?.updatedAt;
  if (!(cutoff instanceof Timestamp)) return;

  const database = getFirestore();
  for (const collectionName of ["reactions", "rsvps"]) {
    let snapshot = await database.collectionGroup(collectionName)
      .where("groupId", "==", event.params.groupId)
      .where("userId", "==", event.params.uid)
      .where("updatedAt", "<=", cutoff)
      .limit(400)
      .get();
    while (!snapshot.empty) {
      const batch = database.batch();
      snapshot.docs.forEach((document) => batch.delete(document.ref));
      await batch.commit();
      if (snapshot.size < 400) break;
      snapshot = await database.collectionGroup(collectionName)
        .where("groupId", "==", event.params.groupId)
        .where("userId", "==", event.params.uid)
        .where("updatedAt", "<=", cutoff)
        .limit(400)
        .get();
    }
  }
}

async function cleanupReplacedCover(
  groupId: string,
  ideaId: string,
  before: DocumentData | undefined,
  after: DocumentData | undefined,
): Promise<void> {
  const oldPath = typeof before?.media?.storagePath === "string" ?
    before.media.storagePath :
    null;
  const nextPath = typeof after?.media?.storagePath === "string" ?
    after.media.storagePath :
    null;
  const shouldDelete = oldPath !== null &&
    (after?.isDeleted === true || oldPath !== nextPath);
  const prefix = `groups/${groupId}/ideas/${ideaId}/cover/`;
  if (!shouldDelete || !oldPath.startsWith(prefix) || !oldPath.endsWith(".webp")) {
    return;
  }
  try {
    await getStorage().bucket().file(oldPath).delete({ignoreNotFound: true});
  } catch {
    // The aggregate is already committed. A later retry or orphan sweep can
    // safely retry cleanup; no local URI or user content is logged here.
  }
}

export const aggregateTestHelpers = {
  ideaBucket,
  reactionValue,
  numberField,
  isVisibleComment,
};
