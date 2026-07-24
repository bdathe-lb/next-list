package com.example.nextlist.feature.ideas

import androidx.lifecycle.SavedStateHandle
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.domain.model.Group
import com.example.nextlist.domain.model.GroupMember
import com.example.nextlist.domain.model.GroupRole
import com.example.nextlist.domain.model.GroupStatus
import com.example.nextlist.domain.model.GroupSummary
import com.example.nextlist.domain.model.Idea
import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaComment
import com.example.nextlist.domain.model.IdeaDraft
import com.example.nextlist.domain.model.IdeaMedia
import com.example.nextlist.domain.model.IdeaReaction
import com.example.nextlist.domain.model.IdeaStatus
import com.example.nextlist.domain.model.InviteCredential
import com.example.nextlist.domain.model.InviteCredentials
import com.example.nextlist.domain.model.InvitePreview
import com.example.nextlist.domain.model.MemberSnapshot
import com.example.nextlist.domain.model.ReactionCounts
import com.example.nextlist.domain.model.ReactionValue
import com.example.nextlist.domain.model.RealtimeItems
import com.example.nextlist.domain.repository.AvatarRepository
import com.example.nextlist.domain.repository.GroupRepository
import com.example.nextlist.domain.repository.IdeaImageRepository
import com.example.nextlist.domain.repository.IdeaRepository
import com.example.nextlist.feature.groups.GroupDetailViewModel
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IdeaViewModelsTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `group detail maps idea empty content error and retry states`() = runTest(dispatcher) {
        val ideas = FakeIdeaRepository()
        val viewModel = GroupDetailViewModel(
            SavedStateHandle(mapOf("groupId" to "group-1")),
            FakeGroupRepository(),
            ideas,
            FakeAvatarRepository(),
        )
        runCurrent()
        assertTrue(viewModel.uiState.value.ideas is LoadState.Empty)

        ideas.ideaList.value = AppResult.Success(
            RealtimeItems(listOf(sampleIdea()), hasPendingWrites = true, isFromCache = true),
        )
        runCurrent()
        val content = viewModel.uiState.value.ideas as LoadState.Content
        assertEquals(1, content.data.size)
        assertTrue(content.hasPendingWrites)
        assertTrue(viewModel.uiState.value.isFromCache)

        ideas.ideaList.value = AppResult.Failure(AppError.NETWORK_UNAVAILABLE)
        runCurrent()
        assertEquals(
            AppError.NETWORK_UNAVAILABLE,
            (viewModel.uiState.value.ideas as LoadState.Error).kind,
        )
        val callsBeforeRetry = ideas.observeIdeasCalls
        viewModel.refresh()
        runCurrent()
        assertTrue(ideas.observeIdeasCalls > callsBeforeRetry)
    }

    @Test
    fun `category filtering applies only to idea tab`() = runTest(dispatcher) {
        val ideas = FakeIdeaRepository()
        val viewModel = GroupDetailViewModel(
            SavedStateHandle(mapOf("groupId" to "group-1")),
            FakeGroupRepository(),
            ideas,
            FakeAvatarRepository(),
        )
        runCurrent()
        viewModel.selectCategory(IdeaCategory.MOVIE)
        runCurrent()
        assertEquals(IdeaCategory.MOVIE, ideas.lastCategory)
        viewModel.selectStatus(IdeaStatus.SCHEDULED)
        runCurrent()
        assertEquals(IdeaStatus.SCHEDULED, ideas.lastStatus)
        assertEquals(null, ideas.lastCategory)
    }

    @Test
    fun `new idea form validates and prevents duplicate creation`() = runTest(dispatcher) {
        val ideas = FakeIdeaRepository()
        ideas.createResult = CompletableDeferred()
        val viewModel = IdeaFormViewModel(
            SavedStateHandle(mapOf("groupId" to "group-1")),
            ideas,
            FakeIdeaImageRepository(),
        )
        viewModel.submit()
        assertNotNull(viewModel.uiState.value.titleError)
        viewModel.onTitleChanged("去植物园")
        viewModel.submit()
        viewModel.submit()
        runCurrent()
        assertEquals(1, ideas.createCalls)
        assertTrue(viewModel.uiState.value.isSubmitting)
        ideas.createResult?.complete(AppResult.Success(Unit))
        advanceUntilIdle()
        assertEquals("idea-new", viewModel.uiState.value.savedIdeaId)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `edit form loads existing data and prevents duplicate update`() = runTest(dispatcher) {
        val ideas = FakeIdeaRepository()
        ideas.idea.value = AppResult.Success(sampleIdea())
        ideas.updateResult = CompletableDeferred()
        val viewModel = IdeaFormViewModel(
            SavedStateHandle(mapOf("groupId" to "group-1", "ideaId" to "idea-1")),
            ideas,
            FakeIdeaImageRepository(),
        )
        runCurrent()
        assertEquals("去植物园", viewModel.uiState.value.title)
        viewModel.onTitleChanged("去植物园看荷花")
        assertTrue(viewModel.uiState.value.isDirty)
        viewModel.submit()
        viewModel.submit()
        runCurrent()
        assertEquals(1, ideas.updateCalls)
        ideas.updateResult?.complete(AppResult.Success(Unit))
        advanceUntilIdle()
        assertEquals("idea-1", viewModel.uiState.value.savedIdeaId)
    }

    @Test
    fun `detail permissions distinguish creator admin and ordinary member`() = runTest(dispatcher) {
        val creatorRepository = FakeGroupRepository(currentUid = "bob")
        val creatorIdeas = FakeIdeaRepository()
        creatorIdeas.idea.value = AppResult.Success(sampleIdea(createdBy = "bob"))
        val creator = IdeaDetailViewModel(
            SavedStateHandle(mapOf("groupId" to "group-1", "ideaId" to "idea-1")),
            creatorRepository,
            creatorIdeas,
            FakeIdeaImageRepository(),
            FakeAvatarRepository(),
        )
        runCurrent()
        assertTrue(creator.canEditIdea())
        assertTrue(creator.canDeleteIdea())

        val adminRepository = FakeGroupRepository(currentUid = "alice")
        val adminIdeas = FakeIdeaRepository()
        adminIdeas.idea.value = AppResult.Success(sampleIdea(createdBy = "bob"))
        val admin = IdeaDetailViewModel(
            SavedStateHandle(mapOf("groupId" to "group-1", "ideaId" to "idea-1")),
            adminRepository,
            adminIdeas,
            FakeIdeaImageRepository(),
            FakeAvatarRepository(),
        )
        runCurrent()
        assertFalse(admin.canEditIdea())
        assertTrue(admin.canDeleteIdea())

        val memberRepository = FakeGroupRepository(currentUid = "charlie")
        val memberIdeas = FakeIdeaRepository()
        memberIdeas.idea.value = AppResult.Success(sampleIdea(createdBy = "bob"))
        val member = IdeaDetailViewModel(
            SavedStateHandle(mapOf("groupId" to "group-1", "ideaId" to "idea-1")),
            memberRepository,
            memberIdeas,
            FakeIdeaImageRepository(),
            FakeAvatarRepository(),
        )
        runCurrent()
        assertFalse(member.canEditIdea())
        assertFalse(member.canDeleteIdea())
    }

    @Test
    fun `reaction and comment submissions prevent duplicates and retain safe errors`() =
        runTest(dispatcher) {
            val ideas = FakeIdeaRepository()
            ideas.idea.value = AppResult.Success(sampleIdea())
            ideas.reactionResult = CompletableDeferred()
            ideas.commentResult = CompletableDeferred()
            val viewModel = IdeaDetailViewModel(
                SavedStateHandle(mapOf("groupId" to "group-1", "ideaId" to "idea-1")),
                FakeGroupRepository(currentUid = "bob"),
                ideas,
                FakeIdeaImageRepository(),
                FakeAvatarRepository(),
            )
            runCurrent()

            viewModel.setReaction(ReactionValue.WANT)
            viewModel.setReaction(ReactionValue.OK)
            runCurrent()
            assertEquals(1, ideas.reactionCalls)
            ideas.reactionResult?.complete(AppResult.Success(Unit))
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.isReactionSubmitting)

            viewModel.addComment()
            assertEquals("请输入评论", viewModel.uiState.value.commentError)
            viewModel.onCommentChanged("周六一起去")
            viewModel.addComment()
            viewModel.addComment()
            runCurrent()
            assertEquals(1, ideas.commentCalls)
            ideas.commentResult?.complete(AppResult.Failure(AppError.PERMISSION_DENIED))
            advanceUntilIdle()
            assertEquals("周六一起去", viewModel.uiState.value.commentInput)
            assertEquals("没有权限完成此操作", viewModel.uiState.value.message)
        }

    @Test
    fun `same reaction cancels and comment deletion expose guarded submitting state`() =
        runTest(dispatcher) {
            val ideas = FakeIdeaRepository()
            ideas.idea.value = AppResult.Success(sampleIdea())
            ideas.reactions.value = AppResult.Success(
                RealtimeItems(
                    listOf(
                        IdeaReaction(
                            userId = "bob",
                            value = ReactionValue.WANT,
                            userSnapshot = MemberSnapshot("小周", null),
                            createdAt = Instant.EPOCH,
                            updatedAt = Instant.EPOCH,
                        ),
                    ),
                    false,
                    false,
                ),
            )
            ideas.clearReactionResult = CompletableDeferred()
            ideas.deleteCommentResult = CompletableDeferred()
            val viewModel = IdeaDetailViewModel(
                SavedStateHandle(mapOf("groupId" to "group-1", "ideaId" to "idea-1")),
                FakeGroupRepository(currentUid = "bob"),
                ideas,
                FakeIdeaImageRepository(),
                FakeAvatarRepository(),
            )
            runCurrent()

            viewModel.setReaction(ReactionValue.WANT)
            viewModel.setReaction(ReactionValue.WANT)
            runCurrent()
            assertEquals(1, ideas.clearReactionCalls)
            ideas.clearReactionResult?.complete(AppResult.Success(Unit))
            advanceUntilIdle()

            viewModel.deleteComment("comment-1")
            viewModel.deleteComment("comment-2")
            runCurrent()
            assertEquals("comment-1", viewModel.uiState.value.deletingCommentId)
            assertEquals(1, ideas.deleteCommentCalls)
            ideas.deleteCommentResult?.complete(AppResult.Success(Unit))
            advanceUntilIdle()
            assertEquals(null, viewModel.uiState.value.deletingCommentId)
        }

    @Test
    fun `detail exits when a remote delete makes the idea unreadable`() = runTest(dispatcher) {
        val ideas = FakeIdeaRepository()
        val viewModel = IdeaDetailViewModel(
            SavedStateHandle(mapOf("groupId" to "group-1", "ideaId" to "idea-1")),
            FakeGroupRepository(currentUid = "bob"),
            ideas,
            FakeIdeaImageRepository(),
            FakeAvatarRepository(),
        )
        runCurrent()
        assertFalse(viewModel.uiState.value.ideaDeleted)

        ideas.idea.value = AppResult.Failure(AppError.PERMISSION_DENIED)
        runCurrent()

        assertTrue(viewModel.uiState.value.ideaDeleted)
    }

    @Test
    fun `detail resolves creator reaction and comment avatar snapshots`() = runTest(dispatcher) {
        val ideas = FakeIdeaRepository()
        ideas.idea.value = AppResult.Success(
            sampleIdea().copy(creatorSnapshot = MemberSnapshot("小周", "creator.webp")),
        )
        ideas.reactions.value = AppResult.Success(
            RealtimeItems(
                listOf(
                    IdeaReaction(
                        userId = "alice",
                        value = ReactionValue.OK,
                        userSnapshot = MemberSnapshot("小林", "reaction.webp"),
                        createdAt = Instant.EPOCH,
                        updatedAt = Instant.EPOCH,
                    ),
                ),
                false,
                false,
            ),
        )
        ideas.comments.value = AppResult.Success(
            RealtimeItems(
                listOf(
                    IdeaComment(
                        id = "comment-1",
                        content = "周六见",
                        createdBy = "alice",
                        creatorSnapshot = MemberSnapshot("小林", "comment.webp"),
                        createdAt = Instant.EPOCH,
                    ),
                ),
                false,
                false,
            ),
        )
        val viewModel = IdeaDetailViewModel(
            SavedStateHandle(mapOf("groupId" to "group-1", "ideaId" to "idea-1")),
            FakeGroupRepository(currentUid = "bob"),
            ideas,
            FakeIdeaImageRepository(),
            FakeAvatarRepository(),
        )
        advanceUntilIdle()

        val idea = (viewModel.uiState.value.idea as LoadState.Content).data
        val comments = (viewModel.uiState.value.comments as LoadState.Content).data
        assertNotNull(idea.creatorSnapshot.avatarUrl)
        assertNotNull(viewModel.uiState.value.reactions.single().userSnapshot.avatarUrl)
        assertNotNull(comments.single().creatorSnapshot.avatarUrl)
    }
}

