import assert from "node:assert/strict";
import {test} from "node:test";
import {Timestamp} from "firebase-admin/firestore";
import {notificationTestHelpers} from "./service";
import {reminderTestHelpers} from "./reminders";

test("feed and delivery identifiers are deterministic without exposing input", () => {
  const first = notificationTestHelpers.hashId(
    "feed",
    "cloud-event-id:recipient-uid",
  );
  const duplicate = notificationTestHelpers.hashId(
    "feed",
    "cloud-event-id:recipient-uid",
  );
  const other = notificationTestHelpers.hashId(
    "feed",
    "cloud-event-id:other-uid",
  );
  assert.equal(first, duplicate);
  assert.notEqual(first, other);
  assert.equal(first.includes("recipient-uid"), false);
});

test("only supported token errors are treated as permanent", () => {
  assert.equal(
    notificationTestHelpers.isInvalidToken(
      "messaging/registration-token-not-registered",
    ),
    true,
  );
  assert.equal(
    notificationTestHelpers.isInvalidToken("messaging/internal-error"),
    false,
  );
});

test("schedule helpers reject malformed revisions and timestamps", () => {
  const startAt = Timestamp.fromMillis(1_000);
  assert.equal(
    notificationTestHelpers.scheduleStart({
      schedule: {startAt},
    })?.toMillis(),
    1_000,
  );
  assert.equal(
    notificationTestHelpers.scheduleRevision({
      schedule: {revision: 2},
    }),
    2,
  );
  assert.equal(
    notificationTestHelpers.scheduleRevision({
      schedule: {revision: 1.5},
    }),
    null,
  );
});

test("reminder window and claim lease match the product contract", () => {
  assert.equal(reminderTestHelpers.REMINDER_WINDOW_MILLIS, 30 * 60 * 1_000);
  assert.equal(reminderTestHelpers.CLAIM_LEASE_MILLIS, 10 * 60 * 1_000);
});

test("too-late uses the saved schedule time and keeps the exact boundary", () => {
  const scheduledAt = Timestamp.fromMillis(1_000_000);
  assert.equal(
    notificationTestHelpers.isReminderTooLate(
      Timestamp.fromMillis(scheduledAt.toMillis() + 30 * 60 * 1_000),
      scheduledAt,
    ),
    false,
  );
  assert.equal(
    notificationTestHelpers.isReminderTooLate(
      Timestamp.fromMillis(scheduledAt.toMillis() + 30 * 60 * 1_000 - 1),
      scheduledAt,
    ),
    true,
  );
});
