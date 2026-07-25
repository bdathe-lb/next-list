import {initializeApp} from "firebase-admin/app";
import {randomUUID} from "node:crypto";
import {setGlobalOptions} from "firebase-functions/v2";
import {onCall} from "firebase-functions/v2/https";
import {
  onDocumentUpdated,
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import {onSchedule} from "firebase-functions/v2/scheduler";
import type {CallableRequest} from "firebase-functions/v2/https";
import {createHealthPayload} from "./shared/health";
import {deleteAccountHandler} from "./account/service";
import {
  acceptDirectInviteHandler,
  acceptInviteHandler,
  createGroupHandler,
  dissolveGroupHandler,
  getOrCreateInviteHandler,
  leaveGroupHandler,
  previewInviteHandler,
  removeMemberHandler,
  rotateInviteHandler,
  sendDirectInviteHandler,
  transferAdminHandler,
  updateGroupNameHandler,
} from "./groups/service";
import {syncMembershipProfileSnapshots} from "./groups/profileSync";
import {inviteHmacSecret} from "./groups/inviteCrypto";
import {
  cleanupDepartedMemberResponses,
  maintainCommentCount,
  maintainGroupIdeaCounts,
  maintainReactionCounts,
  maintainRsvpCounts,
} from "./ideas/aggregates";
import {
  processCommentActivity,
  processDirectInvitation,
  processIdeaActivity,
} from "./notifications/service";
import {sendUpcomingReminders} from "./notifications/reminders";
import {
  callableRequestId,
  observeExecution,
  safeRequestId,
} from "./shared/observability";

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
  () => observeExecution(
    "health",
    undefined,
    async () => createHealthPayload(),
  ),
);

const protectedCallable = {
  enforceAppCheck: process.env.FUNCTIONS_EMULATOR !== "true",
  secrets: [inviteHmacSecret],
};

type CallableHandler = (
  request: CallableRequest<unknown>,
) => Promise<unknown>;

function observedCallable(functionName: string, handler: CallableHandler) {
  return (request: CallableRequest<unknown>): Promise<unknown> =>
    observeExecution(
      functionName,
      callableRequestId(request.data) ?? randomUUID(),
      () => handler(request),
    );
}

function observedEvent<T extends {id: string}>(
  functionName: string,
  handler: (event: T) => Promise<void>,
): (event: T) => Promise<void> {
  return (event) => observeExecution(
    functionName,
    safeRequestId(event.id),
    () => handler(event),
  );
}

export const createGroup = onCall(
  protectedCallable,
  observedCallable("createGroup", createGroupHandler),
);
export const updateGroupName = onCall(
  protectedCallable,
  observedCallable("updateGroupName", updateGroupNameHandler),
);
export const getOrCreateInvite = onCall(
  protectedCallable,
  observedCallable("getOrCreateInvite", getOrCreateInviteHandler),
);
export const rotateInvite = onCall(
  protectedCallable,
  observedCallable("rotateInvite", rotateInviteHandler),
);
export const sendDirectInvite = onCall(
  protectedCallable,
  observedCallable("sendDirectInvite", sendDirectInviteHandler),
);
export const previewInvite = onCall(
  protectedCallable,
  observedCallable("previewInvite", previewInviteHandler),
);
export const acceptInvite = onCall(
  protectedCallable,
  observedCallable("acceptInvite", acceptInviteHandler),
);
export const acceptDirectInvite = onCall(
  protectedCallable,
  observedCallable("acceptDirectInvite", acceptDirectInviteHandler),
);
export const leaveGroup = onCall(
  protectedCallable,
  observedCallable("leaveGroup", leaveGroupHandler),
);
export const removeMember = onCall(
  protectedCallable,
  observedCallable("removeMember", removeMemberHandler),
);
export const transferAdmin = onCall(
  protectedCallable,
  observedCallable("transferAdmin", transferAdminHandler),
);
export const dissolveGroup = onCall(
  protectedCallable,
  observedCallable("dissolveGroup", dissolveGroupHandler),
);
export const deleteAccount = onCall(
  {
    ...protectedCallable,
    timeoutSeconds: 300,
  },
  observedCallable("deleteAccount", deleteAccountHandler),
);
export const syncMembershipProfiles = onDocumentUpdated(
  "users/{uid}",
  observedEvent("syncMembershipProfiles", syncMembershipProfileSnapshots),
);
export const updateGroupIdeaCounts = onDocumentWritten(
  "groups/{groupId}/ideas/{ideaId}",
  observedEvent("updateGroupIdeaCounts", maintainGroupIdeaCounts),
);
export const updateIdeaReactionCounts = onDocumentWritten(
  "groups/{groupId}/ideas/{ideaId}/reactions/{uid}",
  observedEvent("updateIdeaReactionCounts", maintainReactionCounts),
);
export const updateIdeaRsvpCounts = onDocumentWritten(
  "groups/{groupId}/ideas/{ideaId}/rsvps/{uid}",
  observedEvent("updateIdeaRsvpCounts", maintainRsvpCounts),
);
export const updateIdeaCommentCount = onDocumentWritten(
  "groups/{groupId}/ideas/{ideaId}/comments/{commentId}",
  observedEvent("updateIdeaCommentCount", maintainCommentCount),
);
export const cleanupMemberResponses = onDocumentUpdated(
  "groups/{groupId}/members/{uid}",
  observedEvent("cleanupMemberResponses", cleanupDepartedMemberResponses),
);
export const createIdeaActivity = onDocumentWritten(
  {
    document: "groups/{groupId}/ideas/{ideaId}",
    retry: true,
  },
  observedEvent("createIdeaActivity", processIdeaActivity),
);
export const createCommentActivity = onDocumentWritten(
  {
    document: "groups/{groupId}/ideas/{ideaId}/comments/{commentId}",
    retry: true,
  },
  observedEvent("createCommentActivity", processCommentActivity),
);
export const createDirectInvitationActivity = onDocumentWritten(
  {
    document: "users/{uid}/invitations/{invitationId}",
    retry: true,
  },
  observedEvent("createDirectInvitationActivity", processDirectInvitation),
);
export const upcomingActivityReminders = onSchedule(
  {
    schedule: "every 5 minutes",
    timeZone: "Asia/Shanghai",
    retryCount: 3,
  },
  (event) => observeExecution(
    "upcomingActivityReminders",
    safeRequestId(event.scheduleTime),
    () => sendUpcomingReminders(),
  ),
);
