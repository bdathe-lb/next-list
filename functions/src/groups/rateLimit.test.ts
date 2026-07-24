import assert from "node:assert/strict";
import {afterEach, test} from "node:test";
import {
  assertInviteAttemptAllowed,
  recordFailedInviteAttempt,
  resetInviteAttemptsForTest,
} from "./rateLimit";

afterEach(resetInviteAttemptsForTest);

test("repeated invalid invite attempts are rate limited per user", () => {
  for (let index = 0; index < 8; index += 1) {
    assertInviteAttemptAllowed("alice", 1_000);
    recordFailedInviteAttempt("alice", 1_000);
  }
  assert.throws(() => assertInviteAttemptAllowed("alice", 1_000));
  assert.doesNotThrow(() => assertInviteAttemptAllowed("bob", 1_000));
  assert.doesNotThrow(() => assertInviteAttemptAllowed("alice", 301_001));
});
