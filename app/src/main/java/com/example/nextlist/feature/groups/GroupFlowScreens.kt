package com.example.nextlist.feature.groups

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.core.validation.GroupInputValidator
import com.example.nextlist.domain.model.Group
import com.example.nextlist.domain.model.GroupMember
import com.example.nextlist.domain.model.GroupRole
import com.example.nextlist.domain.model.InviteCredentials
import com.example.nextlist.domain.model.InvitePreview
import com.example.nextlist.domain.model.MemberSnapshot
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CreateGroupRoute(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onVerifyEmail: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CreateGroupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.createdGroupId) {
        state.createdGroupId?.let(onCreated)
    }
    CreateGroupScreen(
        state = state,
        onNameChanged = viewModel::onNameChanged,
        onSubmit = viewModel::submit,
        onVerifyEmail = onVerifyEmail,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CreateGroupScreen(
    state: CreateGroupUiState,
    onNameChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onVerifyEmail: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("创建小组") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !state.isSubmitting) {
                        Text("返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("给大家的共享清单起个名字。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSubmitting,
                label = { Text("小组名称") },
                singleLine = true,
                isError = state.nameError != null,
                supportingText = { Text(state.nameError ?: "2～30 个字符") },
            )
            state.message?.let { StatusText(it) }
            if (state.errorKind == AppError.EMAIL_NOT_VERIFIED) {
                TextButton(onClick = onVerifyEmail) {
                    Text("前往“我的”验证邮箱")
                }
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
                    Text("正在创建…")
                } else {
                    Text("创建小组")
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JoinCodeScreen(
    onBack: () -> Unit,
    onContinue: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var code by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("加入小组") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("输入朋友分享的 8 位邀请码。")
            OutlinedTextField(
                value = code,
                onValueChange = {
                    code = it.take(12)
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("邀请码") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "不区分大小写，可包含空格或短横线") },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                ),
            )
            Button(
                onClick = {
                    error = GroupInputValidator.inviteCodeError(code)
                    if (error == null) {
                        onContinue(GroupInputValidator.normalizedInviteCode(code))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("查看邀请")
            }
        }
    }
}

@Composable
fun JoinGroupRoute(
    onBack: () -> Unit,
    onJoined: (String) -> Unit,
    onVerifyEmail: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: JoinGroupViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.joinedGroupId) {
        state.joinedGroupId?.let(onJoined)
    }
    LaunchedEffect(state.declined) {
        if (state.declined) onBack()
    }
    JoinGroupScreen(
        state = state,
        onRetry = viewModel::loadPreview,
        onAccept = viewModel::accept,
        onDecline = viewModel::decline,
        onVerifyEmail = onVerifyEmail,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun JoinGroupScreen(
    state: JoinGroupUiState,
    onRetry: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onVerifyEmail: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("确认加入") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !state.isSubmitting) {
                        Text("返回")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            when (val previewState = state.preview) {
                LoadState.Loading -> CircularProgressIndicator()
                is LoadState.Empty -> Text("邀请无效")
                is LoadState.Error -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(previewState.kind.toUserMessage())
                    if (previewState.canRetry) {
                        OutlinedButton(onClick = onRetry) { Text("重试") }
                    }
                }
                is LoadState.Content -> InvitePreviewContent(
                    preview = previewState.data,
                    submitting = state.isSubmitting,
                    message = state.message,
                    onAccept = onAccept,
                    onDecline = if (state.isDirect) onDecline else null,
                    showVerifyEmail = state.errorKind == AppError.EMAIL_NOT_VERIFIED,
                    onVerifyEmail = onVerifyEmail,
                )
            }
        }
    }
}

@Composable
private fun InvitePreviewContent(
    preview: InvitePreview,
    submitting: Boolean,
    message: String?,
    onAccept: () -> Unit,
    onDecline: (() -> Unit)?,
    showVerifyEmail: Boolean,
    onVerifyEmail: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = preview.groupName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        MemberAvatarRow(preview.members, preview.memberCount)
        Text("${preview.memberCount} 位成员")
        message?.let { StatusText(it) }
        if (showVerifyEmail) {
            TextButton(onClick = onVerifyEmail, enabled = !submitting) {
                Text("前往“我的”验证邮箱")
            }
        }
        Button(
            onClick = onAccept,
            enabled = !submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (submitting) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text("正在加入…")
            } else {
                Text("加入小组")
            }
        }
        if (onDecline != null) {
            TextButton(onClick = onDecline, enabled = !submitting) {
                Text("拒绝邀请")
            }
        }
    }
}

