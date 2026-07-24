import {createHash} from "node:crypto";
import {
  DocumentData,
  DocumentReference,
  DocumentSnapshot,
  getFirestore,
  Timestamp,
} from "firebase-admin/firestore";
import {getMessaging} from "firebase-admin/messaging";
import type {
  Change,
  FirestoreEvent,
} from "firebase-functions/v2/firestore";

const FEED_TTL_MILLIS = 90 * 24 * 60 * 60 * 1000;
const EVENT_TTL_MILLIS = 8 * 24 * 60 * 60 * 1000;
const DELIVERY_LEASE_MILLIS = 10 * 60 * 1000;
const MAX_MULTICAST_SIZE = 500;
const MAX_DEVICES_PER_USER = 20;
const REMINDER_WINDOW_MILLIS = 30 * 60 * 1000;

export type FeedType =
  "idea_created" |
  "schedule_created" |
  "schedule_updated" |
  "idea_commented" |
  "idea_completed" |
  "group_invited";

export type PushType =
  "schedule_created" |
  "schedule_updated" |
  "upcoming_reminder" |
  "idea_commented" |
  "group_invited";

interface UserSnapshot {
  nickname: string;
  avatarPath: string | null;
}

interface Recipient {
  uid: string;
}

interface FeedPayload {
  type: FeedType;
  groupId: string;
  groupNameSnapshot: string;
  ideaId: string | null;
  ideaTitleSnapshot: string | null;
  invitationId: string | null;
  actorId: string;
  actorSnapshot: UserSnapshot;
  createdAt: Timestamp;
}

export interface PushData {
  type: PushType;
  groupId: string;
  ideaId?: string;
  invitationId?: string;
}

export interface PushRequest {
  token: string;
  data: PushData;
}

export interface PushResult {
  success: boolean;
  errorCode?: string;
}

export type PushSender = (
  requests: PushRequest[],
) => Promise<PushResult[]>;

type IdeaWrittenEvent = FirestoreEvent<
  Change<DocumentSnapshot> | undefined,
  {groupId: string; ideaId: string}
>;

type CommentWrittenEvent = FirestoreEvent<
  Change<DocumentSnapshot> | undefined,
  {groupId: string; ideaId: string; commentId: string}
>;

type InvitationWrittenEvent = FirestoreEvent<
  Change<DocumentSnapshot> | undefined,
  {uid: string; invitationId: string}
>;

interface DeviceDelivery {
  uid: string;
  deviceId: string;
  token: string;
  ref: DocumentReference;
}

function hashId(prefix: string, material: string): string {
  const digest = createHash("sha256").update(material, "utf8").digest("hex");
  return `${prefix}_${digest.slice(0, 40)}`;
}

function userSnapshot(value: unknown): UserSnapshot | null {
  if (!value || typeof value !== "object" || Array.isArray(value)) return null;
  const snapshot = value as Record<string, unknown>;
  if (typeof snapshot.nickname !== "string") return null;
  return {
    nickname: snapshot.nickname,
    avatarPath: typeof snapshot.avatarPath === "string" ?
      snapshot.avatarPath :
      null,
  };
}

function timestamp(value: unknown, fallback = Timestamp.now()): Timestamp {
  return value instanceof Timestamp ? value : fallback;
}

function scheduleStart(data: DocumentData | undefined): Timestamp | null {
  return data?.schedule?.startAt instanceof Timestamp ?
    data.schedule.startAt :
    null;
}

function scheduleRevision(data: DocumentData | undefined): number | null {
  const revision = data?.schedule?.revision;
  return typeof revision === "number" &&
    Number.isInteger(revision) &&
    revision >= 1 ?
    revision :
    null;
}

function isReminderTooLate(startAt: Timestamp, scheduledAt: Timestamp): boolean {
  return startAt.toMillis() - scheduledAt.toMillis() < REMINDER_WINDOW_MILLIS;
}

function eventRecord(functionName: string, sourcePath: string): DocumentData {
  const now = Timestamp.now();
  return {
    functionName,
    sourcePath,
    processedAt: now,
    expiresAt: Timestamp.fromMillis(now.toMillis() + EVENT_TTL_MILLIS),
    schemaVersion: 1,
  };
}

