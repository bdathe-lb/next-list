import {HttpsError, type FunctionsErrorCode} from "firebase-functions/v2/https";

export type BusinessErrorCode =
  | "UNAUTHENTICATED"
  | "EMAIL_NOT_VERIFIED"
  | "VALIDATION"
  | "NOT_FOUND"
  | "NOT_ADMIN"
  | "PERMISSION_DENIED"
  | "GROUP_DISSOLVED"
  | "GROUP_FULL"
  | "INVITE_INVALID"
  | "INVITE_EXPIRED"
  | "ADMIN_CANNOT_LEAVE"
  | "TARGET_NOT_MEMBER"
  | "RATE_LIMITED";

const functionCodes: Record<BusinessErrorCode, FunctionsErrorCode> = {
  UNAUTHENTICATED: "unauthenticated",
  EMAIL_NOT_VERIFIED: "failed-precondition",
  VALIDATION: "invalid-argument",
  NOT_FOUND: "not-found",
  NOT_ADMIN: "permission-denied",
  PERMISSION_DENIED: "permission-denied",
  GROUP_DISSOLVED: "failed-precondition",
  GROUP_FULL: "resource-exhausted",
  INVITE_INVALID: "not-found",
  INVITE_EXPIRED: "failed-precondition",
  ADMIN_CANNOT_LEAVE: "failed-precondition",
  TARGET_NOT_MEMBER: "failed-precondition",
  RATE_LIMITED: "resource-exhausted",
};

export function businessError(code: BusinessErrorCode): HttpsError {
  return new HttpsError(functionCodes[code], code, {code});
}

export function isBusinessError(error: unknown): error is HttpsError {
  return error instanceof HttpsError;
}
