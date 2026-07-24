package com.example.nextlist.feature.ideas

import androidx.activity.compose.BackHandler
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.domain.model.Idea
import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaComment
import com.example.nextlist.domain.model.IdeaReaction
import com.example.nextlist.domain.model.IdeaStatus
import com.example.nextlist.domain.model.ReactionValue
import com.example.nextlist.domain.model.RsvpValue
import com.example.nextlist.feature.groups.InitialAvatar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun IdeaFormRoute(
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IdeaFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.savedIdeaId) {
        state.savedIdeaId?.let(onSaved)
    }
    IdeaFormScreen(
        state = state,
        onTitleChanged = viewModel::onTitleChanged,
        onCategoryChanged = viewModel::onCategoryChanged,
        onNoteChanged = viewModel::onNoteChanged,
        onLocationOrLinkChanged = viewModel::onLocationOrLinkChanged,
        onImageSelected = viewModel::onImageSelected,
        onRemoveImage = viewModel::removeImage,
        onSubmit = viewModel::submit,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun IdeaFormScreen(
    state: IdeaFormUiState,
    onTitleChanged: (String) -> Unit,
    onCategoryChanged: (IdeaCategory) -> Unit,
    onNoteChanged: (String) -> Unit,
    onLocationOrLinkChanged: (String) -> Unit,
    onImageSelected: (String) -> Unit,
    onRemoveImage: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    val requestBack = {
        if (state.isDirty && !state.isSubmitting) showDiscardDialog = true else onBack()
    }
    BackHandler(enabled = state.isDirty && !state.isSubmitting) { showDiscardDialog = true }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.toString()?.let(onImageSelected)
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑想法" else "添加想法") },
                navigationIcon = {
                    TextButton(onClick = requestBack, enabled = !state.isSubmitting) {
                        Text("返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("标题") },
                    singleLine = true,
                    isError = state.titleError != null,
                    supportingText = { Text(state.titleError ?: "必填，1～80 个字符") },
                )
                Text("分类：${state.category.glyph} ${state.category.label}")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(IdeaCategory.entries, key = IdeaCategory::wireValue) { category ->
                        FilterChip(
                            selected = state.category == category,
                            onClick = { onCategoryChanged(category) },
                            enabled = !state.isSubmitting,
                            label = { Text("${category.glyph} ${category.label}") },
                        )
                    }
                }
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("备注（可选）") },
                    minLines = 4,
                    isError = state.noteError != null,
                    supportingText = { Text(state.noteError ?: "最多 1,000 个字符") },
                )
                OutlinedTextField(
                    value = state.locationOrLink,
                    onValueChange = onLocationOrLinkChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("地址或链接（可选）") },
                    isError = state.locationOrLinkError != null,
                    supportingText = {
                        Text(state.locationOrLinkError ?: "最多 500 个字符")
                    },
                )
                Text("封面图片（可选，最多一张）")
                when {
                    state.selectedImageUri != null -> AsyncImage(
                        model = state.selectedImageUri,
                        contentDescription = "待上传的想法封面",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                    state.existingMedia != null -> Text(
                        "已上传封面 · ${state.existingMedia.width} × ${state.existingMedia.height}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        enabled = !state.isSubmitting,
                    ) {
                        Text(if (state.existingMedia == null) "选择图片" else "替换图片")
                    }
                    if (state.selectedImageUri != null || state.existingMedia != null) {
                        TextButton(
                            onClick = onRemoveImage,
                            enabled = !state.isSubmitting,
                        ) {
                            Text("移除图片")
                        }
                    }
                }
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onSubmit,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(state.submitStage ?: "保存想法")
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("放弃本次编辑？") },
            text = { Text("尚未保存的内容会丢失。") },
            confirmButton = {
                TextButton(onClick = onBack) { Text("放弃") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("继续编辑") }
            },
        )
    }
}