@Composable
fun GroupDetailRoute(
    onBack: () -> Unit,
    onMembers: (String) -> Unit,
    onInvite: (String) -> Unit,
    onSettings: (String) -> Unit,
    onAccessLost: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.accessLostMessage) {
        state.accessLostMessage?.let(onAccessLost)
    }
    GroupDetailScreen(
        state = state,
        onBack = onBack,
        onMembers = onMembers,
        onInvite = onInvite,
        onSettings = onSettings,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GroupDetailScreen(
    state: GroupDetailUiState,
    onBack: () -> Unit,
    onMembers: (String) -> Unit,
    onInvite: (String) -> Unit,
    onSettings: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val group = (state.group as? LoadState.Content)?.data
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(group?.name ?: "小组") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
                actions = {
                    if (group != null && group.adminId == state.currentUserId) {
                        TextButton(onClick = { onInvite(group.id) }) { Text("邀请") }
                    }
                    if (group != null) {
                        TextButton(onClick = { onSettings(group.id) }) { Text("设置") }
                    }
                },
            )
        },
    ) { padding ->
        when (val groupState = state.group) {
            LoadState.Loading -> CenteredProgress(Modifier.padding(padding))
            is LoadState.Empty -> ErrorState("小组不存在")
            is LoadState.Error -> ErrorState(groupState.kind.toUserMessage())
            is LoadState.Content -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MemberAvatarRow(
                        state.members.take(5).map {
                            MemberSnapshot(it.nickname, it.avatarPath, it.avatarUrl)
                        },
                        groupState.data.memberCount,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { onMembers(groupState.data.id) }) {
                        Text("${groupState.data.memberCount} 位成员")
                    }
                }
                TabRow(selectedTabIndex = selectedTab) {
                    listOf("想法", "已安排", "已完成").forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                        )
                    }
                }
                val emptyTexts = listOf(
                    "还没有灵感，记下大家下次想做的事吧。",
                    "还没有安排，从想法里挑一个定下来。",
                    "完成的活动会留在这里。",
                )
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(emptyTexts[selectedTab])
                    Text(
                        "想法与活动能力将在 M3/M4 开放",
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun MembersRoute(
    onBack: () -> Unit,
    onExitGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MembersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exitGroup) {
        if (state.exitGroup) onExitGroup(state.message ?: "小组成员状态已变化")
    }
    MembersScreen(
        state = state,
        onRemove = viewModel::remove,
        onTransfer = viewModel::transfer,
        onLeave = viewModel::leave,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MembersScreen(
    state: MembersUiState,
    onRemove: (String) -> Unit,
    onTransfer: (String) -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmation by remember { mutableStateOf<Pair<String, GroupMember>?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val isAdmin = state.group?.adminId == state.currentUserId
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("小组成员") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        if (state.loading) {
            CenteredProgress(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    state.message?.let { StatusText(it) }
                }
                items(state.members, key = GroupMember::userId) { member ->
                    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            InitialAvatar(member.nickname, avatarUrl = member.avatarUrl)
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 12.dp),
                            ) {
                                Text(member.nickname, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (member.role == GroupRole.ADMIN) "管理员" else "成员",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (
                                isAdmin &&
                                member.userId != state.currentUserId &&
                                member.role == GroupRole.MEMBER
                            ) {
                                TextButton(
                                    onClick = { confirmation = "transfer" to member },
                                    enabled = state.submittingUserId == null,
                                ) { Text("转让") }
                                TextButton(
                                    onClick = { confirmation = "remove" to member },
                                    enabled = state.submittingUserId == null,
                                ) { Text("移除") }
                            }
                            if (state.submittingUserId == member.userId) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            }
                        }
                    }
                }
                if (!isAdmin) {
                    item {
                        OutlinedButton(
                            onClick = { confirmLeave = true },
                            enabled = !state.isLeaving,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.isLeaving) "正在退出…" else "退出小组")
                        }
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    confirmation?.let { (action, member) ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (action == "remove") "移除成员？" else "转让管理员？") },
            text = {
                Text(
                    if (action == "remove") {
                        "移除 ${member.nickname} 后，对方会立即失去小组访问权限。"
                    } else {
                        "管理员身份将转让给 ${member.nickname}，你会变为普通成员。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmation = null
                        if (action == "remove") onRemove(member.userId) else {
                            onTransfer(member.userId)
                        }
                    },
                ) { Text("确认") }
            },
            dismissButton = {
                TextButton(onClick = { confirmation = null }) { Text("取消") }
            },
        )
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("退出小组？") },
            text = { Text("退出后将不再看到小组内容，历史成员记录会保留。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onLeave()
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("取消") }
            },
        )
    }
}

