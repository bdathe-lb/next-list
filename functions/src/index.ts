import {initializeApp} from "firebase-admin/app";
import {setGlobalOptions} from "firebase-functions/v2";
import {onCall} from "firebase-functions/v2/https";
import {createHealthPayload} from "./shared/health";

initializeApp();

setGlobalOptions({
  maxInstances: 10,
  region: "asia-east1",
  timeoutSeconds: 60,
});

export const health = onCall(
  {
    enforceAppCheck: false,
  },
  () => createHealthPayload(),
);
