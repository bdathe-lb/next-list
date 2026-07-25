import assert from "node:assert/strict";
import {test} from "node:test";
import {businessError} from "../groups/errors";
import {
  callableRequestId,
  observeExecution,
  safeRequestId,
} from "./observability";

test("request identifiers accept only bounded structured values", () => {
  assert.equal(safeRequestId("request_1234"), "request_1234");
  assert.equal(safeRequestId("含用户正文"), undefined);
  assert.equal(safeRequestId("a".repeat(161)), undefined);
  assert.equal(callableRequestId({requestId: "request-1234"}), "request-1234");
  assert.equal(callableRequestId({requestId: "token/value"}), undefined);
});

test("observability preserves success and business errors", async () => {
  assert.equal(
    await observeExecution("testFunction", "request-1", async () => "ok"),
    "ok",
  );
  await assert.rejects(
    observeExecution("testFunction", "request-2", async () => {
      throw businessError("VALIDATION");
    }),
    (error: unknown) => (
      (error as {details?: {code?: string}}).details?.code === "VALIDATION"
    ),
  );
});
