import {businessError} from "./errors";

export const GROUP_NAME_MIN = 2;
export const GROUP_NAME_MAX = 30;
export const INVITE_CODE_LENGTH = 8;
export const MAX_GROUP_MEMBERS = 10;

export function unicodeLength(value: string): number {
  return Array.from(value).length;
}

export function normalizeGroupName(value: unknown): string {
  if (typeof value !== "string") {
    throw businessError("VALIDATION");
  }
  const normalized = value.trim();
  const length = unicodeLength(normalized);
  if (length < GROUP_NAME_MIN || length > GROUP_NAME_MAX) {
    throw businessError("VALIDATION");
  }
  return normalized;
}

export function normalizeInviteCode(value: unknown): string {
  if (typeof value !== "string") {
    throw businessError("VALIDATION");
  }
  const normalized = value.replace(/[\s-]/g, "").toUpperCase();
  if (!new RegExp(`^[A-HJ-NP-Z2-9]{${INVITE_CODE_LENGTH}}$`).test(normalized)) {
    throw businessError("INVITE_INVALID");
  }
  return normalized;
}

export function requireString(
  value: unknown,
  minLength = 1,
  maxLength = 256,
): string {
  if (typeof value !== "string") {
    throw businessError("VALIDATION");
  }
  const normalized = value.trim();
  const length = unicodeLength(normalized);
  if (length < minLength || length > maxLength) {
    throw businessError("VALIDATION");
  }
  return normalized;
}

export function requireRequestId(value: unknown): string {
  const requestId = requireString(value, 8, 80);
  if (!/^[A-Za-z0-9_-]+$/.test(requestId)) {
    throw businessError("VALIDATION");
  }
  return requestId;
}

export function normalizeEmail(value: unknown): string {
  const email = requireString(value, 3, 254).toLowerCase();
  if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) {
    throw businessError("VALIDATION");
  }
  return email;
}

export function requireInviteCredential(
  data: Record<string, unknown>,
): {kind: "token" | "code" | "direct"; value: string} {
  if (data.kind === "token") {
    return {kind: "token", value: requireString(data.value, 20, 512)};
  }
  if (data.kind === "code") {
    return {kind: "code", value: normalizeInviteCode(data.value)};
  }
  if (data.kind === "direct") {
    return {kind: "direct", value: requireString(data.value, 1, 128)};
  }
  throw businessError("VALIDATION");
}