async function recordEvent(
  eventKey: string,
  functionName: string,
  sourcePath: string,
): Promise<void> {
  const database = getFirestore();
  const eventRef = database.collection("functionEvents").doc(eventKey);
  await database.runTransaction(async (transaction) => {
    const existing = await transaction.get(eventRef);
    if (!existing.exists) {
      transaction.create(eventRef, eventRecord(functionName, sourcePath));
    }
  });
}

async function activeRecipients(
  groupId: string,
  excludedUid?: string,
): Promise<Recipient[]> {
  const members = await getFirestore().collection("groups")
    .doc(groupId)
    .collection("members")
    .where("status", "==", "active")
    .get();
  return members.docs
    .filter((document) => document.id !== excludedUid)
    .map((document) => ({uid: document.id}));
}

async function createFeedEntries(
  eventKey: string,
  recipients: Recipient[],
  payload: FeedPayload,
): Promise<void> {
  const database = getFirestore();
  await Promise.all(recipients.map(async ({uid}) => {
    const feedId = hashId("feed", `${eventKey}:${uid}`);
    const feedRef = database.collection("users")
      .doc(uid)
      .collection("feed")
      .doc(feedId);
    await database.runTransaction(async (transaction) => {
      const existing = await transaction.get(feedRef);
      if (existing.exists) return;
      transaction.create(feedRef, {
        ...payload,
        readAt: null,
        expiresAt: Timestamp.fromMillis(
          payload.createdAt.toMillis() + FEED_TTL_MILLIS,
        ),
        schemaVersion: 1,
      });
    });
  }));
}

function preferenceField(type: PushType): string {
  if (type === "group_invited") return "groupInvite";
  if (type === "upcoming_reminder") return "upcomingReminder";
  if (type === "idea_commented") return "ideaComment";
  return "newSchedule";
}

async function eligibleDevices(
  recipients: Recipient[],
  type: PushType,
): Promise<DeviceDelivery[]> {
  const database = getFirestore();
  const field = preferenceField(type);
  const deliveries = await Promise.all(recipients.map(async ({uid}) => {
    const userRef = database.collection("users").doc(uid);
    const [user, devices] = await Promise.all([
      userRef.get(),
      userRef.collection("devices")
        .orderBy("updatedAt", "desc")
        .limit(MAX_DEVICES_PER_USER)
        .get(),
    ]);
    if (!user.exists || user.get(`notificationPrefs.${field}`) === false) {
      return [];
    }
    return devices.docs.flatMap((device) => {
      const token = device.get("token");
      const platform = device.get("platform");
      if (
        typeof token !== "string" ||
        token.length < 1 ||
        platform !== "android"
      ) {
        return [];
      }
      return [{
        uid,
        deviceId: device.id,
        token,
        ref: device.ref,
      }];
    });
  }));
  return deliveries.flat();
}

function isInvalidToken(errorCode: string | undefined): boolean {
  return errorCode === "messaging/registration-token-not-registered" ||
    errorCode === "messaging/invalid-registration-token" ||
    errorCode === "messaging/invalid-argument";
}

async function claimDeliveries(
  eventKey: string,
  deliveries: DeviceDelivery[],
): Promise<Array<DeviceDelivery & {deliveryRef: DocumentReference}>> {
  const database = getFirestore();
  const eventRef = database.collection("functionEvents").doc(eventKey);
  const now = Timestamp.now();
  const claimed = await Promise.all(deliveries.map(async (delivery) => {
    const deliveryId = hashId(
      "delivery",
      `${delivery.uid}:${delivery.deviceId}`,
    );
    const deliveryRef = eventRef.collection("deliveries").doc(deliveryId);
    return database.runTransaction(async (transaction) => {
      const existing = await transaction.get(deliveryRef);
      if (existing.get("succeededAt") instanceof Timestamp) return null;
      const claimedAt = existing.get("claimedAt");
      if (
        claimedAt instanceof Timestamp &&
        now.toMillis() - claimedAt.toMillis() < DELIVERY_LEASE_MILLIS
      ) {
        return null;
      }
      transaction.set(deliveryRef, {
        uid: delivery.uid,
        deviceId: delivery.deviceId,
        claimedAt: now,
        succeededAt: null,
        lastErrorKind: null,
        updatedAt: now,
        expiresAt: Timestamp.fromMillis(now.toMillis() + EVENT_TTL_MILLIS),
        schemaVersion: 1,
      }, {merge: true});
      return {...delivery, deliveryRef};
    });
  }));
  return claimed.filter(
    (value): value is DeviceDelivery & {deliveryRef: DocumentReference} =>
      value !== null,
  );
}

