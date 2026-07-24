import assert from "node:assert/strict";
import {test} from "node:test";
import {aggregateTestHelpers} from "./aggregates";

test("idea buckets include only visible supported statuses", () => {
  assert.equal(aggregateTestHelpers.ideaBucket({status: "idea", isDeleted: false}), "ideaCount");
  assert.equal(
    aggregateTestHelpers.ideaBucket({status: "scheduled", isDeleted: false}),
    "scheduledCount",
  );
  assert.equal(
    aggregateTestHelpers.ideaBucket({status: "completed", isDeleted: false}),
    "completedCount",
  );
  assert.equal(aggregateTestHelpers.ideaBucket({status: "idea", isDeleted: true}), null);
  assert.equal(aggregateTestHelpers.ideaBucket(undefined), null);
});

test("reaction values reject unsupported input", () => {
  assert.equal(aggregateTestHelpers.reactionValue({value: "want"}), "want");
  assert.equal(
    aggregateTestHelpers.reactionValue({value: "not_interested"}),
    "not_interested",
  );
  assert.equal(aggregateTestHelpers.reactionValue({value: "forged"}), null);
  assert.equal(aggregateTestHelpers.reactionValue(undefined), null);
});

test("RSVP values accept only the three supported states", () => {
  assert.equal(aggregateTestHelpers.rsvpValue({value: "going"}), "going");
  assert.equal(aggregateTestHelpers.rsvpValue({value: "maybe"}), "maybe");
  assert.equal(aggregateTestHelpers.rsvpValue({value: "not_going"}), "not_going");
  assert.equal(aggregateTestHelpers.rsvpValue({value: "want"}), null);
  assert.equal(aggregateTestHelpers.rsvpValue(undefined), null);
});

test("number fields are finite non-negative integers", () => {
  assert.equal(aggregateTestHelpers.numberField(3.9), 3);
  assert.equal(aggregateTestHelpers.numberField(-2), 0);
  assert.equal(aggregateTestHelpers.numberField(Number.NaN), 0);
  assert.equal(aggregateTestHelpers.numberField("3"), 0);
});

test("only non-deleted comments contribute to count", () => {
  assert.equal(aggregateTestHelpers.isVisibleComment({isDeleted: false}), true);
  assert.equal(aggregateTestHelpers.isVisibleComment({isDeleted: true}), false);
  assert.equal(aggregateTestHelpers.isVisibleComment(undefined), false);
});
