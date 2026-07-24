import {
  DocumentReference,
  getFirestore,
  Timestamp,
} from "firebase-admin/firestore";
import {
  deliverPush,
  firebasePushSender,
  PushSender,
} from "./service";

const REMINDER_WINDOW_MILLIS = 30 * 60 * 1000;
const CLAIM_LEASE_MILLIS = 10 * 60 * 1000;

interface ReminderClaim {
  ideaRef: DocumentReference;
  groupId: string;
  ideaId: string;
  startAt: Timestamp;
  revision: number;
}

async function claimReminder(
  ideaRef: DocumentReference,
  now: Timestamp,
): Promise<ReminderClaim | null> {
  const database = getFirestore();
  return database.runTransaction(async (transaction) => {
    const idea = await transaction.get(ideaRef);
    const startAt = idea.get("schedule.startAt");
    const revision = idea.get("schedule.revision");
    const scheduleUpdatedAt = idea.get("schedule.updatedAt");
    const claimedAt = idea.get("reminderClaimedAt");
    if (
      !idea.exists ||
      idea.get("status") !== "scheduled" ||
      idea.get("isDeleted") !== false ||
      idea.get("reminderSentAt") !== null ||
      idea.get("reminderSkippedReason") !== null ||
      !(startAt instanceof Timestamp) ||
      startAt.toMillis() <= now.toMillis() ||
      startAt.toMillis() > now.toMillis() + REMINDER_WINDOW_MILLIS ||
      typeof revision !== "number" ||
      !Number.isInteger(revision) ||
      revision < 1
    ) {
      return null;
    }
    if (
      scheduleUpdatedAt instanceof Timestamp &&
      startAt.toMillis() - scheduleUpdatedAt.toMillis() < REMINDER_WINDOW_MILLIS
    ) {
      transaction.update(ideaRef, {
        reminderClaimedAt: null,
        reminderSkippedReason: "too_late",
      });
      return null;
    }
    if (
      claimedAt instanceof Timestamp &&
      now.toMillis() - claimedAt.toMillis() < CLAIM_LEASE_MILLIS
    ) {
      return null;
    }
    transaction.update(ideaRef, {reminderClaimedAt: now});
    const groupId = idea.get("groupId");
    if (typeof groupId !== "string") return null;
    return {
      ideaRef,
      groupId,
      ideaId: ideaRef.id,
      startAt,
      revision,
    };
  });
}

async function reminderRecipients(claim: ReminderClaim): Promise<Array<{uid: string}>> {
  const database = getFirestore();
  const groupRef = database.collection("groups").doc(claim.groupId);
  const [group, members, rsvps] = await Promise.all([
    groupRef.get(),
    groupRef.collection("members").where("status", "==", "active").get(),
    claim.ideaRef.collection("rsvps").get(),
  ]);
  if (!group.exists || group.get("status") !== "active") return [];
  const declined = new Set(
    rsvps.docs
      .filter((document) => document.get("value") === "not_going")
      .map((document) => document.id),
  );
  return members.docs
    .filter((document) => !declined.has(document.id))
    .map((document) => ({uid: document.id}));
}

async function finishReminder(
  claim: ReminderClaim,
  sentAt: Timestamp,
): Promise<void> {
  await getFirestore().runTransaction(async (transaction) => {
    const current = await transaction.get(claim.ideaRef);
    const startAt = current.get("schedule.startAt");
    if (
      current.get("status") === "scheduled" &&
      current.get("isDeleted") === false &&
      current.get("schedule.revision") === claim.revision &&
      startAt instanceof Timestamp &&
      startAt.toMillis() === claim.startAt.toMillis() &&
      current.get("reminderSentAt") === null &&
      current.get("reminderSkippedReason") === null
    ) {
      transaction.update(claim.ideaRef, {
        reminderClaimedAt: null,
        reminderSentAt: sentAt,
      });
    }
  });
}

export async function sendUpcomingReminders(
  sender: PushSender = firebasePushSender,
  now: Timestamp = Timestamp.now(),
): Promise<void> {
  const deadline = Timestamp.fromMillis(
    now.toMillis() + REMINDER_WINDOW_MILLIS,
  );
  const candidates = await getFirestore().collectionGroup("ideas")
    .where("isDeleted", "==", false)
    .where("status", "==", "scheduled")
    .where("reminderSentAt", "==", null)
    .where("reminderSkippedReason", "==", null)
    .where("schedule.startAt", ">", now)
    .where("schedule.startAt", "<=", deadline)
    .limit(200)
    .get();
  let failed = false;
  for (const candidate of candidates.docs) {
    const claim = await claimReminder(candidate.ref, now);
    if (!claim) continue;
    const recipients = await reminderRecipients(claim);
    const eventKey = `reminder_${claim.groupId}_${claim.ideaId}` +
      `_${claim.startAt.toMillis()}`;
    const succeeded = await deliverPush(eventKey, recipients, {
      type: "upcoming_reminder",
      groupId: claim.groupId,
      ideaId: claim.ideaId,
    }, sender);
    if (succeeded) {
      await finishReminder(claim, Timestamp.now());
    } else {
      failed = true;
    }
  }
  if (failed) throw new Error("Temporary reminder delivery failure");
}

export const reminderTestHelpers = {
  CLAIM_LEASE_MILLIS,
  REMINDER_WINDOW_MILLIS,
};
