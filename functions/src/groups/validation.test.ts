import assert from "node:assert/strict";
import {test} from "node:test";
import {
  normalizeGroupName,
  normalizeInviteCode,
  unicodeLength,
} from "./validation";

test("group names are trimmed and count Unicode code points", () => {
  assert.equal(normalizeGroupName("  周末去哪  "), "周末去哪");
  assert.equal(unicodeLength("𠮷野"), 2);
  assert.throws(() => normalizeGroupName("组"));
  assert.throws(() => normalizeGroupName("一".repeat(31)));
});

test("invite codes normalize separators and reject ambiguous characters", () => {
  assert.equal(normalizeInviteCode("ab-cd ef23"), "ABCDEF23");
  assert.throws(() => normalizeInviteCode("ABCD0F23"));
  assert.throws(() => normalizeInviteCode("ABCDIF23"));
});
