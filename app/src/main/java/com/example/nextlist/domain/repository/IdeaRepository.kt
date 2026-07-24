package com.example.nextlist.domain.repository

import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.Idea
import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaComment
import com.example.nextlist.domain.model.IdeaDraft
import com.example.nextlist.domain.model.IdeaReaction
import com.example.nextlist.domain.model.IdeaRsvp
import com.example.nextlist.domain.model.IdeaStatus
import com.example.nextlist.domain.model.CompletionDraft
import com.example.nextlist.domain.model.ReactionValue
import com.example.nextlist.domain.model.RealtimeItems
import com.example.nextlist.domain.model.RsvpValue
import com.example.nextlist.domain.model.ScheduleDraft
import kotlinx.coroutines.flow.Flow

interface IdeaRepository {
    fun newIdeaId(groupId: String): String

    fun observeIdeas(
        groupId: String,
        status: IdeaStatus,
        category: IdeaCategory?,
    ): Flow<AppResult<RealtimeItems<Idea>>>

    fun observeIdea(groupId: String, ideaId: String): Flow<AppResult<Idea>>

    fun observeReactions(
        groupId: String,
        ideaId: String,
    ): Flow<AppResult<RealtimeItems<IdeaReaction>>>

    fun observeComments(
        groupId: String,
        ideaId: String,
    ): Flow<AppResult<RealtimeItems<IdeaComment>>>

    fun observeRsvps(
        groupId: String,
        ideaId: String,
    ): Flow<AppResult<RealtimeItems<IdeaRsvp>>>

    suspend fun createIdea(
        groupId: String,
        ideaId: String,
        draft: IdeaDraft,
        requireServerAck: Boolean = false,
    ): AppResult<Unit>

    suspend fun updateIdea(
        groupId: String,
        ideaId: String,
        draft: IdeaDraft,
        requireServerAck: Boolean = false,
    ): AppResult<Unit>

    suspend fun deleteIdea(groupId: String, ideaId: String): AppResult<Unit>

    suspend fun setReaction(
        groupId: String,
        ideaId: String,
        value: ReactionValue,
        isExisting: Boolean,
    ): AppResult<Unit>

    suspend fun clearReaction(groupId: String, ideaId: String): AppResult<Unit>

    suspend fun addComment(
        groupId: String,
        ideaId: String,
        content: String,
    ): AppResult<Unit>

    suspend fun deleteComment(
        groupId: String,
        ideaId: String,
        commentId: String,
    ): AppResult<Unit>

    suspend fun saveSchedule(
        groupId: String,
        ideaId: String,
        draft: ScheduleDraft,
    ): AppResult<Unit>

    suspend fun setRsvp(
        groupId: String,
        ideaId: String,
        value: RsvpValue,
        scheduleRevision: Int,
        isExisting: Boolean,
    ): AppResult<Unit>

    suspend fun clearRsvp(groupId: String, ideaId: String): AppResult<Unit>

    suspend fun saveCompletion(
        groupId: String,
        ideaId: String,
        draft: CompletionDraft,
    ): AppResult<Unit>

    suspend fun getIdeaFromServer(groupId: String, ideaId: String): AppResult<Idea>

    suspend fun loadRandomCandidates(
        groupId: String,
        category: IdeaCategory?,
        minimumWant: Int,
    ): AppResult<List<Idea>>
}
