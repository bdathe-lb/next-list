import * as logger from "firebase-functions/logger";
import {isBusinessError} from "../groups/errors";

const REQUEST_ID_PATTERN = /^[A-Za-z0-9._:-]{1,160}$/;

export function safeRequestId(value: unknown): string | undefined {
  return typeof value === "string" && REQUEST_ID_PATTERN.test(value) ?
    value :
    undefined;
}

export function callableRequestId(data: unknown): string | undefined {
  if (!data || typeof data !== "object" || Array.isArray(data)) return undefined;
  return safeRequestId((data as Record<string, unknown>).requestId);
}

export async function observeExecution<T>(
  functionName: string,
  requestId: string | undefined,
  operation: () => Promise<T>,
): Promise<T> {
  const startedAt = Date.now();
  try {
    const result = await operation();
    logger.info("function_completed", {
      function: functionName,
      result: "success",
      duration: Date.now() - startedAt,
      requestId,
    });
    return result;
  } catch (error) {
    const fields = {
      function: functionName,
      result: isBusinessError(error) ? "business_error" : "failure",
      duration: Date.now() - startedAt,
      errorKind: safeErrorKind(error),
      requestId,
    };
    if (isBusinessError(error)) {
      logger.warn("function_rejected", fields);
    } else {
      logger.error("function_failed", fields);
    }
    throw error;
  }
}

function safeErrorKind(error: unknown): string {
  if (isBusinessError(error)) {
    const code = (error.details as {code?: unknown} | undefined)?.code;
    return typeof code === "string" ? code : "BUSINESS_ERROR";
  }
  if (error instanceof Error && /^[A-Za-z][A-Za-z0-9]*$/.test(error.name)) {
    return error.name;
  }
  return "UNKNOWN";
}
