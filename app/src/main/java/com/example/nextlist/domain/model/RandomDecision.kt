package com.example.nextlist.domain.model

import kotlin.random.Random

object RandomIdeaSelector {
    fun filter(
        ideas: List<Idea>,
        category: IdeaCategory?,
        minimumWant: Int,
    ): List<Idea> = ideas.filter { idea ->
        idea.status in setOf(IdeaStatus.IDEA, IdeaStatus.SCHEDULED) &&
            (category == null || idea.category == category) &&
            idea.reactionCounts.want >= minimumWant
    }

    fun choose(
        candidates: List<Idea>,
        excludingId: String? = null,
        random: Random = Random.Default,
    ): Idea? {
        val eligible = if (candidates.size > 1 && excludingId != null) {
            candidates.filterNot { it.id == excludingId }
        } else {
            candidates
        }
        if (eligible.isEmpty()) return null
        return eligible[random.nextInt(eligible.size)]
    }
}
