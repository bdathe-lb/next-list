import {initializeApp} from "firebase-admin/app";
import {setGlobalOptions} from "firebase-functions/v2";
import {onCall} from "firebase-functions/v2/https";
import {
  onDocumentUpdated,
  onDocumentWritten,
} from "firebase-functions/v2/firestore";
import {onSchedule} from "firebase-functions/v2/scheduler";
import {createHealthPayload} from "./shared/health";
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

const protectedCallable = {
  enforceAppCheck: process.env.FUNCTIONS_EMULATOR !== "true",
  secrets: [inviteHmacSecret],
};

export const createGroup = onCall(protectedCallable, createGroupHandler);
export const updateGroupName = onCall(protectedCallable, updateGroupNameHandler);
export const getOrCreateInvite = onCall(
  protectedCallable,
  getOrCreateInviteHandler,
);
export const rotateInvite = onCall(protectedCallable, rotateInviteHandler);
export const sendDirectInvite = onCall(
  protectedCallable,
  sendDirectInviteHandler,
);
export const previewInvite = onCall(protectedCallable, previewInviteHandler);
export const acceptInvite = onCall(protectedCallable, acceptInviteHandler);
export const acceptDirectInvite = onCall(
  protectedCallable,
  acceptDirectInviteHandler,
);
export const leaveGroup = onCall(protectedCallable, leaveGroupHandler);
export const removeMember = onCall(protectedCallable, removeMemberHandler);
export const transferAdmin = onCall(protectedCallable, transferAdminHandler);
export const dissolveGroup = onCall(protectedCallable, dissolveGroupHandler);
export const syncMembershipProfiles = onDocumentUpdated(
  "users/{uid}",
  syncMembershipProfileSnapshots,
);
export const updateGroupIdeaCounts = onDocumentWritten(
  "groups/{groupId}/ideas/{ideaId}",
  maintainGroupIdeaCounts,
);
export const updateIdeaReactionCounts = onDocumentWritten(
  "groups/{groupId}/ideas/{ideaId}/reactions/{uid}",
  maintainReactionCounts,
);
export const updateIdeaRsvpCounts = onDocumentWritten(
  "groups/{groupId}/ideas/{ideaId}/rsvps/{uid}",
  maintainRsvpCounts,
);
export const updateIdeaCommentCount = onDocumentWritten(
  "groups/{groupId}/ideas/{ideaId}/comments/{commentId}",
  maintainCommentCount,
);
export const cleanupMemberResponses = onDocumentUpdated(
  "groups/{groupId}/members/{uid}",
  cleanupDepartedMemberResponses,
);
export const createIdeaActivity = onDocumentWritten(
  {
    document: "groups/{groupId}/ideas/{ideaId}",
    retry: true,
  },
  processIdeaActivity,
);
export const createCommentActivity = onDocumentWritten(
  {
    document: "groups/{groupId}/ideas/{ideaId}/comments/{commentId}",
    retry: true,
  },
  processCommentActivity,
);
export const createDirectInvitationActivity = onDocumentWritten(
  {
    document: "users/{uid}/invitations/{invitationId}",
    retry: true,
  },
  processDirectInvitation,
);
export const upcomingActivityReminders = onSchedule(
  {
    schedule: "every 5 minutes",
    timeZone: "Asia/Shanghai",
    retryCount: 3,
  },
  () => sendUpcomingReminders(),
);
