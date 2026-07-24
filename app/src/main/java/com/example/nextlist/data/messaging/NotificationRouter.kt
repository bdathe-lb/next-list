package com.example.nextlist.data.messaging

import com.example.nextlist.domain.model.NotificationType
import com.example.nextlist.domain.model.PendingNotificationTarget

object NotificationRouter {
    private val identifier = Regex("^[A-Za-z0-9_-]{1,128}$")

    fun parse(data: Map<String, String>, messageId: String?): PendingNotificationTarget? {
        val type = NotificationType.fromWire(data["type"]) ?: return null
        val groupId = data["groupId"]?.takeIf { identifier.matches(it) } ?: return null
        val safeMessageId = messageId
            ?.takeIf { it.length in 1..256 }
            ?: return null
        val ideaId = data["ideaId"]?.takeIf { identifier.matches(it) }
        val invitationId = data["invitationId"]?.takeIf { identifier.matches(it) }
        val targetIsValid = when (type) {
            NotificationType.GROUP_INVITED -> invitationId != null
            else -> ideaId != null
        }
        if (!targetIsValid) return null
        return PendingNotificationTarget(
            messageId = safeMessageId,
            type = type,
            groupId = groupId,
            ideaId = ideaId,
            invitationId = invitationId,
        )
    }
}
