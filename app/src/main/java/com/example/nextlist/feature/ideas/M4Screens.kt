package com.example.nextlist.feature.ideas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.nextlist.domain.model.GroupMember
import com.example.nextlist.domain.model.Idea
import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaRsvp
import com.example.nextlist.domain.model.IdeaStatus
import com.example.nextlist.domain.model.RsvpValue
import com.example.nextlist.feature.groups.InitialAvatar
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleFormRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScheduleFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    ScheduleFormScreen(
        state = state,
        onDateChanged = viewModel::onDateChanged,
        onTimeChanged = viewModel::onTimeChanged,
        onTimezoneChanged = viewModel::onTimezoneChanged,
        onMeetingPointChanged = viewModel::onMeetingPointChanged,
        onNoteChanged = viewModel::onNoteChanged,
        onSubmit = viewModel::submit,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ScheduleFormScreen(
    state: ScheduleFormUiState,
    onDateChanged: (String) -> Unit,
    onTimeChanged: (String) -> Unit,
    onTimezoneChanged: (String) -> Unit,
    onMeetingPointChanged: (String) -> Unit,
    onNoteChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "修改安排" else "安排活动") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !state.isSubmitting) {
                        Text("返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            CenteredM4Progress(Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.ideaTitle.isNotBlank()) {
                    Text(
                        state.ideaTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedTextField(
                    value = state.date,
                    onValueChange = onDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("日期") },
                    placeholder = { Text("2026-07-26") },
                    singleLine = true,
                    isError = state.dateError != null,
                    supportingText = { Text(state.dateError ?: "必填，YYYY-MM-DD；允许过去日期") },
                )
                OutlinedTextField(
                    value = state.time,
                    onValueChange = onTimeChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("时间") },
                    placeholder = { Text("14:30") },
                    singleLine = true,
                    isError = state.timeError != null,
                    supportingText = { Text(state.timeError ?: "必填，24 小时制 HH:mm") },
                )
                OutlinedTextField(
                    value = state.timezone,
                    onValueChange = onTimezoneChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("时区") },
                    singleLine = true,
                    isError = state.timezoneError != null,
                    supportingText = {
                        Text(state.timezoneError ?: "IANA 时区，例如 Asia/Shanghai")
                    },
                )
                OutlinedTextField(
                    value = state.meetingPoint,
                    onValueChange = onMeetingPointChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("集合地点（可选）") },
                    isError = state.meetingPointError != null,
                    supportingText = {
                        Text(state.meetingPointError ?: "最多 200 个字符")
                    },
                )
                OutlinedTextField(
                    value = state.note,
                    onValueChange = onNoteChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("安排备注（可选）") },
                    minLines = 3,
                    isError = state.noteError != null,
                    supportingText = { Text(state.noteError ?: "最多 500 个字符") },
                )
                if (state.conflict) {
                    Text(
                        "检测到安排冲突",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onSubmit,
                    enabled = !state.isSubmitting && state.ideaTitle.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (state.isSubmitting) "正在保存安排…" else "保存安排")
                }
            }
        }
    }
}

