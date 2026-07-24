package com.example.nextlist.data.messaging

import com.example.nextlist.domain.model.NotificationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationRouterTest {
    @Test
    fun `valid schedule and invitation payloads map only trusted ids`() {
        NotificationType.entries
            .filterNot { it == NotificationType.GROUP_INVITED }
            .forEachIndexed { index, type ->
                val schedule = NotificationRouter.parse(
                    mapOf(
                        "type" to type.wireValue,
                        "groupId" to "group-1",
                        "ideaId" to "idea-1",
                        "title" to "不可信标题",
                    ),
                    "message-$index",
                )
                assertEquals(type, schedule?.type)
                assertEquals("idea-1", schedule?.ideaId)
            }

        val invite = NotificationRouter.parse(
            mapOf(
                "type" to "group_invited",
                "groupId" to "group-1",
                "invitationId" to "direct-1",
            ),
            "message-2",
        )
        assertEquals("direct-1", invite?.invitationId)
    }

    @Test
    fun `unknown malformed and incomplete payloads are ignored`() {
        assertNull(
            NotificationRouter.parse(
                mapOf("type" to "unknown", "groupId" to "group-1"),
                "message",
            ),
        )
        assertNull(
            NotificationRouter.parse(
                mapOf("type" to "schedule_updated", "groupId" to "../group"),
                "message",
            ),
        )
        assertNull(
            NotificationRouter.parse(
                mapOf("type" to "idea_commented", "groupId" to "group-1"),
                "message",
            ),
        )
        assertNull(
            NotificationRouter.parse(
                mapOf(
                    "type" to "group_invited",
                    "groupId" to "group-1",
                    "invitationId" to "direct-1",
                ),
                null,
            ),
        )
    }
}