private class FakeIdeaRepository : IdeaRepository {
    val ideaList = MutableStateFlow<AppResult<RealtimeItems<Idea>>>(
        AppResult.Success(RealtimeItems(emptyList(), false, false)),
    )
    val idea = MutableStateFlow<AppResult<Idea>>(AppResult.Success(sampleIdea()))
    val reactions = MutableStateFlow<AppResult<RealtimeItems<IdeaReaction>>>(
        AppResult.Success(RealtimeItems(emptyList(), false, false)),
    )
    val comments = MutableStateFlow<AppResult<RealtimeItems<IdeaComment>>>(
        AppResult.Success(RealtimeItems(emptyList(), false, false)),
    )
    var observeIdeasCalls = 0
    var lastStatus: IdeaStatus? = null
    var lastCategory: IdeaCategory? = null
    var createCalls = 0
    var updateCalls = 0
    var reactionCalls = 0
    var commentCalls = 0
    var clearReactionCalls = 0
    var deleteCommentCalls = 0
    var createResult: CompletableDeferred<AppResult<Unit>>? = null
    var updateResult: CompletableDeferred<AppResult<Unit>>? = null
    var reactionResult: CompletableDeferred<AppResult<Unit>>? = null
    var commentResult: CompletableDeferred<AppResult<Unit>>? = null
    var clearReactionResult: CompletableDeferred<AppResult<Unit>>? = null
    var deleteCommentResult: CompletableDeferred<AppResult<Unit>>? = null

