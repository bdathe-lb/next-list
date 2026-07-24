package com.example.nextlist.feature.activityfeed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.domain.model.ActivityFeedItem
import com.example.nextlist.domain.model.ActivityFeedType
import com.example.nextlist.domain.model.AppTarget
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ActivityFeedRoute(
    onOpenTarget: (AppTarget) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ActivityFeedViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ActivityFeedScreen(
        state = state,
        onRefresh = viewModel::refresh,
        onRetry = viewModel::refresh,
        onLoadMore = viewModel::loadMore,
        onMarkAllRead = viewModel::markAllRead,
        onOpen = { item ->
            viewModel.open(item) { target ->
                onOpenTarget(target)
            }
        },
        modifier = modifier,
    )
}

@Composable
fun ActivityFeedScreen(
    state: ActivityFeedUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpen: (ActivityFeedItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "动态",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (state.content is LoadState.Content) {
                    TextButton(
                        onClick = onMarkAllRead,
                        enabled = !state.isMarkingAllRead,
                    ) {
                        Text(if (state.isMarkingAllRead) "处理中…" else "全部标记已读")
                    }
                }
            }
            if (state.isFromCache) {
                Text(
                    text = "当前显示离线缓存，联网后会自动更新",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            state.message?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when (val content = state.content) {
                LoadState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is LoadState.Empty -> FeedStatus(
                    title = "还没有动态",
                    body = "小组里的重要变化和定向邀请会显示在这里。",
                    modifier = Modifier.weight(1f),
                )
                is LoadState.Error -> FeedStatus(
                    title = "暂时无法加载动态",
                    body = content.kind.toUserMessage(),
                    modifier = Modifier.weight(1f),
                    action = {
                        Button(onClick = onRetry) { Text("重试") }
                    },
                )
                is LoadState.Content -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(content.data, key = ActivityFeedItem::id) { item ->
                        FeedCard(item = item, onClick = { onOpen(item) })
                    }
                    if (state.canLoadMore) {
                        item(key = "load-more") {
                            OutlinedButton(
                                onClick = onLoadMore,
                                enabled = !state.isLoadingMore,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.isLoadingMore) {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                }
                                Text(if (state.isLoadingMore) "正在加载…" else "加载更早动态")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedCard(item: ActivityFeedItem, onClick: () -> Unit) {
    val unread = item.readAt == null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = buildString {
                    append(item.description())
                    append("，小组")
                    append(item.groupName)
                    if (unread) append("，未读")
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (unread) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.secondaryContainer,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(item.glyph(), fontWeight = FontWeight.SemiBold)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = item.description(),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text = item.groupName,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = feedTimeFormatter.format(item.createdAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (unread) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .size(9.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .semantics { contentDescription = "未读" },
                )
            }
        }
    }
}

@Composable
private fun FeedStatus(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            action?.invoke()
        }
    }
}

internal fun ActivityFeedItem.description(): String {
    val title = ideaTitle?.let { "「$it」" } ?: "一条想法"
    return when (type) {
        ActivityFeedType.IDEA_CREATED -> "${actor.nickname}添加了新想法$title"
        ActivityFeedType.SCHEDULE_CREATED -> "${actor.nickname}安排了$title"
        ActivityFeedType.SCHEDULE_UPDATED -> "${actor.nickname}更新了${title}的安排"
        ActivityFeedType.IDEA_COMMENTED -> "${actor.nickname}评论了你的想法$title"
        ActivityFeedType.IDEA_COMPLETED -> "${actor.nickname}完成了$title"
        ActivityFeedType.GROUP_INVITED -> "${actor.nickname}邀请你加入小组"
    }
}

private fun ActivityFeedItem.glyph(): String = when (type) {
    ActivityFeedType.IDEA_CREATED -> "想"
    ActivityFeedType.SCHEDULE_CREATED -> "日"
    ActivityFeedType.SCHEDULE_UPDATED -> "更"
    ActivityFeedType.IDEA_COMMENTED -> "评"
    ActivityFeedType.IDEA_COMPLETED -> "✓"
    ActivityFeedType.GROUP_INVITED -> "邀"
}

private val feedTimeFormatter = DateTimeFormatter
    .ofPattern("M月d日 HH:mm")
    .withZone(ZoneId.systemDefault())