@Composable
fun IdeaDetailRoute(
    onBack: () -> Unit,
    onEdit: (String, String) -> Unit,
    onSchedule: (String, String) -> Unit,
    onComplete: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: IdeaDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.ideaDeleted) {
        if (state.ideaDeleted) onBack()
    }
    IdeaDetailScreen(
        state = state,
        canEditIdea = viewModel.canEditIdea(),
        canDeleteIdea = viewModel.canDeleteIdea(),
        canDeleteComment = viewModel::canDeleteComment,
        onSetReaction = viewModel::setReaction,
        onSetRsvp = viewModel::setRsvp,
        onCommentChanged = viewModel::onCommentChanged,
        onAddComment = viewModel::addComment,
        onDeleteComment = viewModel::deleteComment,
        onDeleteIdea = viewModel::deleteIdea,
        onEdit = onEdit,
        onSchedule = onSchedule,
        onComplete = onComplete,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun IdeaDetailScreen(
    state: IdeaDetailUiState,
    canEditIdea: Boolean,
    canDeleteIdea: Boolean,
    canDeleteComment: (IdeaComment) -> Boolean,
    onSetReaction: (ReactionValue) -> Unit,
    onSetRsvp: (RsvpValue) -> Unit,
    onCommentChanged: (String) -> Unit,
    onAddComment: () -> Unit,
    onDeleteComment: (String) -> Unit,
    onDeleteIdea: () -> Unit,
    onEdit: (String, String) -> Unit,
    onSchedule: (String, String) -> Unit,
    onComplete: (String, String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val idea = (state.idea as? LoadState.Content)?.data
    var confirmDeleteIdea by remember { mutableStateOf(false) }
    var confirmDeleteComment by remember { mutableStateOf<IdeaComment?>(null) }
    var showReactionMembers by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("想法详情") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = {
                    if (idea != null && canEditIdea) {
                        TextButton(onClick = { onEdit(idea.groupId, idea.id) }) {
                            Text("编辑")
                        }
                    }
                    if (idea != null && canDeleteIdea) {
                        TextButton(
                            onClick = { confirmDeleteIdea = true },
                            enabled = !state.isDeletingIdea,
                        ) {
                            Text(if (state.isDeletingIdea) "删除中…" else "删除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val ideaState = state.idea) {
            LoadState.Loading -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            is LoadState.Empty -> DetailStatus("想法不存在", padding)
            is LoadState.Error -> DetailStatus(ideaState.kind.toUserMessage(), padding)
            is LoadState.Content -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    top = 16.dp,
                    end = 20.dp,
                    bottom = 48.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item {
                    IdeaHeader(
                        idea = ideaState.data,
                        imageUrl = state.imageUrl,
                    )
                }
                if (ideaState.data.status == IdeaStatus.IDEA) {
                    item {
                        Button(
                            onClick = {
                                onSchedule(ideaState.data.groupId, ideaState.data.id)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("安排活动")
                        }
                    }
                }
                item { HorizontalDivider() }
                item {
                    ReactionSection(
                        reactions = state.reactions,
                        currentUserId = state.currentUserId,
                        memberCount = state.members.size,
                        isSubmitting = state.isReactionSubmitting,
                        onSetReaction = onSetReaction,
                        onShowMembers = { showReactionMembers = true },
                    )
                }
                if (ideaState.data.schedule != null) {
                    item { HorizontalDivider() }
                    item {
                        ScheduleAndRsvpSection(
                            idea = ideaState.data,
                            rsvps = state.rsvps,
                            members = state.members,
                            currentUserId = state.currentUserId,
                            isSubmitting = state.isRsvpSubmitting,
                            onSetRsvp = onSetRsvp,
                            onEditSchedule = {
                                onSchedule(ideaState.data.groupId, ideaState.data.id)
                            },
                            onComplete = {
                                onComplete(ideaState.data.groupId, ideaState.data.id)
                            },
                        )
                    }
                }
                if (ideaState.data.completion != null) {
                    item { HorizontalDivider() }
                    item {
                        CompletionSection(
                            idea = ideaState.data,
                            members = state.members,
                            photoUrl = state.completionPhotoUrl,
                            onEdit = {
                                onComplete(ideaState.data.groupId, ideaState.data.id)
                            },
                        )
                    }
                }
                item { HorizontalDivider() }
                item {
                    Text(
                        "评论 ${ideaState.data.commentCount}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                when (val comments = state.comments) {
                    LoadState.Loading -> item { CircularProgressIndicator(Modifier.size(24.dp)) }
                    is LoadState.Empty -> item {
                        Text(
                            comments.reason,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    is LoadState.Error -> item { Text(comments.kind.toUserMessage()) }
                    is LoadState.Content -> items(comments.data, key = IdeaComment::id) { comment ->
                        CommentCard(
                            comment = comment,
                            canDelete = canDeleteComment(comment),
                            deleting = state.deletingCommentId == comment.id,
                            onDelete = { confirmDeleteComment = comment },
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = state.commentInput,
                        onValueChange = onCommentChanged,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !state.isCommentSubmitting,
                        label = { Text("写下评论") },
                        minLines = 2,
                        maxLines = 5,
                        isError = state.commentError != null,
                        supportingText = { Text(state.commentError ?: "1～500 个字符") },
                    )
                }
                item {
                    Button(
                        onClick = onAddComment,
                        enabled = !state.isCommentSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.isCommentSubmitting) "正在发布…" else "发布评论")
                    }
                }
                state.message?.let { message ->
                    item { Text(message, color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }

    if (confirmDeleteIdea) {
        AlertDialog(
            onDismissRequest = { confirmDeleteIdea = false },
            title = { Text("删除这个想法？") },
            text = { Text("删除后会从小组列表中移除。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteIdea = false
                        onDeleteIdea()
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteIdea = false }) { Text("取消") }
            },
        )
    }
    confirmDeleteComment?.let { comment ->
        AlertDialog(
            onDismissRequest = { confirmDeleteComment = null },
            title = { Text("删除这条评论？") },
            text = { Text("删除后评论正文将不再显示。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteComment = null
                        onDeleteComment(comment.id)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteComment = null }) { Text("取消") }
            },
        )
    }
    if (showReactionMembers) {
        ReactionMembersSheet(
            reactions = state.reactions,
            memberCount = state.members.size,
            onDismiss = { showReactionMembers = false },
        )
    }
}

@Composable
private fun IdeaHeader(
    idea: Idea,
    imageUrl: String?,
) {
    val uriHandler = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row {
            Text(
                "${idea.category.glyph} ${idea.category.label}",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.weight(1f))
            Text(idea.status.label)
        }
        Text(
            idea.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "${idea.title} 的封面",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
        }
        idea.note?.let { Text(it) }
        idea.locationOrLink?.let { value ->
            val isWebLink = value.startsWith("https://") || value.startsWith("http://")
            Text(
                text = value,
                modifier = if (isWebLink) {
                    Modifier.clickable { uriHandler.openUri(value) }
                } else {
                    Modifier
                },
                color = if (isWebLink) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            InitialAvatar(
                nickname = idea.creatorSnapshot.nickname,
                avatarUrl = idea.creatorSnapshot.avatarUrl,
            )
            Column(Modifier.padding(start = 10.dp)) {
                Text(idea.creatorSnapshot.nickname)
                Text(
                    formatDetailTime(idea.createdAt),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (idea.hasPendingWrites) {
                Spacer(Modifier.weight(1f))
                Text("等待同步", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun ReactionSection(
    reactions: List<IdeaReaction>,
    currentUserId: String?,
    memberCount: Int,
    isSubmitting: Boolean,
    onSetReaction: (ReactionValue) -> Unit,
    onShowMembers: () -> Unit,
) {
    val current = reactions.firstOrNull { it.userId == currentUserId }?.value
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            if (current == null) "选择你的态度" else "你的态度：${current.label}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        ReactionValue.entries.forEach { value ->
            val count = reactions.count { it.value == value }
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = current == value,
                    onClick = { onSetReaction(value) },
                    enabled = !isSubmitting,
                    label = { Text(value.label) },
                )
                TextButton(onClick = onShowMembers) {
                    Text("$count 人")
                }
            }
        }
        TextButton(onClick = onShowMembers) {
            Text("查看成员明细 · 还有 ${(memberCount - reactions.size).coerceAtLeast(0)} 人未表态")
        }
        if (reactions.any { it.hasPendingWrites }) {
            Text(
                "表态等待同步",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun CommentCard(
    comment: IdeaComment,
    canDelete: Boolean,
    deleting: Boolean,
    onDelete: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                InitialAvatar(
                    nickname = comment.creatorSnapshot.nickname,
                    size = 32,
                    avatarUrl = comment.creatorSnapshot.avatarUrl,
                )
                Column(Modifier.padding(start = 8.dp)) {
                    Text(
                        comment.creatorSnapshot.nickname,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        formatDetailTime(comment.createdAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (canDelete) {
                    TextButton(onClick = onDelete, enabled = !deleting) {
                        Text(if (deleting) "删除中…" else "删除")
                    }
                }
            }
            Text(comment.content)
            if (comment.hasPendingWrites) {
                Text(
                    "等待同步",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReactionMembersSheet(
    reactions: List<IdeaReaction>,
    memberCount: Int,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "成员表态",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            ReactionValue.entries.forEach { value ->
                val members = reactions.filter { it.value == value }
                Text("${value.label} · ${members.size} 人", fontWeight = FontWeight.SemiBold)
                if (members.isEmpty()) {
                    Text("暂无", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    members.forEach { reaction ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InitialAvatar(
                                nickname = reaction.userSnapshot.nickname,
                                size = 32,
                                avatarUrl = reaction.userSnapshot.avatarUrl,
                            )
                            Text(
                                reaction.userSnapshot.nickname,
                                modifier = Modifier.padding(start = 10.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Text(
                "还有 ${(memberCount - reactions.size).coerceAtLeast(0)} 人未表态",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DetailStatus(
    message: String,
    padding: androidx.compose.foundation.layout.PaddingValues,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentAlignment = Alignment.Center,
    ) {
        Text(message)
    }
}

private fun formatDetailTime(instant: Instant?): String {
    if (instant == null) return "等待同步"
    return DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
