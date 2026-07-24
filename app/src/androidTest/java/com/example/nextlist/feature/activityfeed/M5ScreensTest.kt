package com.example.nextlist.feature.activityfeed

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.example.nextlist.core.designsystem.NextListTheme
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.domain.model.ActivityFeedItem
import com.example.nextlist.domain.model.ActivityFeedType
import com.example.nextlist.domain.model.MemberSnapshot
import com.example.nextlist.domain.model.NotificationPreferences
import com.example.nextlist.feature.profile.NotificationSettingsScreen
import com.example.nextlist.feature.profile.NotificationSettingsUiState
import java.time.Instant
import org.junit.Rule
import org.junit.Test

class M5ScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun feedShowsUnreadDescriptionGroupAndPaginationAction() {
        composeRule.setContent {
            NextListTheme {
                ActivityFeedScreen(
                    state = ActivityFeedUiState(
                        content = LoadState.Content(listOf(feedItem())),
                        unreadCount = 1,
                        canLoadMore = true,
                    ),
                    onRefresh = {},
                    onRetry = {},
                    onLoadMore = {},
                    onMarkAllRead = {},
                    onOpen = {},
                )
            }
        }

        composeRule.onNodeWithText("小林添加了新想法「去植物园」")
            .assertIsDisplayed()
        composeRule.onNodeWithText("周末去哪").assertIsDisplayed()
        composeRule.onNodeWithText("全部标记已读").assertHasClickAction()
        composeRule.onNodeWithText("加载更早动态").assertHasClickAction()
        composeRule.onNodeWithContentDescription("未读").assertIsDisplayed()
    }

    @Test
    fun feedEmptyAndOfflineStatesRemainUnderstandable() {
        composeRule.setContent {
            NextListTheme {
                ActivityFeedScreen(
                    state = ActivityFeedUiState(
                        content = LoadState.Empty("empty"),
                        isFromCache = true,
                    ),
                    onRefresh = {},
                    onRetry = {},
                    onLoadMore = {},
                    onMarkAllRead = {},
                    onOpen = {},
                )
            }
        }

        composeRule.onNodeWithText("还没有动态").assertIsDisplayed()
        composeRule.onNodeWithText("当前显示离线缓存，联网后会自动更新")
            .assertIsDisplayed()
    }

    @Test
    fun notificationSettingsExplainDeniedPermissionAndExposeFourSwitches() {
        composeRule.setContent {
            NextListTheme {
                NotificationSettingsScreen(
                    state = NotificationSettingsUiState(
                        content = LoadState.Content(NotificationPreferences()),
                    ),
                    systemPermissionGranted = false,
                    canRequestPermission = true,
                    onRequestPermission = {},
                    onOpenSystemSettings = {},
                    onToggle = { _, _ -> },
                    onRetryLoad = {},
                    onRetrySave = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("系统通知权限已关闭").assertIsDisplayed()
        composeRule.onNodeWithText("允许通知").assertHasClickAction()
        composeRule.onNodeWithText("打开系统设置").assertHasClickAction()
        composeRule.onNodeWithContentDescription("小组邀请，已开启").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("新安排活动，已开启").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("活动即将开始，已开启").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("想法收到评论，已开启").assertIsDisplayed()
    }

    @Test
    fun grantedNotificationPermissionHidesRecoveryCard() {
        composeRule.setContent {
            NextListTheme {
                NotificationSettingsScreen(
                    state = NotificationSettingsUiState(
                        content = LoadState.Content(NotificationPreferences()),
                    ),
                    systemPermissionGranted = true,
                    canRequestPermission = true,
                    onRequestPermission = {},
                    onOpenSystemSettings = {},
                    onToggle = { _, _ -> },
                    onRetryLoad = {},
                    onRetrySave = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("系统通知权限已关闭").assertDoesNotExist()
        composeRule.onNodeWithText("关闭推送不会影响“动态”中的应用内记录。")
            .assertIsDisplayed()
    }

    @Test
    fun notificationSettingsRemainReachableAtLargeFontScale() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 2f),
            ) {
                NextListTheme {
                    NotificationSettingsScreen(
                        state = NotificationSettingsUiState(
                            content = LoadState.Content(NotificationPreferences()),
                        ),
                        systemPermissionGranted = false,
                        canRequestPermission = true,
                        onRequestPermission = {},
                        onOpenSystemSettings = {},
                        onToggle = { _, _ -> },
                        onRetryLoad = {},
                        onRetrySave = {},
                        onBack = {},
                    )
                }
            }
        }

        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText("关闭推送不会影响“动态”中的应用内记录。"),
        )
        composeRule.onNodeWithText("关闭推送不会影响“动态”中的应用内记录。")
            .assertIsDisplayed()
    }
}

private fun feedItem() = ActivityFeedItem(
    id = "feed-1",
    type = ActivityFeedType.IDEA_CREATED,
    groupId = "group-1",
    groupName = "周末去哪",
    ideaId = "idea-1",
    ideaTitle = "去植物园",
    invitationId = null,
    actorId = "alice",
    actor = MemberSnapshot("小林", null),
    createdAt = Instant.parse("2026-07-24T02:00:00Z"),
    readAt = null,
)
