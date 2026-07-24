package com.example.nextlist.feature.activityfeed

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.nextlist.core.designsystem.NextListTheme
import com.example.nextlist.feature.shell.M0PlaceholderScreen

@Composable
fun ActivityFeedScreen() {
    M0PlaceholderScreen(
        eyebrow = "重要变化，不打扰",
        title = "动态",
        description = "新想法、活动安排、评论与完成记录会出现在这里。",
    )
}

@Preview(showBackground = true)
@Composable
private fun ActivityFeedScreenPreview() {
    NextListTheme {
        ActivityFeedScreen()
    }
}