export const firebasePushSender: PushSender = async (requests) => {
  const results: PushResult[] = [];
  for (let index = 0; index < requests.length; index += MAX_MULTICAST_SIZE) {
    const chunk = requests.slice(index, index + MAX_MULTICAST_SIZE);
    const response = await getMessaging().sendEach(
      chunk.map((request) => ({
        token: request.token,
        data: {
          type: request.data.type,
          groupId: request.data.groupId,
          ...(request.data.ideaId ? {ideaId: request.data.ideaId} : {}),
          ...(request.data.invitationId ?
            {invitationId: request.data.invitationId} :
            {}),
        },
        android: {priority: "high" as const},
      })),
    );
    results.push(...response.responses.map((item) => ({
      success: item.success,
      errorCode: item.error?.code,
    })));
  }
  return results;
};

export async function deliverPush(
  eventKey: string,
  recipients: Recipient[],
  data: PushData,
  sender: PushSender = firebasePushSender,
): Promise<boolean> {
  await recordEvent(eventKey, "deliverPush", "notifications");
  const devices = await eligibleDevices(recipients, data.type);
  const claimed = await claimDeliveries(eventKey, devices);
  if (claimed.length === 0) {
    if (devices.length === 0) return true;
    const eventRef = getFirestore().collection("functionEvents").doc(eventKey);
    const states = await Promise.all(devices.map((delivery) => {
      const deliveryId = hashId(
        "delivery",
        `${delivery.uid}:${delivery.deviceId}`,
      );
      return eventRef.collection("deliveries").doc(deliveryId).get();
    }));
    return states.every(
      (state) => state.get("succeededAt") instanceof Timestamp,
    );
  }
  let results: PushResult[];
  try {
    results = await sender(
      claimed.map((delivery) => ({token: delivery.token, data})),
    );
  } catch {
    const now = Timestamp.now();
    await Promise.all(claimed.map((delivery) => delivery.deliveryRef.update({
      claimedAt: null,
      lastErrorKind: "temporary",
      updatedAt: now,
    })));
    return false;
  }
  if (results.length !== claimed.length) {
    return false;
  }
  const now = Timestamp.now();
  let allSucceeded = true;
  await Promise.all(results.map(async (result, index) => {
    const delivery = claimed[index];
    if (result.success) {
      await delivery.deliveryRef.update({
        succeededAt: now,
        claimedAt: null,
        lastErrorKind: null,
        updatedAt: now,
      });
      return;
    }
    if (isInvalidToken(result.errorCode)) {
      await Promise.all([
        delivery.ref.delete(),
        delivery.deliveryRef.update({
          succeededAt: now,
          claimedAt: null,
          lastErrorKind: "invalid_token",
          updatedAt: now,
        }),
      ]);
      return;
    }
    allSucceeded = false;
    await delivery.deliveryRef.update({
      claimedAt: null,
      lastErrorKind: "temporary",
      updatedAt: now,
    });
  }));
  return allSucceeded;
}

async function groupAndActor(
  groupId: string,
  actorId: string,
  embeddedSnapshot: unknown,
): Promise<{
  groupName: string;
  actorSnapshot: UserSnapshot;
} | null> {
  const database = getFirestore();
  const [group, actorMembership] = await Promise.all([
    database.collection("groups").doc(groupId).get(),
    database.collection("groups").doc(groupId)
      .collection("members").doc(actorId).get(),
  ]);
  if (!group.exists || group.get("status") !== "active") return null;
  const actorSnapshot = userSnapshot(actorMembership.get("profileSnapshot")) ??
    userSnapshot(embeddedSnapshot);
  const groupName = group.get("name");
  if (!actorSnapshot || typeof groupName !== "string") return null;
  return {groupName, actorSnapshot};
}

