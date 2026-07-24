import assert from "node:assert/strict";
import {test} from "node:test";
import {deriveInviteCredentials, sha256} from "./inviteCrypto";

test("token and code use independent deterministic HMAC outputs", () => {
  const first = deriveInviteCredentials(
    "test-secret-that-is-at-least-thirty-two-characters",
    "invite-1",
    "group-1",
    123456789,
  );
  const second = deriveInviteCredentials(
    "test-secret-that-is-at-least-thirty-two-characters",
    "invite-1",
    "group-1",
    123456789,
  );

  assert.deepEqual(first, second);
  assert.match(first.token, /^[A-Za-z0-9_-]{40,}$/);
  assert.match(first.code, /^[A-HJ-NP-Z2-9]{8}$/);
  assert.equal(first.tokenHash, sha256(first.token));
  assert.equal(first.codeHash, sha256(first.code));
  assert.notEqual(first.tokenHash, first.codeHash);
});

test("different invite material produces different credentials", () => {
  const secret = "test-secret-that-is-at-least-thirty-two-characters";
  const first = deriveInviteCredentials(secret, "invite-1", "group-1", 1);
  const second = deriveInviteCredentials(secret, "invite-2", "group-1", 1);
  assert.notEqual(first.token, second.token);
  assert.notEqual(first.code, second.code);
});
