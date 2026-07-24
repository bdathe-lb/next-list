package com.example.nextlist.data.firestore

import com.example.nextlist.domain.model.GroupRole
import com.example.nextlist.domain.model.GroupStatus
import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupDocumentMapperTest {
    @Test
    fun `maps group document fields to the domain model`() {
        val group = GroupDocumentMapper.toGroup(
            id = "group-1",
            data = mapOf(
                "name" to "周末去哪",
                "adminId" to "alice",
                "status" to "active",
                "memberCount" to 3L,
                "ideaCount" to 4L,
                "scheduledCount" to 2L,
                "completedCount" to 1L,
                "lastActivityAt" to Timestamp(Date(1_000)),
            ),
        )

        requireNotNull(group)
        assertEquals("group-1", group.id)
        assertEquals(GroupStatus.ACTIVE, group.status)
        assertEquals(3, group.memberCount)
        assertEquals(1_000L, group.lastActivityAt?.toEpochMilli())
    }

    @Test
    fun `rejects malformed enum fields`() {
        assertNull(
            GroupDocumentMapper.toGroup(
                "group-1",
                mapOf(
                    "name" to "周末去哪",
                    "adminId" to "alice",
                    "status" to "unknown",
                ),
            ),
        )
    }

    @Test
    fun `maps member snapshot and role`() {
        val member = GroupDocumentMapper.toMember(
            "bob",
            mapOf(
                "role" to "member",
                "profileSnapshot" to mapOf(
                    "nickname" to "小周",
                    "avatarPath" to "users/bob/avatar/a.webp",
                ),
            ),
        )
        requireNotNull(member)
        assertEquals(GroupRole.MEMBER, member.role)
        assertEquals("小周", member.nickname)
    }
}