@Composable
fun CompletionFormRoute(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompletionFormViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }
    CompletionFormScreen(
        state = state,
        onDateChanged = viewModel::onDateChanged,
        onTimezoneChanged = viewModel::onTimezoneChanged,
        onReviewChanged = viewModel::onReviewChanged,
        onRatingChanged = viewModel::onRatingChanged,
        onImageSelected = viewModel::onImageSelected,
        onRemoveImage = viewModel::removeImage,
        onSubmit = viewModel::submit,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CompletionFormScreen(
    state: CompletionFormUiState,
    onDateChanged: (String) -> Unit,
    onTimezoneChanged: (String) -> Unit,
    onReviewChanged: (String) -> Unit,
    onRatingChanged: (Int?) -> Unit,
    onImageSelected: (String) -> Unit,
    onRemoveImage: () -> Unit,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.toString()?.let(onImageSelected)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "编辑完成记录" else "标记完成") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !state.isSubmitting) {
                        Text("返回")
                    }
                },
            )
        },
    ) { padding ->
        if (state.isLoading) {
            CenteredM4Progress(Modifier.padding(padding))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (state.ideaTitle.isNotBlank()) {
                    Text(
                        state.ideaTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                OutlinedTextField(
                    value = state.completedOn,
                    onValueChange = onDateChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("完成日期") },
                    singleLine = true,
                    isError = state.dateError != null,
                    supportingText = { Text(state.dateError ?: "必填，YYYY-MM-DD") },
                )
                OutlinedTextField(
                    value = state.timezone,
                    onValueChange = onTimezoneChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("时区") },
                    singleLine = true,
                    isError = state.timezoneError != null,
                    supportingText = {
                        Text(state.timezoneError ?: "IANA 时区，例如 Asia/Shanghai")
                    },
                )
                Text("完成照片（可选，最多一张）")
                when {
                    state.selectedImageUri != null -> AsyncImage(
                        model = state.selectedImageUri,
                        contentDescription = "待上传的完成照片",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    )
                    state.existingPhoto != null -> Text(
                        "已上传照片 · ${state.existingPhoto.width} × ${state.existingPhoto.height}",
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
                        Text(if (state.existingPhoto == null) "选择照片" else "替换照片")
                    }
                    if (state.selectedImageUri != null || state.existingPhoto != null) {
                        TextButton(onClick = onRemoveImage, enabled = !state.isSubmitting) {
                            Text("移除照片")
                        }
                    }
                }
                OutlinedTextField(
                    value = state.review,
                    onValueChange = onReviewChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isSubmitting,
                    label = { Text("简短评价（可选）") },
                    minLines = 3,
                    isError = state.reviewError != null,
                    supportingText = { Text(state.reviewError ?: "最多 500 个字符") },
                )
                Text("评分（可选）")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = state.rating == null,
                            onClick = { onRatingChanged(null) },
                            enabled = !state.isSubmitting,
                            label = { Text("不评分") },
                        )
                    }
                    items((1..5).toList()) { rating ->
                        FilterChip(
                            selected = state.rating == rating,
                            onClick = { onRatingChanged(rating) },
                            enabled = !state.isSubmitting,
                            label = { Text("$rating 星") },
                        )
                    }
                }
                state.ratingError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = onSubmit,
                    enabled = !state.isSubmitting && state.ideaTitle.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(state.submitStage ?: "保存完成记录")
                }
            }
        }
    }
}

@Composable
fun RandomDecisionRoute(
    onBack: () -> Unit,
    onAddIdea: () -> Unit,
    onArrange: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: RandomDecisionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.arrangeTargetId) {
        state.arrangeTargetId?.let { ideaId ->
            viewModel.consumeArrangeTarget()
            onArrange(ideaId)
        }
    }
    RandomDecisionScreen(
        state = state,
        onSelectCategory = viewModel::selectCategory,
        onSetMinimumWant = viewModel::setMinimumWant,
        onDraw = viewModel::draw,
        onDrawAnother = viewModel::drawAnother,
        onArrange = viewModel::arrangeResult,
        onAddIdea = onAddIdea,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RandomDecisionScreen(
    state: RandomDecisionUiState,
    onSelectCategory: (IdeaCategory?) -> Unit,
    onSetMinimumWant: (Int) -> Unit,
    onDraw: () -> Unit,
    onDrawAnother: () -> Unit,
    onArrange: () -> Unit,
    onAddIdea: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("随机决定") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("分类", style = MaterialTheme.typography.titleMedium)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = state.category == null,
                        onClick = { onSelectCategory(null) },
                        label = { Text("全部") },
                    )
                }
                items(IdeaCategory.entries, key = IdeaCategory::wireValue) { category ->
                    FilterChip(
                        selected = state.category == category,
                        onClick = { onSelectCategory(category) },
                        label = { Text("${category.glyph} ${category.label}") },
                    )
                }
            }
            Text(
                "最少“想参加”人数：${state.minimumWant}",
                style = MaterialTheme.typography.titleMedium,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items((0..state.memberCount).toList()) { count ->
                    FilterChip(
                        selected = state.minimumWant == count,
                        onClick = { onSetMinimumWant(count) },
                        label = { Text(count.toString()) },
                    )
                }
            }
            Button(
                onClick = onDraw,
                enabled = !state.isLoading && !state.isCheckingResult,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isLoading) "正在寻找候选…" else "随机选一个")
            }
            if (state.isLoading) {
                CircularProgressIndicator()
            }
            val result = state.result
            if (result != null) {
                RandomResultCard(result)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = onDrawAnother,
                        enabled = !state.isCheckingResult,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("换一个")
                    }
                    Button(
                        onClick = onArrange,
                        enabled = !state.isCheckingResult,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (state.isCheckingResult) "正在确认…" else "安排这个")
                    }
                }
            } else if (state.hasDrawn && !state.isLoading) {
                Text(
                    "没有符合条件的想法",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("调整上面的分类或人数条件，或者先添加一个想法。")
                OutlinedButton(onClick = onAddIdea) { Text("添加想法") }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun RandomResultCard(idea: Idea) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                "这次就选它",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                idea.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text("${idea.category.glyph} ${idea.category.label}")
            Text("${idea.reactionCounts.want} 人想参加")
            if (idea.status == IdeaStatus.SCHEDULED) {
                Text("这个想法已经安排，将打开现有安排")
            }
        }
    }
}

