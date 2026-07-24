package com.example.nextlist.feature.profile

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.nextlist.core.designsystem.NextListTheme
import com.example.nextlist.feature.shell.M0PlaceholderScreen

@Composable
fun ProfileScreen() {
    M0PlaceholderScreen(
        eyebrow = "简单设置",
        title = "我的",
        description = "账号资料、通知设置和应用信息会集中在这里。",
    )
}

@Preview(showBackground = true)
@Composable
private fun ProfileScreenPreview() {
    NextListTheme {
        ProfileScreen()
    }
}
