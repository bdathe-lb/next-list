import assert from "node:assert/strict";
import {test} from "node:test";
import {createHealthPayload} from "./health";

test("createHealthPayload returns the stable emulator contract", () => {
  assert.deepEqual(createHealthPayload(), {
    service: "nextlist-functions",
    status: "ok",
  });
});
