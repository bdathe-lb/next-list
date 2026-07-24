package com.example.nextlist.domain.model

import java.time.Instant

enum class ActivityFeedType(val wireValue: String) {
    IDEA_CREATED("idea_created"),
    SCHEDULE_CREATED("schedule_created"),
    SCHEDULE_UPDATED("schedule_updated"),
    IDEA_COMMENTED("idea_commented"),
    IDEA_COMPLETED("idea_completed"),
    GROUP_INVITED("group_invited"),
    ;

    companion object {
        fun fromWire(value: String?): ActivityFeedType? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class ActivityFeedItem(
    val id: String,
    val type: ActivityFeedType,
    val groupId: String,
    val groupName: String,
    val ideaId: String?,
    val ideaTitle: String?,
    val invitationId: String?,
    val actorId: String,
    val actor: MemberSnapshot,
    val createdAt: Instant,
    val readAt: Instant?,
)

data class ActivityFeedPage(
    val items: List<ActivityFeedItem>,
    val nextCursor: ActivityFeedCursor? = null,
    val isFromCache: Boolean = false,
)

data class ActivityFeedCursor(
    val createdAt: Instant,
    val documentId: String,
)

data class NotificationPreferences(
    val groupInvite: Boolean = true,
    val newSchedule: Boolean = true,
    val upcomingReminder: Boolean = true,
    val ideaComment: Boolean = true,
)

enum class NotificationPreferenceType {
    GROUP_INVITE,
    NEW_SCHEDULE,
    UPCOMING_REMINDER,
    IDEA_COMMENT,
}

fun NotificationPreferences.withValue(
    type: NotificationPreferenceType,
    enabled: Boolean,
): NotificationPreferences = when (type) {
    NotificationPreferenceType.GROUP_INVITE -> copy(groupInvite = enabled)
    NotificationPreferenceType.NEW_SCHEDULE -> copy(newSchedule = enabled)
    NotificationPreferenceType.UPCOMING_REMINDER -> copy(upcomingReminder = enabled)
    NotificationPreferenceType.IDEA_COMMENT -> copy(ideaComment = enabled)
}

data class NotificationPreferencesSnapshot(
    val preferences: NotificationPreferences,
    val isFromCache: Boolean,
    val hasPendingWrites: Boolean,
)

enum class NotificationType(val wireValue: String) {
    SCHEDULE_CREATED("schedule_created"),
    SCHEDULE_UPDATED("schedule_updated"),
    UPCOMING_REMINDER("upcoming_reminder"),
    IDEA_COMMENTED("idea_commented"),
    GROUP_INVITED("group_invited"),
    ;

    companion object {
        fun fromWire(value: String?): NotificationType? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class PendingNotificationTarget(
    val messageId: String,
    val type: NotificationType,
    val groupId: String,
    val ideaId: String?,
    val invitationId: String?,
)

sealed interface AppTarget {
    data class Group(val groupId: String) : AppTarget
    data class Idea(val groupId: String, val ideaId: String) : AppTarget
    data class Invitation(val invitationId: String) : AppTarget
}

fun ActivityFeedItem.toAppTarget(): AppTarget = when {
    type == ActivityFeedType.GROUP_INVITED && !invitationId.isNullOrBlank() ->
        AppTarget.Invitation(invitationId)
    !ideaId.isNullOrBlank() -> AppTarget.Idea(groupId, ideaId)
    else -> AppTarget.Group(groupId)
}

fun PendingNotificationTarget.toAppTarget(): AppTarget? = when (type) {
    NotificationType.GROUP_INVITED ->
        invitationId?.takeIf(String::isNotBlank)?.let(AppTarget::Invitation)
    else -> ideaId?.takeIf(String::isNotBlank)?.let { AppTarget.Idea(groupId, it) }
}
