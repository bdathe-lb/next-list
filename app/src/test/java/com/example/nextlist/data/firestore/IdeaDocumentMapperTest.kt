package com.example.nextlist.data.firestore

import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaStatus
import com.google.firebase.Timestamp
import java.util.Date
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaDocumentMapperTest {
    @Test
    fun `maps valid idea DTO and clamps negative aggregates`() {
        val mapped = IdeaDocumentMapper.toIdea(
            id = "idea-1",
            data = validIdeaData(
                reactionCounts = mapOf(
                    "want" to 2L,
                    "ok" to -3L,
                    "notInterested" to 1L,
                ),
                commentCount = -1L,
            ),
            hasPendingWrites = true,
        )

        requireNotNull(mapped)
        assertEquals(IdeaCategory.KTV_SONG, mapped.category)
        assertEquals(IdeaStatus.IDEA, mapped.status)
        assertEquals(2, mapped.reactionCounts.want)
        assertEquals(0, mapped.reactionCounts.ok)
        assertEquals(0, mapped.commentCount)
        assertTrue(mapped.hasPendingWrites)
        assertEquals("小周", mapped.creatorSnapshot.nickname)
        assertEquals("groups/group-1/ideas/idea-1/cover/file.webp", mapped.media?.storagePath)
    }

    @Test
    fun `rejects unknown category and status values`() {
        assertNull(
            IdeaDocumentMapper.toIdea(
                "idea-1",
                validIdeaData(category = "unknown"),
            ),
        )
        assertNull(
            IdeaDocumentMapper.toIdea(
                "idea-1",
                validIdeaData(status = "draft"),
            ),
        )
    }

    @Test
    fun `category and status wire mappings are stable`() {
        assertEquals(IdeaCategory.RESTAURANT, IdeaCategory.fromWire("restaurant"))
        assertEquals(IdeaStatus.SCHEDULED, IdeaStatus.fromWire("scheduled"))
        assertNull(IdeaCategory.fromWire("food"))
        assertNull(IdeaStatus.fromWire("deleted"))
    }

    @Test
    fun `maps schedule completion RSVP aggregates and audit snapshots`() {
        val schedule = mapOf(
            "startAt" to Timestamp(Date(3_000)),
            "timezone" to "Asia/Shanghai",
            "meetingPoint" to "地铁站",
            "note" to null,
            "scheduledBy" to "alice",
            "schedulerSnapshot" to mapOf("nickname" to "小林", "avatarPath" to null),
            "scheduledAt" to Timestamp(Date(2_000)),
            "updatedBy" to "bob",
            "updatedAt" to Timestamp(Date(2_500)),
            "revision" to 3L,
        )
        val completion = mapOf(
            "completedOn" to "2026-07-24",
            "timezone" to "Asia/Shanghai",
            "photo" to mapOf(
                "storagePath" to
                    "groups/group-1/ideas/idea-1/completion/completed.webp",
                "mimeType" to "image/webp",
                "width" to 800L,
                "height" to 600L,
                "byteSize" to 2_000L,
            ),
            "review" to "很好",
            "rating" to 5L,
            "completedBy" to "bob",
            "completerSnapshot" to mapOf("nickname" to "小周", "avatarPath" to null),
            "completedAt" to Timestamp(Date(4_000)),
            "updatedBy" to "alice",
            "updatedAt" to Timestamp(Date(5_000)),
        )
        val mapped = IdeaDocumentMapper.toIdea(
            "idea-1",
            validIdeaData(
                status = "completed",
                schedule = schedule,
                completion = completion,
                rsvpCounts = mapOf("going" to 2L, "maybe" to 1L, "notGoing" to -1L),
            ),
        )

        requireNotNull(mapped)
        assertEquals(3, mapped.schedule?.revision)
        assertEquals("地铁站", mapped.schedule?.meetingPoint)
        assertEquals(LocalDate.of(2026, 7, 24), mapped.completion?.completedOn)
        assertEquals(5, mapped.completion?.rating)
        assertEquals(2, mapped.rsvpCounts.going)
        assertEquals(0, mapped.rsvpCounts.notGoing)
        assertEquals(
            "groups/group-1/ideas/idea-1/completion/completed.webp",
            mapped.completion?.photo?.storagePath,
        )
    }

    private fun validIdeaData(
        category: String = "ktv_song",
        status: String = "idea",
        reactionCounts: Map<String, Any> = mapOf(
            "want" to 0L,
            "ok" to 0L,
            "notInterested" to 0L,
        ),
        schedule: Map<String, Any?>? = null,
        completion: Map<String, Any?>? = null,
        rsvpCounts: Map<String, Any> = mapOf(
            "going" to 0L,
            "maybe" to 0L,
            "notGoing" to 0L,
        ),
        commentCount: Long = 0,
    ): Map<String, Any?> = mapOf(
        "groupId" to "group-1",
        "title" to "唱首歌",
        "category" to category,
        "note" to null,
        "media" to mapOf(
            "storagePath" to "groups/group-1/ideas/idea-1/cover/file.webp",
            "mimeType" to "image/webp",
            "width" to 1200L,
            "height" to 800L,
            "byteSize" to 1234L,
        ),
        "locationOrLink" to null,
        "createdBy" to "bob",
        "creatorSnapshot" to mapOf("nickname" to "小周", "avatarPath" to null),
        "status" to status,
        "schedule" to schedule,
        "completion" to completion,
        "reactionCounts" to reactionCounts,
        "rsvpCounts" to rsvpCounts,
        "commentCount" to commentCount,
        "lastModifiedBy" to "bob",
        "createdAt" to Timestamp(Date(1_000)),
        "updatedAt" to Timestamp(Date(2_000)),
    )
}