async function resetReminderState(
  ideaRef: DocumentReference,
  expectedRevision: number,
  startAt: Timestamp,
  scheduledAt: Timestamp,
  eventKey: string,
): Promise<void> {
  const database = getFirestore();
  const skipped = isReminderTooLate(startAt, scheduledAt);
  const resetRef = database.collection("functionEvents").doc(eventKey);
  await database.runTransaction(async (transaction) => {
    const [current, processed] = await Promise.all([
      transaction.get(ideaRef),
      transaction.get(resetRef),
    ]);
    if (processed.exists) return;
    if (
      current.get("status") !== "scheduled" ||
      current.get("isDeleted") !== false ||
      current.get("schedule.revision") !== expectedRevision ||
      !(current.get("schedule.startAt") instanceof Timestamp) ||
      current.get("schedule.startAt").toMillis() !== startAt.toMillis() ||
      !(current.get("schedule.updatedAt") instanceof Timestamp) ||
      current.get("schedule.updatedAt").toMillis() !== scheduledAt.toMillis()
    ) {
      return;
    }
    transaction.update(ideaRef, {
      reminderClaimedAt: null,
      reminderSentAt: null,
      reminderSkippedReason: skipped ? "too_late" : null,
    });
    transaction.create(
      resetRef,
      eventRecord("resetReminderState", "groups/{groupId}/ideas/{ideaId}"),
    );
  });
}

export async function processIdeaActivity(
  event: IdeaWrittenEvent,
  sender: PushSender = firebasePushSender,
): Promise<void> {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (!after || after.isDeleted === true) return;
  const groupId = event.params.groupId;
  const ideaId = event.params.ideaId;
  const sourcePath = "groups/{groupId}/ideas/{ideaId}";
  const events: Array<{
    type: FeedType;
    actorId: string;
    actorSnapshot: unknown;
    shouldPush: boolean;
    createdAt: Timestamp;
  }> = [];

  if (!before && after.status === "idea") {
    events.push({
      type: "idea_created",
      actorId: after.createdBy,
      actorSnapshot: after.creatorSnapshot,
      shouldPush: false,
      createdAt: timestamp(after.createdAt),
    });
  }
  const beforeRevision = scheduleRevision(before);
  const afterRevision = scheduleRevision(after);
  const scheduledNow = before?.status === "idea" &&
    after.status === "scheduled" &&
    afterRevision === 1;
  const scheduleUpdated = before?.status === "scheduled" &&
    after.status === "scheduled" &&
    beforeRevision !== null &&
    afterRevision !== null &&
    afterRevision > beforeRevision;
  const oldStart = scheduleStart(before);
  const newStart = scheduleStart(after);
  const startChanged = newStart !== null &&
    (oldStart === null || oldStart.toMillis() !== newStart.toMillis());
  if (scheduledNow || scheduleUpdated) {
    const scheduleUpdatedAt = timestamp(after.schedule?.updatedAt);
    events.push({
      type: scheduledNow ? "schedule_created" : "schedule_updated",
      actorId: after.schedule?.updatedBy,
      actorSnapshot: scheduledNow ?
        after.schedule?.schedulerSnapshot :
        userSnapshot(after.schedule?.updaterSnapshot) ??
          after.schedule?.schedulerSnapshot,
      shouldPush: scheduledNow || startChanged,
      createdAt: scheduleUpdatedAt,
    });
    if (startChanged && newStart && afterRevision) {
      await resetReminderState(
        event.data?.after.ref ??
          getFirestore().collection("groups").doc(groupId)
            .collection("ideas").doc(ideaId),
        afterRevision,
        newStart,
        scheduleUpdatedAt,
        hashId("reminderReset", `${event.id}:${newStart.toMillis()}`),
      );
    }
  }
  if (before?.status !== "completed" && after.status === "completed") {
    events.push({
      type: "idea_completed",
      actorId: after.completion?.updatedBy,
      actorSnapshot: after.completion?.completerSnapshot,
      shouldPush: false,
      createdAt: timestamp(after.completion?.updatedAt),
    });
  }

  for (const activity of events) {
    if (typeof activity.actorId !== "string") continue;
    const context = await groupAndActor(
      groupId,
      activity.actorId,
      activity.actorSnapshot,
    );
    if (!context) continue;
    const recipients = await activeRecipients(groupId, activity.actorId);
    const eventKey = hashId(
      "activity",
      `${event.id}:${activity.type}`,
    );
    await recordEvent(eventKey, "processIdeaActivity", sourcePath);
    await createFeedEntries(eventKey, recipients, {
      type: activity.type,
      groupId,
      groupNameSnapshot: context.groupName,
      ideaId,
      ideaTitleSnapshot: typeof after.title === "string" ? after.title : null,
      invitationId: null,
      actorId: activity.actorId,
      actorSnapshot: context.actorSnapshot,
      createdAt: activity.createdAt,
    });
    if (activity.shouldPush) {
      const succeeded = await deliverPush(eventKey, recipients, {
        type: activity.type as "schedule_created" | "schedule_updated",
        groupId,
        ideaId,
      }, sender);
      if (!succeeded) throw new Error("Temporary notification delivery failure");
    }
  }
}

