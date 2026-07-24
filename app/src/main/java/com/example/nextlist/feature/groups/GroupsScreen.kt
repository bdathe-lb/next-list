package com.example.nextlist.feature.groups

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.example.nextlist.core.designsystem.NextListTheme
import com.example.nextlist.data.firebase.FirebaseRuntimeStatus
import com.example.nextlist.feature.shell.M0PlaceholderScreen

@Composable
fun GroupsScreen(firebaseStatus: FirebaseRuntimeStatus) {
    val statusText = when (firebaseStatus) {
        FirebaseRuntimeStatus.EMULATOR -> "Firebase Emulator 已连接"
        FirebaseRuntimeStatus.PRODUCTION -> "Firebase 生产配置已加载"
        FirebaseRuntimeStatus.NOT_CONFIGURED -> "骨架模式：尚未加载 Firebase 配置"
    }
    val statusColor = when (firebaseStatus) {
        FirebaseRuntimeStatus.EMULATOR -> Color(0xFF2F6B45)
        FirebaseRuntimeStatus.PRODUCTION -> MaterialTheme.colorScheme.secondary
        FirebaseRuntimeStatus.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    M0PlaceholderScreen(
        eyebrow = "下次 · NextList",
        title = "我的小组",
        description = "把大家以后想一起做的事，先放在这里。",
        supportingContent = {
            Text(
                text = statusText,
                color = statusColor,
                style = MaterialTheme.typography.labelLarge,
            )
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun GroupsScreenPreview() {
    NextListTheme {
        GroupsScreen(firebaseStatus = FirebaseRuntimeStatus.EMULATOR)
    }
}
