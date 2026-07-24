package com.example.nextlist.feature.profile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.domain.model.NotificationPreferenceType
import com.example.nextlist.domain.model.NotificationPreferences

@Composable
fun NotificationSettingsRoute(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var permissionGranted by remember { mutableStateOf(context.notificationsAllowed()) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        permissionGranted = context.notificationsAllowed()
    }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionGranted = context.notificationsAllowed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    NotificationSettingsScreen(
        state = state,
        systemPermissionGranted = permissionGranted,
        canRequestPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
        onRequestPermission = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        },
        onOpenSystemSettings = {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                },
            )
        },
        onToggle = viewModel::toggle,
        onRetryLoad = viewModel::retryLoad,
        onRetrySave = viewModel::retrySave,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun NotificationSettingsScreen(
    state: NotificationSettingsUiState,
    systemPermissionGranted: Boolean,
    canRequestPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    onToggle: (NotificationPreferenceType, Boolean) -> Unit,
    onRetryLoad: () -> Unit,
    onRetrySave: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("通知设置") },
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
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!systemPermissionGranted) {
                OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "系统通知权限已关闭",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "应用内动态仍会正常记录。允许系统通知后，下面开启的类型才能推送。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (canRequestPermission) {
                                Button(onClick = onRequestPermission) {
                                    Text("允许通知")
                                }
                            }
                            TextButton(onClick = onOpenSystemSettings) {
                                Text("打开系统设置")
                            }
                        }
                    }
                }
            }
            if (state.isFromCache) {
                Text(
                    "当前离线，显示已缓存的设置",
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (val content = state.content) {
                LoadState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
                is LoadState.Empty -> Unit
                is LoadState.Error -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        content.kind.toUserMessage(),
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRetryLoad) { Text("重试") }
                }
                is LoadState.Content -> PreferenceCards(
                    preferences = content.data,
                    enabled = !state.isSaving,
                    onToggle = onToggle,
                )
            }
            state.message?.let {
                Text(
                    text = it,
                    color = if (state.canRetrySave) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (state.canRetrySave) {
                Button(onClick = onRetrySave) { Text("重试保存") }
            }
            Text(
                "关闭推送不会影响“动态”中的应用内记录。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PreferenceCards(
    preferences: NotificationPreferences,
    enabled: Boolean,
    onToggle: (NotificationPreferenceType, Boolean) -> Unit,
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            PreferenceRow(
                title = "小组邀请",
                supporting = "收到定向小组邀请",
                checked = preferences.groupInvite,
                enabled = enabled,
                onCheckedChange = {
                    onToggle(NotificationPreferenceType.GROUP_INVITE, it)
                },
            )
            PreferenceRow(
                title = "新安排活动",
                supporting = "首次安排或日期时间变化",
                checked = preferences.newSchedule,
                enabled = enabled,
                onCheckedChange = {
                    onToggle(NotificationPreferenceType.NEW_SCHEDULE, it)
                },
            )
            PreferenceRow(
                title = "活动即将开始",
                supporting = "符合条件时提前约 30 分钟",
                checked = preferences.upcomingReminder,
                enabled = enabled,
                onCheckedChange = {
                    onToggle(NotificationPreferenceType.UPCOMING_REMINDER, it)
                },
            )
            PreferenceRow(
                title = "想法收到评论",
                supporting = "自己创建的想法有新评论",
                checked = preferences.ideaComment,
                enabled = enabled,
                onCheckedChange = {
                    onToggle(NotificationPreferenceType.IDEA_COMMENT, it)
                },
            )
        }
    }
}

@Composable
private fun PreferenceRow(
    title: String,
    supporting: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                supporting,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.semantics {
                contentDescription = "$title，${if (checked) "已开启" else "已关闭"}"
            },
        )
    }
}

private fun android.content.Context.notificationsAllowed(): Boolean =
    NotificationManagerCompat.from(this).areNotificationsEnabled() &&
        (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            )