export async function processCommentActivity(
  event: CommentWrittenEvent,
  sender: PushSender = firebasePushSender,
): Promise<void> {
  const before = event.data?.before.data();
  const after = event.data?.after.data();
  if (before || !after || after.isDeleted !== false) return;
  const actorId = after.createdBy;
  if (typeof actorId !== "string") return;
  const database = getFirestore();
  const idea = await database.collection("groups")
    .doc(event.params.groupId)
    .collection("ideas")
    .doc(event.params.ideaId)
    .get();
  if (
    !idea.exists ||
    idea.get("isDeleted") !== false ||
    typeof idea.get("createdBy") !== "string" ||
    idea.get("createdBy") === actorId
  ) {
    return;
  }
  const creatorId = idea.get("createdBy") as string;
  const creatorMembership = await database.collection("groups")
    .doc(event.params.groupId)
    .collection("members")
    .doc(creatorId)
    .get();
  if (creatorMembership.get("status") !== "active") return;
  const context = await groupAndActor(
    event.params.groupId,
    actorId,
    after.creatorSnapshot,
  );
  if (!context) return;
  const recipients = [{uid: creatorId}];
  const eventKey = hashId("activity", `${event.id}:idea_commented`);
  await recordEvent(
    eventKey,
    "processCommentActivity",
    "groups/{groupId}/ideas/{ideaId}/comments/{commentId}",
  );
  await createFeedEntries(eventKey, recipients, {
    type: "idea_commented",
    groupId: event.params.groupId,
    groupNameSnapshot: context.groupName,
    ideaId: event.params.ideaId,
    ideaTitleSnapshot: typeof idea.get("title") === "string" ?
      idea.get("title") :
      null,
    invitationId: null,
    actorId,
    actorSnapshot: context.actorSnapshot,
    createdAt: timestamp(after.createdAt),
  });
  const succeeded = await deliverPush(eventKey, recipients, {
    type: "idea_commented",
    groupId: event.params.groupId,
    ideaId: event.params.ideaId,
  }, sender);
  if (!succeeded) throw new Error("Temporary notification delivery failure");
}

export async function processDirectInvitation(
  event: InvitationWrittenEvent,
  sender: PushSender = firebasePushSender,
): Promise<void> {
  const before = event.data?.before.data();
  const invitation = event.data?.after.data();
  if (
    !invitation ||
    invitation.status !== "pending" ||
    before?.status === "pending"
  ) {
    return;
  }
  const actorId = invitation.invitedBy;
  const groupId = invitation.groupId;
  if (typeof actorId !== "string" || typeof groupId !== "string") return;
  const actorSnapshot = userSnapshot(invitation.inviterSnapshot);
  const groupName = invitation.groupNameSnapshot;
  if (!actorSnapshot || typeof groupName !== "string") return;
  const recipients = [{uid: event.params.uid}];
  const eventKey = hashId("activity", `${event.id}:group_invited`);
  await recordEvent(
    eventKey,
    "processDirectInvitation",
    "users/{uid}/invitations/{invitationId}",
  );
  await createFeedEntries(eventKey, recipients, {
    type: "group_invited",
    groupId,
    groupNameSnapshot: groupName,
    ideaId: null,
    ideaTitleSnapshot: null,
    invitationId: event.params.invitationId,
    actorId,
    actorSnapshot,
    createdAt: timestamp(invitation.createdAt),
  });
  const succeeded = await deliverPush(eventKey, recipients, {
    type: "group_invited",
    groupId,
    invitationId: event.params.invitationId,
  }, sender);
  if (!succeeded) throw new Error("Temporary notification delivery failure");
}

export const notificationTestHelpers = {
  hashId,
  isInvalidToken,
  isReminderTooLate,
  scheduleRevision,
  scheduleStart,
  userSnapshot,
};