    override fun newIdeaId(groupId: String) = "idea-new"
    override fun observeIdeas(
        groupId: String,
        status: IdeaStatus,
        category: IdeaCategory?,
    ): Flow<AppResult<RealtimeItems<Idea>>> {
        observeIdeasCalls += 1
        lastStatus = status
        lastCategory = category
        return ideaList
    }

    override fun observeIdea(groupId: String, ideaId: String) = idea
    override fun observeReactions(groupId: String, ideaId: String) = reactions
    override fun observeComments(groupId: String, ideaId: String) = comments

    override suspend fun createIdea(
        groupId: String,
        ideaId: String,
        draft: IdeaDraft,
        requireServerAck: Boolean,
    ): AppResult<Unit> {
        createCalls += 1
        return createResult?.await() ?: AppResult.Success(Unit)
    }

    override suspend fun updateIdea(
        groupId: String,
        ideaId: String,
        draft: IdeaDraft,
        requireServerAck: Boolean,
    ): AppResult<Unit> {
        updateCalls += 1
        return updateResult?.await() ?: AppResult.Success(Unit)
    }

    override suspend fun deleteIdea(groupId: String, ideaId: String) = AppResult.Success(Unit)

    override suspend fun setReaction(
        groupId: String,
        ideaId: String,
        value: ReactionValue,
        isExisting: Boolean,
    ): AppResult<Unit> {
        reactionCalls += 1
        return reactionResult?.await() ?: AppResult.Success(Unit)
    }

