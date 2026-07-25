package com.example.nextlist.feature.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.domain.model.GroupSummary
import com.example.nextlist.domain.model.MemberSnapshot
import coil3.compose.AsyncImage

@Composable
fun GroupsRoute(
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GroupsScreen(
        state = state,
        onCreateGroup = onCreateGroup,
        onJoinGroup = onJoinGroup,
        onOpenGroup = onOpenGroup,
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}

@Composable
fun GroupsScreen(
    state: LoadState<List<GroupSummary>>,
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    onOpenGroup: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateGroup,
                text = { Text("创建小组") },
                icon = { Text("+", style = MaterialTheme.typography.titleLarge) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "我的小组",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = onJoinGroup) {
                    Text("加入小组")
                }
            }
            Spacer(Modifier.height(12.dp))
            when (state) {
                LoadState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is LoadState.Empty -> GroupEmptyState(
                    onCreateGroup = onCreateGroup,
                    onJoinGroup = onJoinGroup,
                    modifier = Modifier.weight(1f),
                )
                is LoadState.Error -> ErrorState(
                    message = state.kind.toUserMessage(),
                    onRetry = onRetry,
                )
                is LoadState.Content -> LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.data, key = GroupSummary::id) { group ->
                        GroupCard(group = group, onClick = { onOpenGroup(group.id) })
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
}

@Composable
private fun GroupEmptyState(
    onCreateGroup: () -> Unit,
    onJoinGroup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "还没有小组",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "创建一个小组，或使用朋友发来的邀请码加入。",
            modifier = Modifier.padding(top = 8.dp, bottom = 20.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onCreateGroup) {
            Text("创建小组")
        }
        TextButton(onClick = onJoinGroup) {
            Text("输入邀请码")
        }
    }
}

@Composable
private fun GroupCard(group: GroupSummary, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = group.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            MemberAvatarRow(group.members, group.memberCount)
            Text(
                text = "${group.memberCount} 位成员 · ${group.ideaCount} 个想法 · " +
                    "${group.scheduledCount} 个已安排",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
internal fun MemberAvatarRow(
    members: List<MemberSnapshot>,
    memberCount: Int,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        members.take(5).forEach { member ->
            InitialAvatar(member.nickname, avatarUrl = member.avatarUrl)
        }
        if (memberCount > 5) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .semantics { contentDescription = "还有 ${memberCount - 5} 位成员" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "+${memberCount - 5}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

@Composable
internal fun InitialAvatar(
    nickname: String,
    modifier: Modifier = Modifier,
    size: Int = 36,
    avatarUrl: String? = null,
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .semantics { contentDescription = "$nickname 的头像" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = nickname.trim().firstOrNull()?.toString()?.uppercase() ?: "下",
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clearAndSetSemantics {},
        )
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clearAndSetSemantics {},
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
internal fun ErrorState(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("暂时无法加载", style = MaterialTheme.typography.titleLarge)
        Text(
            text = message,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry) {
                Text("重试")
            }
        }
    }
}
