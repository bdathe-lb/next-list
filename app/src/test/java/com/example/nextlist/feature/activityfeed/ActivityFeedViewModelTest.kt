package com.example.nextlist.feature.activityfeed

import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.domain.model.ActivityFeedCursor
import com.example.nextlist.domain.model.ActivityFeedItem
import com.example.nextlist.domain.model.ActivityFeedPage
import com.example.nextlist.domain.model.ActivityFeedType
import com.example.nextlist.domain.model.AppTarget
import com.example.nextlist.domain.model.MemberSnapshot
import com.example.nextlist.domain.model.toAppTarget
import com.example.nextlist.domain.repository.ActivityFeedRepository
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
class ActivityFeedViewModelTest {
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
    fun `feed exposes empty content error retry and realtime unread updates`() =
        runTest(dispatcher) {
            val repository = FakeActivityFeedRepository()
            val viewModel = ActivityFeedViewModel(repository)
            val collection = backgroundScope.launch { viewModel.uiState.collect {} }
            runCurrent()
            assertTrue(viewModel.uiState.value.content is LoadState.Empty)

            repository.latest.value = AppResult.Success(
                ActivityFeedPage(listOf(feedItem("feed-1"))),
            )
            runCurrent()
            assertEquals(1, viewModel.uiState.value.unreadCount)
            assertTrue(viewModel.uiState.value.content is LoadState.Content)

            repository.latest.value = AppResult.Failure(AppError.NETWORK_UNAVAILABLE)
            runCurrent()
            assertTrue(viewModel.uiState.value.content is LoadState.Content)
            assertTrue(viewModel.uiState.value.message != null)

            viewModel.refresh()
            runCurrent()
            assertTrue(repository.observeCalls >= 2)
            collection.cancel()
        }

    @Test
    fun `pagination deduplicates realtime items and marks single and all read`() =
        runTest(dispatcher) {
            val repository = FakeActivityFeedRepository()
            repository.latest.value = AppResult.Success(
                ActivityFeedPage(
                    (1..30).map { feedItem("feed-$it", seconds = 100L - it) },
                    nextCursor = ActivityFeedCursor(
                        createdAt = Instant.ofEpochSecond(70),
                        documentId = "feed-30",
                    ),
                ),
            )
            repository.moreResult = AppResult.Success(
                ActivityFeedPage(
                    listOf(
                        feedItem("feed-30", seconds = 70),
                        feedItem("feed-31", seconds = 69),
                    ),
                ),
            )
            val viewModel = ActivityFeedViewModel(repository)
            runCurrent()
            viewModel.loadMore()
            advanceUntilIdle()
            val items = (viewModel.uiState.value.content as LoadState.Content).data
            assertEquals(31, items.size)

            var target: AppTarget? = null
            viewModel.open(items.first()) { target = it }
            advanceUntilIdle()
            assertEquals(1, repository.markReadCalls)
            assertTrue(target is AppTarget.Idea)
            assertEquals(30, viewModel.uiState.value.unreadCount)

            viewModel.markAllRead()
            advanceUntilIdle()
            assertEquals(1, repository.markAllCalls)
            assertEquals(0, viewModel.uiState.value.unreadCount)
            assertFalse(viewModel.uiState.value.isMarkingAllRead)
        }

    @Test
    fun `six feed types have safe descriptions and invitation mapping`() {
        val types = ActivityFeedType.entries
        assertEquals(6, types.size)
        types.forEach { type ->
            assertTrue(feedItem("id", type = type).description().isNotBlank())
        }
        val invitation = feedItem(
            id = "invite",
            type = ActivityFeedType.GROUP_INVITED,
            ideaId = null,
            invitationId = "direct-1",
        )
        assertTrue(invitation.toAppTarget() is AppTarget.Invitation)
    }

    @Test
    fun `failed read does not navigate while an already read item opens offline`() =
        runTest(dispatcher) {
            val repository = FakeActivityFeedRepository()
            repository.latest.value = AppResult.Success(
                ActivityFeedPage(listOf(feedItem("feed-1"))),
            )
            repository.markReadResult = AppResult.Failure(AppError.NETWORK_UNAVAILABLE)
            val viewModel = ActivityFeedViewModel(repository)
            runCurrent()
            var target: AppTarget? = null

            viewModel.open(feedItem("feed-1")) { target = it }
            advanceUntilIdle()
            assertEquals(null, target)
            assertTrue(viewModel.uiState.value.message != null)

            viewModel.open(feedItem("feed-1").copy(readAt = Instant.now())) { target = it }
            assertTrue(target is AppTarget.Idea)
            assertEquals(1, repository.markReadCalls)
        }
}

private class FakeActivityFeedRepository : ActivityFeedRepository {
    val latest = MutableStateFlow<AppResult<ActivityFeedPage>>(
        AppResult.Success(ActivityFeedPage(emptyList())),
    )
    var moreResult: AppResult<ActivityFeedPage> =
        AppResult.Success(ActivityFeedPage(emptyList()))
    var observeCalls = 0
    var markReadCalls = 0
    var markAllCalls = 0
    var markReadResult: AppResult<Unit> = AppResult.Success(Unit)

    override fun observeLatest(pageSize: Int): Flow<AppResult<ActivityFeedPage>> {
        observeCalls += 1
        return latest
    }

    override suspend fun loadMore(
        cursor: ActivityFeedCursor,
        pageSize: Int,
    ): AppResult<ActivityFeedPage> = moreResult

    override suspend fun markRead(feedId: String): AppResult<Unit> {
        markReadCalls += 1
        return markReadResult
    }

    override suspend fun markAllRead(): AppResult<Unit> {
        markAllCalls += 1
        return AppResult.Success(Unit)
    }
}

private fun feedItem(
    id: String,
    seconds: Long = 100,
    type: ActivityFeedType = ActivityFeedType.IDEA_CREATED,
    ideaId: String? = "idea-1",
    invitationId: String? = null,
): ActivityFeedItem = ActivityFeedItem(
    id = id,
    type = type,
    groupId = "group-1",
    groupName = "周末去哪",
    ideaId = ideaId,
    ideaTitle = "去植物园",
    invitationId = invitationId,
    actorId = "alice",
    actor = MemberSnapshot("小林", null),
    createdAt = Instant.ofEpochSecond(seconds),
    readAt = null,
)
