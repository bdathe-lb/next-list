package com.example.nextlist.feature.groups

import androidx.lifecycle.SavedStateHandle
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.domain.model.Group
import com.example.nextlist.domain.model.GroupMember
import com.example.nextlist.domain.model.GroupSummary
import com.example.nextlist.domain.model.InviteCredential
import com.example.nextlist.domain.model.InviteCredentials
import com.example.nextlist.domain.model.InvitePreview
import com.example.nextlist.domain.model.MemberSnapshot
import com.example.nextlist.domain.model.PendingInvite
import com.example.nextlist.domain.repository.GroupRepository
import com.example.nextlist.domain.repository.PendingInviteRepository
import com.example.nextlist.domain.repository.AvatarRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GroupViewModelsTest {
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
    fun `groups view model exposes loading content and error states`() = runTest(dispatcher) {
        val repository = FakeGroupRepository()
        val viewModel = GroupsViewModel(repository, FakeAvatarRepository())
        val collection = backgroundScope.launch { viewModel.uiState.collect {} }
        runCurrent()
        assertTrue(viewModel.uiState.value is LoadState.Empty)

        repository.groups.value = AppResult.Success(
            listOf(
                GroupSummary(
                    id = "group-1",
                    name = "周末去哪",
                    memberCount = 2,
                    ideaCount = 0,
                    scheduledCount = 0,
                    members = emptyList(),
                    lastActivityAt = Instant.EPOCH,
                ),
            ),
        )
        runCurrent()
        assertTrue(viewModel.uiState.value is LoadState.Content)

        repository.groups.value = AppResult.Failure(AppError.NETWORK_UNAVAILABLE)
        runCurrent()
        assertEquals(
            AppError.NETWORK_UNAVAILABLE,
            (viewModel.uiState.value as LoadState.Error).kind,
        )
        collection.cancel()
    }

    @Test
    fun `create group prevents duplicate submit while request is pending`() = runTest(dispatcher) {
        val repository = FakeGroupRepository()
        val viewModel = CreateGroupViewModel(repository)
        viewModel.onNameChanged("周末去哪")

        viewModel.submit()
        viewModel.submit()
        runCurrent()

        assertEquals(1, repository.createCalls)
        assertTrue(viewModel.uiState.value.isSubmitting)
        repository.createResult.complete(AppResult.Success("group-1"))
        advanceUntilIdle()
        assertEquals("group-1", viewModel.uiState.value.createdGroupId)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `join preview and accept prevent duplicate joins`() = runTest(dispatcher) {
        val repository = FakeGroupRepository()
        val pending = FakePendingInviteRepository()
        val viewModel = JoinGroupViewModel(
            SavedStateHandle(mapOf("kind" to "code", "value" to "ABCDEFGH")),
            repository,
            pending,
            FakeAvatarRepository(),
        )
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.preview is LoadState.Content)

        viewModel.accept()
        viewModel.accept()
        runCurrent()
        assertEquals(1, repository.acceptCalls)
        repository.acceptResult.complete(AppResult.Success("group-1"))
        advanceUntilIdle()
        assertEquals("group-1", viewModel.uiState.value.joinedGroupId)
        assertEquals(1, pending.clearCalls)
    }
}

private class FakeGroupRepository : GroupRepository {
    val groups = MutableStateFlow<AppResult<List<GroupSummary>>>(AppResult.Success(emptyList()))
    var createCalls = 0
    var acceptCalls = 0
    val createResult = CompletableDeferred<AppResult<String>>()
    val acceptResult = CompletableDeferred<AppResult<String>>()

    override fun currentUserId(): String = "alice"
    override fun observeMyGroups(): Flow<AppResult<List<GroupSummary>>> = groups
    override fun observeGroup(groupId: String): Flow<AppResult<Group>> =
        flowOf(AppResult.Failure(AppError.NOT_FOUND))
    override fun observeMembers(groupId: String): Flow<AppResult<List<GroupMember>>> =
        flowOf(AppResult.Success(emptyList()))

    override suspend fun createGroup(name: String, requestId: String): AppResult<String> {
        createCalls += 1
        return createResult.await()
    }

    override suspend fun updateGroupName(groupId: String, name: String) = AppResult.Success(Unit)
    override suspend fun getOrCreateInvite(groupId: String): AppResult<InviteCredentials> =
        AppResult.Failure(AppError.NOT_FOUND)
    override suspend fun rotateInvite(
        groupId: String,
        requestId: String,
    ): AppResult<InviteCredentials> = AppResult.Failure(AppError.NOT_FOUND)

    override suspend fun previewInvite(credential: InviteCredential): AppResult<InvitePreview> =
        AppResult.Success(
            InvitePreview(
                groupId = "group-1",
                groupName = "周末去哪",
                memberCount = 1,
                members = listOf(MemberSnapshot("小林", null)),
            ),
        )

    override suspend fun acceptInvite(credential: InviteCredential): AppResult<String> {
        acceptCalls += 1
        return acceptResult.await()
    }

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

private class FakePendingInviteRepository : PendingInviteRepository {
    private val pending = MutableStateFlow<PendingInvite?>(null)
    var clearCalls = 0

    override fun observe(): Flow<PendingInvite?> = pending
    override suspend fun save(invite: PendingInvite) {
        pending.value = invite
    }
    override suspend fun clear() {
        clearCalls += 1
        pending.value = null
    }
}

private class FakeAvatarRepository : AvatarRepository {
    override suspend fun uploadAvatar(
        userId: String,
        sourceUri: String,
    ) = AppResult.Success("users/$userId/avatar/test.webp")

    override suspend fun getDownloadUrl(storagePath: String) =
        AppResult.Success("https://example.invalid/avatar.webp")

    override suspend fun deleteAvatar(storagePath: String) = AppResult.Success(Unit)
}
