import {businessError} from "./errors";

interface AttemptWindow {
  count: number;
  startedAt: number;
}

const attempts = new Map<string, AttemptWindow>();
const WINDOW_MILLIS = 5 * 60 * 1000;
const MAX_FAILED_ATTEMPTS = 8;

export function assertInviteAttemptAllowed(uid: string, now = Date.now()): void {
  const current = attempts.get(uid);
  if (!current || now - current.startedAt >= WINDOW_MILLIS) {
    return;
  }
  if (current.count >= MAX_FAILED_ATTEMPTS) {
    throw businessError("RATE_LIMITED");
  }
}

export function recordFailedInviteAttempt(uid: string, now = Date.now()): void {
  const current = attempts.get(uid);
  if (!current || now - current.startedAt >= WINDOW_MILLIS) {
    attempts.set(uid, {count: 1, startedAt: now});
    return;
  }
  current.count += 1;
}

export function clearInviteAttempts(uid: string): void {
  attempts.delete(uid);
}

export function resetInviteAttemptsForTest(): void {
  attempts.clear();
}