@Composable
internal fun ScheduleAndRsvpSection(
    idea: Idea,
    rsvps: List<IdeaRsvp>,
    members: List<GroupMember>,
    currentUserId: String?,
    isSubmitting: Boolean,
    onSetRsvp: (RsvpValue) -> Unit,
    onEditSchedule: () -> Unit,
    onComplete: () -> Unit,
) {
    val schedule = idea.schedule ?: return
    val current = rsvps.firstOrNull { it.userId == currentUserId }
    val stale = current != null && current.scheduleRevision < schedule.revision
    var showMembers by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "安排",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(formatScheduleTime(schedule.startAt, schedule.timezone))
        schedule.meetingPoint?.let { Text("集合地点：$it") }
        schedule.note?.let { Text("备注：$it") }
        Text(
            "由 ${schedule.schedulerSnapshot.nickname} 安排 · 第 ${schedule.revision} 版",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        if (idea.status == IdeaStatus.SCHEDULED) {
            if (stale) {
                Text(
                    "安排已变化，请确认",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                current?.let { "你的参加状态：${it.value.label}" } ?: "选择你的参加状态",
                fontWeight = FontWeight.SemiBold,
            )
            RsvpValue.entries.forEach { value ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(
                        selected = current?.value == value,
                        onClick = { onSetRsvp(value) },
                        enabled = !isSubmitting,
                        label = { Text(value.label) },
                    )
                    TextButton(onClick = { showMembers = true }) {
                        Text("${rsvps.count { it.value == value }} 人")
                    }
                }
            }
            if (rsvps.any { it.hasPendingWrites }) {
                Text("参加状态等待同步", color = MaterialTheme.colorScheme.secondary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEditSchedule) { Text("修改安排") }
                Button(onClick = onComplete) { Text("标记完成") }
            }
        } else {
            Text("最终参加状态", fontWeight = FontWeight.SemiBold)
            RsvpValue.entries.forEach { value ->
                Text("${value.label} · ${rsvps.count { it.value == value }} 人")
            }
        }
        TextButton(onClick = { showMembers = true }) {
            Text("查看成员明细 · ${(members.size - rsvps.size).coerceAtLeast(0)} 人未选择")
        }
    }
    if (showMembers) {
        RsvpMembersSheet(
            rsvps = rsvps,
            members = members,
            scheduleRevision = schedule.revision,
            onDismiss = { showMembers = false },
        )
    }
}

@Composable
internal fun CompletionSection(
    idea: Idea,
    members: List<GroupMember>,
    photoUrl: String?,
    onEdit: () -> Unit,
) {
    val completion = idea.completion ?: return
    val updater = members.firstOrNull { it.userId == completion.updatedBy }?.nickname
        ?: completion.updatedBy
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "完成记录",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text("完成日期：${completion.completedOn}")
        if (photoUrl != null) {
            AsyncImage(
                model = photoUrl,
                contentDescription = "${idea.title} 的完成照片",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
            )
        }
        completion.rating?.let { Text("评分：${"★".repeat(it)}") }
        completion.review?.let { Text(it) }
        Text(
            "最后修改：$updater · ${formatInstant(completion.updatedAt)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedButton(onClick = onEdit) { Text("编辑完成记录") }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RsvpMembersSheet(
    rsvps: List<IdeaRsvp>,
    members: List<GroupMember>,
    scheduleRevision: Int,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("参加成员", style = MaterialTheme.typography.titleLarge)
            RsvpValue.entries.forEach { value ->
                val grouped = rsvps.filter { it.value == value }
                Text("${value.label} · ${grouped.size} 人", fontWeight = FontWeight.SemiBold)
                grouped.forEach { rsvp ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        InitialAvatar(
                            nickname = rsvp.userSnapshot.nickname,
                            avatarUrl = rsvp.userSnapshot.avatarUrl,
                            size = 32,
                        )
                        Text(
                            rsvp.userSnapshot.nickname,
                            modifier = Modifier.padding(start = 10.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (rsvp.scheduleRevision < scheduleRevision) {
                            Spacer(Modifier.weight(1f))
                            Text(
                                "待确认",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                }
            }
            Text(
                "还有 ${(members.size - rsvps.size).coerceAtLeast(0)} 人未选择",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CenteredM4Progress(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

private fun formatScheduleTime(instant: Instant, timezone: String): String {
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    return DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm z")
        .withZone(zone)
        .format(instant)
}

private fun formatInstant(instant: Instant?): String {
    if (instant == null) return "等待同步"
    return DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(instant)
}
