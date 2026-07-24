package com.example.nextlist.data.firestore

import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaStatus
import com.google.firebase.Timestamp
import java.util.Date
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

    private fun validIdeaData(
        category: String = "ktv_song",
        status: String = "idea",
        reactionCounts: Map<String, Any> = mapOf(
            "want" to 0L,
            "ok" to 0L,
            "notInterested" to 0L,
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
        "reactionCounts" to reactionCounts,
        "commentCount" to commentCount,
        "lastModifiedBy" to "bob",
        "createdAt" to Timestamp(Date(1_000)),
        "updatedAt" to Timestamp(Date(2_000)),
    )
}
