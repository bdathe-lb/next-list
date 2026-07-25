import assert from "node:assert/strict";
import {test} from "node:test";
import {accountDeletionTestHelpers} from "./service";

test("account deletion accepts only the current user's WebP avatar path", () => {
  const {safeAvatarPath} = accountDeletionTestHelpers;
  assert.equal(
    safeAvatarPath("alice", "users/alice/avatar/current_1.webp"),
    "users/alice/avatar/current_1.webp",
  );
  assert.equal(
    safeAvatarPath("alice", "users/bob/avatar/current.webp"),
    null,
  );
  assert.equal(
    safeAvatarPath("alice", "users/alice/avatar/../../secret.webp"),
    null,
  );
  assert.equal(
    safeAvatarPath("alice", "users/alice/avatar/current.jpg"),
    null,
  );
});
