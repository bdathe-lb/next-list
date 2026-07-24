import {getFirestore, Timestamp} from "firebase-admin/firestore";
import type {FirestoreEvent, Change, DocumentSnapshot} from "firebase-functions/v2/firestore";

interface UserProfileData {
  nickname?: unknown;
  avatarPath?: unknown;
}

export async function syncMembershipProfileSnapshots(
  event: FirestoreEvent<Change<DocumentSnapshot> | undefined, {uid: string}>,
): Promise<void> {
  const before = event.data?.before.data() as UserProfileData | undefined;
  const after = event.data?.after.data() as UserProfileData | undefined;
  if (!after || typeof after.nickname !== "string") {
    return;
  }
  const avatarPath = typeof after.avatarPath === "string" ? after.avatarPath : null;
  if (before?.nickname === after.nickname && before?.avatarPath === avatarPath) {
    return;
  }

  const database = getFirestore();
  let lastDocument: DocumentSnapshot | undefined;
  do {
    let query = database.collectionGroup("members")
      .where("userId", "==", event.params.uid)
      .where("status", "==", "active")
      .orderBy("updatedAt", "desc")
      .limit(400);
    if (lastDocument) {
      query = query.startAfter(lastDocument);
    }
    const memberships = await query.get();
    if (memberships.empty) {
      return;
    }
    const batch = database.batch();
    const now = Timestamp.now();
    for (const membership of memberships.docs) {
      batch.update(membership.ref, {
        profileSnapshot: {
          nickname: after.nickname,
          avatarPath,
        },
        updatedAt: now,
      });
    }
    await batch.commit();
    lastDocument = memberships.docs.at(-1);
    if (memberships.size < 400) {
      return;
    }
  } while (lastDocument);
}
