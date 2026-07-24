package com.example.nextlist.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.nextlist.domain.model.AccountSession
import com.example.nextlist.domain.model.AuthUser

@Composable
fun CompleteProfileRoute(
    user: AuthUser,
    modifier: Modifier = Modifier,
    viewModel: CompleteProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(user) { viewModel.onUserChanged(user) }
    CompleteProfileScreen(
        user = user,
        state = state,
        onNicknameChanged = viewModel::onNicknameChanged,
        onAvatarSelected = viewModel::onAvatarSelected,
        onSubmit = viewModel::submit,
        modifier = modifier,
    )
}

@Composable
fun CompleteProfileScreen(
    user: AuthUser,
    state: CompleteProfileUiState,
    onNicknameChanged: (String) -> Unit,
    onAvatarSelected: (String?) -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        onAvatarSelected(uri?.toString())
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "账号已创建",
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "完善你的资料",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "昵称必填，头像可以现在添加，也可以之后再设置。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(4.dp))
        AvatarEditor(
            model = state.selectedAvatarUri,
            nickname = state.nickname,
            hasAvatar = state.selectedAvatarUri != null,
            enabled = !state.isSubmitting,
            onChoose = {
                imagePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onRemove = { onAvatarSelected(null) },
        )
        OutlinedTextField(
            value = state.nickname,
            onValueChange = onNicknameChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("昵称") },
            singleLine = true,
            isError = state.nicknameError != null,
            supportingText = {
                Text(state.nicknameError ?: "2～20 个字符，保存时会去除首尾空格")
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            enabled = !state.isSubmitting,
        )
        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text("登录邮箱", style = MaterialTheme.typography.labelMedium)
                Text(user.email, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = "验证邮件已尝试发送；之后可在“我的”中查看状态并重新发送。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        StatusMessage(state.message, state.statusText)
        Button(
            onClick = onSubmit,
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
                Text(state.statusText ?: "正在保存…")
            } else {
                Text("保存并进入下次")
            }
        }
    }
}

@Composable
fun ProfileRoute(
    session: AccountSession.SignedIn,
    onEditProfile: () -> Unit,
    onNotificationSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(session.profile.avatarPath) {
        viewModel.loadAvatar(session.profile.avatarPath)
    }
    ProfileScreen(
        session = session,
        state = state,
        onEditProfile = onEditProfile,
        onNotificationSettings = onNotificationSettings,
        onSendVerification = viewModel::sendVerificationEmail,
        onRefreshVerification = { viewModel.refreshVerificationStatus(session) },
        onSignOut = viewModel::signOut,
        modifier = modifier,
    )
}

@Composable
fun ProfileScreen(
    session: AccountSession.SignedIn,
    state: ProfileUiState,
    onEditProfile: () -> Unit,
    onNotificationSettings: () -> Unit,
    onSendVerification: () -> Unit,
    onRefreshVerification: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "我的",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AvatarImage(
                    model = state.avatarUrl,
                    nickname = session.profile.nickname,
                    loading = state.avatarLoading,
                    size = 72,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = session.profile.nickname,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = session.user.email,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                TextButton(onClick = onEditProfile) {
                    Text("编辑")
                }
            }
        }

        VerificationCard(
            verified = session.user.emailVerified,
            sending = state.isSendingVerification,
            refreshing = state.isRefreshingVerification,
            onSend = onSendVerification,
            onRefresh = onRefreshVerification,
        )

        StatusMessage(state.message, null)

        OutlinedCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                SettingsRow(
                    title = "通知设置",
                    supporting = "管理四类推送",
                    onClick = onNotificationSettings,
                )
                HorizontalDivider()
                SettingsRow(title = "关于应用", supporting = "下次 · NextList 0.1.0")
            }
        }

        OutlinedButton(
            onClick = onSignOut,
            enabled = !state.isSigningOut,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
        ) {
            if (state.isSigningOut) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp),
                    strokeWidth = 2.dp,
                )
                Text("正在退出…")
            } else {
                Text("退出登录")
            }
        }
    }
}

@Composable
fun EditProfileRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) {
        if (state.completed) {
            viewModel.consumeCompletion()
            onBack()
        }
    }
    EditProfileScreen(
        state = state,
        onNicknameChanged = viewModel::onNicknameChanged,
        onAvatarSelected = viewModel::onAvatarSelected,
        onRemoveAvatar = viewModel::removeAvatar,
        onSave = viewModel::submit,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EditProfileScreen(
    state: EditProfileUiState,
    onNicknameChanged: (String) -> Unit,
    onAvatarSelected: (String?) -> Unit,
    onRemoveAvatar: () -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) onAvatarSelected(uri.toString())
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("编辑资料") },
                navigationIcon = {
                    TextButton(onClick = onBack, enabled = !state.isSubmitting) {
                        Text("返回")
                    }
                },
            )
        },
    ) { innerPadding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                val avatarModel = state.selectedAvatarUri
                    ?: state.avatarUrl.takeUnless { state.removeAvatar }
                AvatarEditor(
                    model = avatarModel,
                    nickname = state.nickname,
                    hasAvatar = avatarModel != null,
                    enabled = !state.isSubmitting,
                    onChoose = {
                        imagePicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                    onRemove = onRemoveAvatar,
                )
                OutlinedTextField(
                    value = state.nickname,
                    onValueChange = onNicknameChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("昵称") },
                    singleLine = true,
                    isError = state.nicknameError != null,
                    supportingText = {
                        Text(state.nicknameError ?: "2～20 个字符")
                    },
                    enabled = !state.isSubmitting,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave() }),
                )
                StatusMessage(state.message, state.statusText)
                Button(
                    onClick = onSave,
                    enabled = !state.isSubmitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    if (state.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(state.statusText ?: "正在保存…")
                    } else {
                        Text("保存")
                    }
                }
            }
        }
    }
}

@Composable
private fun AvatarEditor(
    model: Any?,
    nickname: String,
    hasAvatar: Boolean,
    enabled: Boolean,
    onChoose: () -> Unit,
    onRemove: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AvatarImage(model = model, nickname = nickname, loading = false, size = 104)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onChoose, enabled = enabled) {
                Text(if (hasAvatar) "更换头像" else "选择头像")
            }
            if (hasAvatar) {
                TextButton(onClick = onRemove, enabled = enabled) {
                    Text("移除")
                }
            }
        }
        Text(
            text = "会压缩为 WebP，上传文件不超过 2 MB",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AvatarImage(
    model: Any?,
    nickname: String,
    loading: Boolean,
    size: Int,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = "用户头像",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = nickname.trim().firstOrNull()?.toString()?.uppercase() ?: "下",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun VerificationCard(
    verified: Boolean,
    sending: Boolean,
    refreshing: Boolean,
    onSend: () -> Unit,
    onRefresh: () -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (verified) "邮箱已验证" else "邮箱尚未验证",
                color = if (verified) MaterialTheme.colorScheme.primary else {
                    MaterialTheme.colorScheme.secondary
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (verified) {
                    "账号邮箱验证状态正常。"
                } else {
                    "请打开验证邮件中的链接。没有收到时可重新发送。"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!verified) {
                    TextButton(onClick = onSend, enabled = !sending && !refreshing) {
                        Text(if (sending) "正在发送…" else "重新发送")
                    }
                }
                TextButton(onClick = onRefresh, enabled = !sending && !refreshing) {
                    Text(if (refreshing) "正在刷新…" else "刷新状态")
                }
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    supporting: String,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(
            supporting,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun StatusMessage(error: String?, status: String?) {
    when {
        error != null -> Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
        )
        status != null -> Text(
            text = status,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
