package com.example.nextlist.feature.ideas

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.nextlist.core.designsystem.NextListTheme
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.domain.model.Group
import com.example.nextlist.domain.model.GroupMember
import com.example.nextlist.domain.model.GroupRole
import com.example.nextlist.domain.model.GroupStatus
import com.example.nextlist.domain.model.Idea
import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaStatus
import com.example.nextlist.domain.model.MemberSnapshot
import com.example.nextlist.domain.model.ReactionCounts
import com.example.nextlist.feature.groups.GroupDetailScreen
import com.example.nextlist.feature.groups.GroupDetailUiState
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class IdeaScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun groupDetailShowsThreeTabsAndRequiredIdeaEmptyState() {
        composeRule.setContent {
            NextListTheme {
                GroupDetailScreen(
                    state = groupDetailState(),
                    onBack = {},
                    onMembers = {},
                    onInvite = {},
                    onSettings = {},
                    onAddIdea = {},
                    onOpenIdea = { _, _ -> },
                    onRandom = {},
                    onSelectStatus = {},
                    onSelectCategory = {},
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("想法").assertIsDisplayed()
        composeRule.onNodeWithText("已安排").assertIsDisplayed()
        composeRule.onNodeWithText("已完成").assertIsDisplayed()
        composeRule.onNodeWithText("还没有灵感，记下大家下次想做的事吧。")
            .assertIsDisplayed()
    }

    @Test
    fun ideaCategoryFilterExposesAllCategoriesAndSelection() {
        var selected: IdeaCategory? = null
        composeRule.setContent {
            NextListTheme {
                GroupDetailScreen(
                    state = groupDetailState(),
                    onBack = {},
                    onMembers = {},
                    onInvite = {},
                    onSettings = {},
                    onAddIdea = {},
                    onOpenIdea = { _, _ -> },
                    onRandom = {},
                    onSelectStatus = {},
                    onSelectCategory = { selected = it },
                    onRefresh = {},
                )
            }
        }

        composeRule.onNodeWithText("全部").assertIsDisplayed()
        composeRule.onNodeWithText("影 电影").performClick()
        assertEquals(IdeaCategory.MOVIE, selected)
    }

    @Test
    fun newIdeaFormShowsDefaultCategoryAndValidationMessages() {
        composeRule.setContent {
            NextListTheme {
                IdeaFormScreen(
                    state = IdeaFormUiState(
                        titleError = "请输入想法标题",
                    ),
                    onTitleChanged = {},
                    onCategoryChanged = {},
                    onNoteChanged = {},
                    onLocationOrLinkChanged = {},
                    onImageSelected = {},
                    onRemoveImage = {},
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("分类：想 其他").assertIsDisplayed()
        composeRule.onNodeWithText("请输入想法标题").assertIsDisplayed()
        composeRule.onNodeWithText("备注（可选）").assertIsDisplayed()
        composeRule.onNodeWithText("地址或链接（可选）").assertIsDisplayed()
    }

    @Test
    fun ideaFormDisablesDuplicateSaveDuringSubmission() {
        composeRule.setContent {
            NextListTheme {
                IdeaFormScreen(
                    state = IdeaFormUiState(
                        title = "去植物园",
                        isSubmitting = true,
                        submitStage = "正在保存…",
                    ),
                    onTitleChanged = {},
                    onCategoryChanged = {},
                    onNoteChanged = {},
                    onLocationOrLinkChanged = {},
                    onImageSelected = {},
                    onRemoveImage = {},
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("正在保存…").assertIsNotEnabled()
    }

    @Test
    fun ideaDetailShowsReactionChoicesAndCommentInput() {
        composeRule.setContent {
            NextListTheme {
                IdeaDetailScreen(
                    state = ideaDetailState(),
                    canEditIdea = false,
                    canDeleteIdea = false,
                    canDeleteComment = { false },
                    onSetReaction = {},
                    onSetRsvp = {},
                    onCommentChanged = {},
                    onAddComment = {},
                    onDeleteComment = {},
                    onDeleteIdea = {},
                    onEdit = { _, _ -> },
                    onSchedule = { _, _ -> },
                    onComplete = { _, _ -> },
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("选择你的态度").assertIsDisplayed()
        composeRule.onNodeWithText("想参加").assertIsDisplayed()
        composeRule.onNodeWithText("都可以").assertIsDisplayed()
        composeRule.onNodeWithText("不感兴趣").assertIsDisplayed()
        composeRule.onNodeWithText("写下评论").assertIsDisplayed()
        composeRule.onNodeWithText("发布评论").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun creatorSeesEditAndDeleteActions() {
        setPermissionContent(canEdit = true, canDelete = true)
        composeRule.onNodeWithText("编辑").assertIsDisplayed()
        composeRule.onNodeWithText("删除").assertIsDisplayed()
    }

    @Test
    fun adminSeesDeleteButCannotEditAnotherMembersIdea() {
        setPermissionContent(canEdit = false, canDelete = true)
        composeRule.onAllNodesWithText("编辑").assertCountEquals(0)
        composeRule.onNodeWithText("删除").assertIsDisplayed()
    }

    @Test
    fun ordinaryMemberSeesNoIdeaManagementActions() {
        setPermissionContent(canEdit = false, canDelete = false)
        composeRule.onAllNodesWithText("编辑").assertCountEquals(0)
        composeRule.onAllNodesWithText("删除").assertCountEquals(0)
    }

    private fun setPermissionContent(canEdit: Boolean, canDelete: Boolean) {
        composeRule.setContent {
            NextListTheme {
                IdeaDetailScreen(
                    state = ideaDetailState(),
                    canEditIdea = canEdit,
                    canDeleteIdea = canDelete,
                    canDeleteComment = { false },
                    onSetReaction = {},
                    onSetRsvp = {},
                    onCommentChanged = {},
                    onAddComment = {},
                    onDeleteComment = {},
                    onDeleteIdea = {},
                    onEdit = { _, _ -> },
                    onSchedule = { _, _ -> },
                    onComplete = { _, _ -> },
                    onBack = {},
                )
            }
        }
    }
}

private fun groupDetailState() = GroupDetailUiState(
    group = LoadState.Content(sampleGroup()),
    members = sampleMembers(),
    ideas = LoadState.Empty("还没有灵感，记下大家下次想做的事吧。"),
    currentUserId = "alice",
)

private fun ideaDetailState() = IdeaDetailUiState(
    idea = LoadState.Content(sampleIdea()),
    group = sampleGroup(),
    members = sampleMembers(),
    comments = LoadState.Empty("还没有评论"),
    currentUserId = "charlie",
)

private fun sampleGroup() = Group(
    id = "group-1",
    name = "周末去哪",
    adminId = "alice",
    status = GroupStatus.ACTIVE,
    memberCount = 3,
    ideaCount = 1,
    scheduledCount = 0,
    completedCount = 0,
    lastActivityAt = Instant.EPOCH,
)

private fun sampleMembers() = listOf(
    GroupMember("alice", GroupRole.ADMIN, "小林", null),
    GroupMember("bob", GroupRole.MEMBER, "小周", null),
    GroupMember("charlie", GroupRole.MEMBER, "小陈", null),
)

private fun sampleIdea() = Idea(
    id = "idea-1",
    groupId = "group-1",
    title = "去植物园",
    category = IdeaCategory.PLACE,
    note = "看荷花",
    media = null,
    locationOrLink = "https://example.com",
    createdBy = "bob",
    creatorSnapshot = MemberSnapshot("小周", null),
    status = IdeaStatus.IDEA,
    reactionCounts = ReactionCounts(),
    commentCount = 0,
    lastModifiedBy = "bob",
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)