@Composable
fun InviteRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InviteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    InviteScreen(
        state = state,
        onRetry = viewModel::load,
        onRotate = viewModel::rotate,
        onEmailChanged = viewModel::onEmailChanged,
        onSendDirect = viewModel::sendDirect,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun InviteScreen(
    state: InviteUiState,
    onRetry: () -> Unit,
    onRotate: () -> Unit,
    onEmailChanged: (String) -> Unit,
    onSendDirect: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("邀请成员") },
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
            when (val credentialsState = state.credentials) {
                LoadState.Loading -> CenteredProgress()
                is LoadState.Empty -> Text("当前没有邀请")
                is LoadState.Error -> {
                    StatusText(credentialsState.kind.toUserMessage())
                    OutlinedButton(onClick = onRetry) { Text("重试") }
                }
                is LoadState.Content -> InviteCredentialsCard(
                    credentials = credentialsState.data,
                    isRotating = state.isRotating,
                    onCopy = { label, value -> copyText(context, label, value) },
                    onShare = { shareInvite(context, credentialsState.data.token) },
                    onRotate = onRotate,
                )
            }
            HorizontalDivider()
            Text("定向邀请", style = MaterialTheme.typography.titleMedium)
            Text(
                "输入已注册用户的邮箱。我们不会透露该邮箱是否注册。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = state.email,
                onValueChange = onEmailChanged,
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isSendingDirect,
                label = { Text("对方邮箱") },
                singleLine = true,
            )
            Button(
                onClick = onSendDirect,
                enabled = !state.isSendingDirect && state.email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSendingDirect) "正在发送…" else "发送邀请")
            }
            state.message?.let { StatusText(it) }
        }
    }
}

@Composable
private fun InviteCredentialsCard(
    credentials: InviteCredentials,
    isRotating: Boolean,
    onCopy: (String, String) -> Unit,
    onShare: () -> Unit,
    onRotate: () -> Unit,
) {
    val link = "https://nextlist.example/invite/${credentials.token}"
    val expires = DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(credentials.expiresAt)
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("邀请码", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                credentials.code,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("有效期至 $expires")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onCopy("邀请码", credentials.code) }) {
                    Text("复制邀请码")
                }
                Button(onClick = onShare) { Text("分享链接") }
            }
            TextButton(
                onClick = onRotate,
                enabled = !isRotating,
            ) {
                Text(if (isRotating) "正在重新生成…" else "重新生成邀请")
            }
            Text(
                link.replace(credentials.token, "••••••••"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
fun GroupSettingsRoute(
    onBack: () -> Unit,
    onExitGroup: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.exitGroup) {
        if (state.exitGroup) onExitGroup(state.message ?: "小组状态已变化")
    }
    GroupSettingsScreen(
        state = state,
        isAdmin = viewModel.isCurrentUserAdmin(),
        onNameChanged = viewModel::onNameChanged,
        onConfirmationChanged = viewModel::onConfirmationChanged,
        onRename = viewModel::rename,
        onDissolve = viewModel::dissolve,
        onLeave = viewModel::leave,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun GroupSettingsScreen(
    state: GroupSettingsUiState,
    isAdmin: Boolean,
    onNameChanged: (String) -> Unit,
    onConfirmationChanged: (String) -> Unit,
    onRename: () -> Unit,
    onDissolve: () -> Unit,
    onLeave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var confirmLeave by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("小组设置") },
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
            if (state.group == null) {
                CenteredProgress()
            } else if (isAdmin) {
                Text("修改名称", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isRenaming && !state.isDissolving,
                    label = { Text("小组名称") },
                    isError = state.nameError != null,
                    supportingText = { Text(state.nameError ?: "2～30 个字符") },
                )
                Button(
                    onClick = onRename,
                    enabled = !state.isRenaming && !state.isDissolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isRenaming) "正在保存…" else "保存名称")
                }
                HorizontalDivider()
                Text(
                    "解散小组",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text("解散后所有成员会立即失去访问权限。请输入小组名称确认。")
                OutlinedTextField(
                    value = state.confirmationName,
                    onValueChange = onConfirmationChanged,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isDissolving,
                    label = { Text(state.group.name) },
                    singleLine = true,
                )
                OutlinedButton(
                    onClick = onDissolve,
                    enabled = !state.isDissolving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isDissolving) "正在解散…" else "解散小组")
                }
            } else {
                Text("你是普通成员，可以退出这个小组。")
                OutlinedButton(
                    onClick = { confirmLeave = true },
                    enabled = !state.isLeaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isLeaving) "正在退出…" else "退出小组")
                }
            }
            state.message?.let { StatusText(it) }
        }
    }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("退出小组？") },
            text = { Text("退出后将立即失去小组访问权限。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmLeave = false
                        onLeave()
                    },
                ) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun StatusText(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun CenteredProgress(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun shareInvite(context: Context, token: String) {
    val link = "https://nextlist.example/invite/$token"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "邀请你加入「下次」小组：$link")
    }
    context.startActivity(Intent.createChooser(intent, "分享邀请"))
}
