package com.example.nextlist.feature.groups

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.nextlist.core.designsystem.NextListTheme
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.domain.model.InvitePreview
import com.example.nextlist.domain.model.MemberSnapshot
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class GroupScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyGroupsScreenShowsRequiredActionsAndCopy() {
        composeRule.setContent {
            NextListTheme {
                GroupsScreen(
                    state = LoadState.Empty("还没有小组"),
                    onCreateGroup = {},
                    onJoinGroup = {},
                    onOpenGroup = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("还没有小组").assertIsDisplayed()
        composeRule.onNodeWithText(
            "创建一个小组，或使用朋友发来的邀请码加入。",
        ).assertIsDisplayed()
        composeRule.onNodeWithText("创建小组").assertIsDisplayed()
        composeRule.onNodeWithText("输入邀请码").assertIsDisplayed()
    }

    @Test
    fun createGroupSubmissionDisablesThePrimaryAction() {
        var submitCount = 0
        composeRule.setContent {
            NextListTheme {
                CreateGroupScreen(
                    state = CreateGroupUiState(
                        name = "周末去哪",
                        isSubmitting = true,
                    ),
                    onNameChanged = {},
                    onSubmit = { submitCount += 1 },
                    onVerifyEmail = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("正在创建…")
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, submitCount)
    }

    @Test
    fun joinSubmissionDisablesDuplicateAcceptance() {
        var acceptCount = 0
        composeRule.setContent {
            NextListTheme {
                JoinGroupScreen(
                    state = JoinGroupUiState(
                        preview = LoadState.Content(
                            InvitePreview(
                                groupId = "group-1",
                                groupName = "周末去哪",
                                memberCount = 1,
                                members = listOf(MemberSnapshot("小林", null)),
                            ),
                        ),
                        isSubmitting = true,
                    ),
                    onRetry = {},
                    onAccept = { acceptCount += 1 },
                    onDecline = {},
                    onVerifyEmail = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("正在加入…")
            .assertIsNotEnabled()
            .performClick()
        assertEquals(0, acceptCount)
    }
}
