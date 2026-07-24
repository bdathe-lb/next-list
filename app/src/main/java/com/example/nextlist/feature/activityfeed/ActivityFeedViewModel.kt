package com.example.nextlist.feature.activityfeed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.domain.model.ActivityFeedCursor
import com.example.nextlist.domain.model.ActivityFeedItem
import com.example.nextlist.domain.model.AppTarget
import com.example.nextlist.domain.model.toAppTarget
import com.example.nextlist.domain.repository.ActivityFeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ActivityFeedUiState(
    val content: LoadState<List<ActivityFeedItem>> = LoadState.Loading,
    val unreadCount: Int = 0,
    val isFromCache: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isMarkingAllRead: Boolean = false,
    val canLoadMore: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class ActivityFeedViewModel @Inject constructor(
    private val repository: ActivityFeedRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ActivityFeedUiState())
    val uiState = mutableState.asStateFlow()
    private val refreshSignal = MutableStateFlow(0)
    private var latest: List<ActivityFeedItem> = emptyList()
    private var older: List<ActivityFeedItem> = emptyList()
    private var nextCursor: ActivityFeedCursor? = null

    init {
        viewModelScope.launch {
            refreshSignal
                .flatMapLatest { repository.observeLatest(PAGE_SIZE) }
                .collectLatest(::onLatest)
        }
    }

    fun refresh() {
        older = emptyList()
        nextCursor = null
        mutableState.update {
            it.copy(isRefreshing = true, message = null)
        }
        refreshSignal.update(Int::inc)
    }

    fun loadMore() {
        if (mutableState.value.isLoadingMore || !mutableState.value.canLoadMore) return
        val cursor = nextCursor ?: return
        mutableState.update { it.copy(isLoadingMore = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.loadMore(cursor, PAGE_SIZE)) {
                is AppResult.Success -> {
                    val known = (latest + older).mapTo(mutableSetOf(), ActivityFeedItem::id)
                    older = (older + result.value.items.filterNot { it.id in known })
                        .sortedWith(feedComparator)
                    nextCursor = result.value.nextCursor
                    publish(
                        isFromCache = result.value.isFromCache,
                        canLoadMore = nextCursor != null,
                    )
                    mutableState.update { it.copy(isLoadingMore = false) }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isLoadingMore = false,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun open(item: ActivityFeedItem, navigate: (AppTarget) -> Unit) {
        if (item.readAt != null) {
            navigate(item.toAppTarget())
            return
        }
        viewModelScope.launch {
            when (val result = repository.markRead(item.id)) {
                is AppResult.Success -> {
                    markLocally(item.id)
                    navigate(item.toAppTarget())
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(message = result.error.toUserMessage())
                }
            }
        }
    }

    fun markAllRead() {
        if (mutableState.value.isMarkingAllRead) return
        mutableState.update { it.copy(isMarkingAllRead = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.markAllRead()) {
                is AppResult.Success -> {
                    val readAt = Instant.now()
                    latest = latest.map { item ->
                        if (item.readAt == null) item.copy(readAt = readAt) else item
                    }
                    older = older.map { item ->
                        if (item.readAt == null) item.copy(readAt = readAt) else item
                    }
                    publish(
                        isFromCache = mutableState.value.isFromCache,
                        canLoadMore = mutableState.value.canLoadMore,
                    )
                    mutableState.update { it.copy(isMarkingAllRead = false) }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isMarkingAllRead = false,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    private fun onLatest(result: AppResult<com.example.nextlist.domain.model.ActivityFeedPage>) {
        when (result) {
            is AppResult.Success -> {
                val hadOlderItems = older.isNotEmpty()
                val previousLatest = latest
                latest = result.value.items
                val latestIds = latest.mapTo(mutableSetOf(), ActivityFeedItem::id)
                older = (previousLatest + older)
                    .filterNot { it.id in latestIds }
                    .distinctBy(ActivityFeedItem::id)
                    .sortedWith(feedComparator)
                if (!hadOlderItems) {
                    nextCursor = result.value.nextCursor
                }
                publish(
                    isFromCache = result.value.isFromCache,
                    canLoadMore = nextCursor != null,
                )
            }
            is AppResult.Failure -> mutableState.update {
                it.copy(
                    content = if (combinedItems().isEmpty()) {
                        LoadState.Error(result.error, canRetry = true)
                    } else {
                        it.content
                    },
                    isRefreshing = false,
                    message = if (combinedItems().isEmpty()) null else result.error.toUserMessage(),
                )
            }
        }
    }

    private fun markLocally(feedId: String) {
        val readAt = Instant.now()
        latest = latest.map { if (it.id == feedId) it.copy(readAt = readAt) else it }
        older = older.map { if (it.id == feedId) it.copy(readAt = readAt) else it }
        publish(
            isFromCache = mutableState.value.isFromCache,
            canLoadMore = mutableState.value.canLoadMore,
        )
    }

    private fun publish(isFromCache: Boolean, canLoadMore: Boolean) {
        val items = combinedItems()
        mutableState.update {
            it.copy(
                content = if (items.isEmpty()) {
                    LoadState.Empty("还没有动态")
                } else {
                    LoadState.Content(items)
                },
                unreadCount = items.count { item -> item.readAt == null },
                isFromCache = isFromCache,
                isRefreshing = false,
                canLoadMore = canLoadMore,
                message = null,
            )
        }
    }

    private fun combinedItems(): List<ActivityFeedItem> =
        (latest + older).distinctBy(ActivityFeedItem::id).sortedWith(feedComparator)

    private companion object {
        const val PAGE_SIZE = 30
        val feedComparator = compareByDescending<ActivityFeedItem> { it.createdAt }
            .thenByDescending { it.id }
    }
}
