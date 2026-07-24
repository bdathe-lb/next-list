import {createHash, createHmac} from "node:crypto";
import {defineSecret} from "firebase-functions/params";

const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
export const inviteHmacSecret = defineSecret("NEXTLIST_INVITE_SECRET");

export interface InviteCredentials {
  token: string;
  code: string;
  tokenHash: string;
  codeHash: string;
}

export function sha256(value: string): string {
  return createHash("sha256").update(value, "utf8").digest("hex");
}

export function deriveInviteCredentials(
  secret: string,
  inviteId: string,
  groupId: string,
  expiresAtMillis: number,
): InviteCredentials {
  const material = `${inviteId}:${groupId}:${expiresAtMillis}`;
  const token = createHmac("sha256", secret)
    .update(`token:${material}`, "utf8")
    .digest("base64url");
  const codeBytes = createHmac("sha256", secret)
    .update(`code:${material}`, "utf8")
    .digest();

  let code = "";
  for (let index = 0; index < 8; index += 1) {
    code += CODE_ALPHABET[codeBytes[index] % CODE_ALPHABET.length];
  }

  return {
    token,
    code,
    tokenHash: sha256(token),
    codeHash: sha256(code),
  };
}

export function inviteSecret(): {value: string; version: string} {
  const configured = process.env.NEXTLIST_INVITE_SECRET;
  if (configured && configured.length >= 32) {
    return {
      value: configured,
      version: process.env.NEXTLIST_INVITE_SECRET_VERSION ?? "configured-v1",
    };
  }

  const isEmulator =
    process.env.FUNCTIONS_EMULATOR === "true" ||
    (process.env.GCLOUD_PROJECT ?? "").startsWith("demo-");
  if (isEmulator) {
    return {
      value: "nextlist-emulator-only-secret-do-not-use-in-production",
      version: "emulator-v1",
    };
  }

  throw new Error("NEXTLIST_INVITE_SECRET is required outside the Emulator");
}
