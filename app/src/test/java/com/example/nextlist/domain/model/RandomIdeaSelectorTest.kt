package com.example.nextlist.domain.model

import java.time.Instant
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomIdeaSelectorTest {
    @Test
    fun `filter includes idea and scheduled but excludes completed and uses want only`() {
        val ideas = listOf(
            idea("idea", IdeaStatus.IDEA, IdeaCategory.MOVIE, want = 2, ok = 0),
            idea("scheduled", IdeaStatus.SCHEDULED, IdeaCategory.MOVIE, want = 2, ok = 0),
            idea("ok-only", IdeaStatus.IDEA, IdeaCategory.MOVIE, want = 0, ok = 5),
            idea("completed", IdeaStatus.COMPLETED, IdeaCategory.MOVIE, want = 9, ok = 0),
            idea("place", IdeaStatus.IDEA, IdeaCategory.PLACE, want = 4, ok = 0),
        )

        val filtered = RandomIdeaSelector.filter(ideas, IdeaCategory.MOVIE, 2)

        assertEquals(listOf("idea", "scheduled"), filtered.map(Idea::id))
    }

    @Test
    fun `draw another excludes current result when alternatives exist`() {
        val candidates = listOf(
            idea("one", IdeaStatus.IDEA, IdeaCategory.OTHER, 0, 0),
            idea("two", IdeaStatus.IDEA, IdeaCategory.OTHER, 0, 0),
        )

        repeat(20) {
            assertEquals(
                "two",
                RandomIdeaSelector.choose(candidates, "one", Random(it))?.id,
            )
        }
    }

    @Test
    fun `selection covers every candidate with an approximately uniform seeded sample`() {
        val candidates = (1..4).map {
            idea("$it", IdeaStatus.IDEA, IdeaCategory.OTHER, 0, 0)
        }
        val random = Random(42)
        val counts = mutableMapOf<String, Int>()
        repeat(8_000) {
            val selected = requireNotNull(RandomIdeaSelector.choose(candidates, random = random))
            counts[selected.id] = counts.getOrDefault(selected.id, 0) + 1
        }

        assertEquals(4, counts.size)
        assertTrue(counts.values.all { it in 1_800..2_200 })
    }

    @Test
    fun `empty candidates return no result and a single candidate can repeat`() {
        assertNull(RandomIdeaSelector.choose(emptyList()))
        val only = idea("one", IdeaStatus.IDEA, IdeaCategory.OTHER, 0, 0)
        assertEquals(only, RandomIdeaSelector.choose(listOf(only), excludingId = only.id))
        assertFalse(RandomIdeaSelector.filter(emptyList(), null, 0).isNotEmpty())
    }

    private fun idea(
        id: String,
        status: IdeaStatus,
        category: IdeaCategory,
        want: Int,
        ok: Int,
    ) = Idea(
        id = id,
        groupId = "group-1",
        title = id,
        category = category,
        note = null,
        media = null,
        locationOrLink = null,
        createdBy = "alice",
        creatorSnapshot = MemberSnapshot("小林", null),
        status = status,
        reactionCounts = ReactionCounts(want = want, ok = ok),
        commentCount = 0,
        lastModifiedBy = "alice",
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