    override suspend fun clearReaction(groupId: String, ideaId: String): AppResult<Unit> {
        clearReactionCalls += 1
        return clearReactionResult?.await() ?: AppResult.Success(Unit)
    }

    override suspend fun addComment(
        groupId: String,
        ideaId: String,
        content: String,
    ): AppResult<Unit> {
        commentCalls += 1
        return commentResult?.await() ?: AppResult.Success(Unit)
    }

    override suspend fun deleteComment(
        groupId: String,
        ideaId: String,
        commentId: String,
    ): AppResult<Unit> {
        deleteCommentCalls += 1
        return deleteCommentResult?.await() ?: AppResult.Success(Unit)
    }
}

private class FakeIdeaImageRepository : IdeaImageRepository {
    override suspend fun uploadCover(groupId: String, ideaId: String, sourceUri: String) =
        AppResult.Success(IdeaMedia("cover.webp", "image/webp", 100, 100, 100))
    override suspend fun getDownloadUrl(storagePath: String) =
        AppResult.Success("https://example.invalid/cover.webp")
    override suspend fun delete(storagePath: String) = AppResult.Success(Unit)
}

private class FakeGroupRepository(
    private val currentUid: String = "bob",
) : GroupRepository {
    private val group = Group(
        id = "group-1",
        name = "周末去哪",
        adminId = "alice",
        status = GroupStatus.ACTIVE,
        memberCount = 3,
        ideaCount = 0,
        scheduledCount = 0,
        completedCount = 0,
        lastActivityAt = Instant.EPOCH,
    )
    override fun currentUserId() = currentUid
    override fun observeMyGroups(): Flow<AppResult<List<GroupSummary>>> =
        flowOf(AppResult.Success(emptyList()))
    override fun observeGroup(groupId: String): Flow<AppResult<Group>> =
        flowOf(AppResult.Success(group))
    override fun observeMembers(groupId: String): Flow<AppResult<List<GroupMember>>> =
        flowOf(
            AppResult.Success(
                listOf(
                    GroupMember("alice", GroupRole.ADMIN, "小林", null),
                    GroupMember("bob", GroupRole.MEMBER, "小周", null),
                    GroupMember("charlie", GroupRole.MEMBER, "小陈", null),
                ),
            ),
        )
    override suspend fun createGroup(name: String, requestId: String) =
        AppResult.Success("group-1")
    override suspend fun updateGroupName(groupId: String, name: String) =
        AppResult.Success(Unit)
    override suspend fun getOrCreateInvite(groupId: String): AppResult<InviteCredentials> =
        AppResult.Failure(AppError.NOT_FOUND)
    override suspend fun rotateInvite(groupId: String, requestId: String) =
        AppResult.Failure(AppError.NOT_FOUND)
    override suspend fun previewInvite(credential: InviteCredential): AppResult<InvitePreview> =
        AppResult.Failure(AppError.NOT_FOUND)
    override suspend fun acceptInvite(credential: InviteCredential) =
        AppResult.Success("group-1")
    override suspend fun sendDirectInvite(groupId: String, email: String) =
        AppResult.Success(Unit)
    override suspend fun acceptDirectInvite(invitationId: String) =
        AppResult.Success("group-1")
    override suspend fun declineDirectInvite(invitationId: String) =
        AppResult.Success(Unit)
    override suspend fun leaveGroup(groupId: String) = AppResult.Success(Unit)
    override suspend fun removeMember(groupId: String, userId: String) =
        AppResult.Success(Unit)
    override suspend fun transferAdmin(groupId: String, userId: String) =
        AppResult.Success(Unit)
    override suspend fun dissolveGroup(groupId: String, confirmationName: String) =
        AppResult.Success(Unit)
}

private class FakeAvatarRepository : AvatarRepository {
    override suspend fun uploadAvatar(userId: String, sourceUri: String) =
        AppResult.Success("avatar.webp")
    override suspend fun getDownloadUrl(storagePath: String) =
        AppResult.Success("https://example.invalid/avatar.webp")
    override suspend fun deleteAvatar(storagePath: String) = AppResult.Success(Unit)
}

private fun sampleIdea(createdBy: String = "bob") = Idea(
    id = "idea-1",
    groupId = "group-1",
    title = "去植物园",
    category = IdeaCategory.PLACE,
    note = null,
    media = null,
    locationOrLink = null,
    createdBy = createdBy,
    creatorSnapshot = MemberSnapshot("小周", null),
    status = IdeaStatus.IDEA,
    reactionCounts = ReactionCounts(),
    commentCount = 0,
    lastModifiedBy = createdBy,
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
