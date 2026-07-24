package com.example.nextlist.feature.ideas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import com.example.nextlist.core.designsystem.NextListTheme
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.domain.model.Group
import com.example.nextlist.domain.model.GroupMember
import com.example.nextlist.domain.model.GroupRole
import com.example.nextlist.domain.model.GroupStatus
import com.example.nextlist.domain.model.Idea
import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaCompletion
import com.example.nextlist.domain.model.IdeaRsvp
import com.example.nextlist.domain.model.IdeaSchedule
import com.example.nextlist.domain.model.IdeaStatus
import com.example.nextlist.domain.model.MemberSnapshot
import com.example.nextlist.domain.model.ReactionCounts
import com.example.nextlist.domain.model.RsvpCounts
import com.example.nextlist.domain.model.RsvpValue
import com.example.nextlist.feature.groups.GroupDetailScreen
import com.example.nextlist.feature.groups.GroupDetailUiState
import java.time.Instant
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class M4ScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun scheduledCardShowsTimeMeetingPointCountsAndPastDueLabel() {
        setGroupContent(
            IdeaStatus.SCHEDULED,
            scheduledIdea(
                startAt = Instant.parse("2020-07-23T06:00:00Z"),
            ),
        )

        composeRule.onNodeWithText("去看日落").assertIsDisplayed()
        composeRule.onNodeWithText("待完成").assertIsDisplayed()
        composeRule.onNodeWithText("集合地点：江边入口").assertIsDisplayed()
        composeRule.onNodeWithText("2 人参加 · 1 人待定").assertIsDisplayed()
    }

    @Test
    fun completedCardShowsDateAndRating() {
        setGroupContent(IdeaStatus.COMPLETED, completedIdea())

        composeRule.onNodeWithText("完成于 2026-07-24").assertIsDisplayed()
        composeRule.onNodeWithText("评分：★★★★").assertIsDisplayed()
    }

    @Test
    fun scheduleFormShowsRequiredFieldsConflictAndDisabledSubmission() {
        composeRule.setContent {
            NextListTheme {
                ScheduleFormScreen(
                    state = ScheduleFormUiState(
                        isLoading = false,
                        ideaTitle = "去看日落",
                        isEditing = true,
                        date = "2026-07-27",
                        time = "18:30",
                        timezone = "Asia/Shanghai",
                        conflict = true,
                        message = "安排已被其他成员更新，已载入最新安排，请确认后重试",
                        isSubmitting = true,
                    ),
                    onDateChanged = {},
                    onTimeChanged = {},
                    onTimezoneChanged = {},
                    onMeetingPointChanged = {},
                    onNoteChanged = {},
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("修改安排").assertExists()
        composeRule.onNodeWithText("日期").assertExists()
        composeRule.onNodeWithText("时间").assertExists()
        composeRule.onNodeWithText("时区").assertExists()
        composeRule.onNodeWithText("检测到安排冲突").assertExists()
        composeRule.onNodeWithText("正在保存安排…").assertIsNotEnabled()
    }

    @Test
    fun completionFormShowsOptionalPhotoReviewAndIntegerRatings() {
        composeRule.setContent {
            NextListTheme {
                CompletionFormScreen(
                    state = CompletionFormUiState(
                        isLoading = false,
                        ideaTitle = "去看日落",
                        completedOn = "2026-07-24",
                        timezone = "Asia/Shanghai",
                    ),
                    onDateChanged = {},
                    onTimezoneChanged = {},
                    onReviewChanged = {},
                    onRatingChanged = {},
                    onImageSelected = {},
                    onRemoveImage = {},
                    onSubmit = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("完成照片（可选，最多一张）").assertExists()
        composeRule.onNodeWithText("简短评价（可选）").assertExists()
        composeRule.onNodeWithText("不评分").assertExists()
        composeRule.onNodeWithText("5 星").assertExists()
        composeRule.onNodeWithText("保存完成记录").assertExists()
    }

    @Test
    fun randomScreenShowsFiltersAndResultActions() {
        composeRule.setContent {
            NextListTheme {
                RandomDecisionScreen(
                    state = RandomDecisionUiState(
                        memberCount = 3,
                        candidates = listOf(scheduledIdea()),
                        result = scheduledIdea(),
                        hasDrawn = true,
                    ),
                    onSelectCategory = {},
                    onSetMinimumWant = {},
                    onDraw = {},
                    onDrawAnother = {},
                    onArrange = {},
                    onAddIdea = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("最少“想参加”人数：0").assertExists()
        composeRule.onNodeWithText("换一个").assertExists()
        composeRule.onNodeWithText("安排这个").assertExists()
        composeRule.onNodeWithText("这个想法已经安排，将打开现有安排").assertExists()
    }

    @Test
    fun randomScreenShowsNoResultRecovery() {
        composeRule.setContent {
            NextListTheme {
                RandomDecisionScreen(
                    state = RandomDecisionUiState(memberCount = 3, hasDrawn = true),
                    onSelectCategory = {},
                    onSetMinimumWant = {},
                    onDraw = {},
                    onDrawAnother = {},
                    onArrange = {},
                    onAddIdea = {},
                    onBack = {},
                )
            }
        }
        composeRule.onNodeWithText("没有符合条件的想法").assertExists()
        composeRule.onNodeWithText("添加想法").assertExists()
    }

    @Test
    fun scheduledDetailShowsRsvpStaleWarningAndPrimaryActions() {
        val idea = scheduledIdea()
        composeRule.setContent {
            NextListTheme {
                IdeaDetailScreen(
                    state = detailState(
                        idea,
                        listOf(
                            IdeaRsvp(
                                userId = "bob",
                                value = RsvpValue.GOING,
                                scheduleRevision = 1,
                                userSnapshot = MemberSnapshot("小周", null),
                                createdAt = Instant.EPOCH,
                                updatedAt = Instant.EPOCH,
                            ),
                        ),
                    ),
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

        composeRule.onNodeWithText("安排已变化，请确认").assertExists()
        composeRule.onNodeWithText("修改安排").assertExists()
        composeRule.onNodeWithText("标记完成").assertExists()
    }

    @Test
    fun completedDetailShowsOriginalScheduleFinalRsvpAndCompletionAudit() {
        val idea = completedIdea()
        composeRule.setContent {
            NextListTheme {
                IdeaDetailScreen(
                    state = detailState(idea, emptyList()),
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

        composeRule.onNodeWithText("最终参加状态").assertExists()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText("完成日期：2026-07-24"),
        )
        composeRule.onNodeWithText("完成日期：2026-07-24").assertExists()
        composeRule.onNodeWithText("评分：★★★★").assertExists()
        composeRule.onNodeWithText("编辑完成记录").assertExists()
    }

    private fun setGroupContent(status: IdeaStatus, idea: Idea) {
        composeRule.setContent {
            NextListTheme {
                GroupDetailScreen(
                    state = GroupDetailUiState(
                        group = LoadState.Content(group()),
                        members = members(),
                        ideas = LoadState.Content(listOf(idea)),
                        selectedStatus = status,
                        currentUserId = "alice",
                    ),
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
    }
}

private fun detailState(idea: Idea, rsvps: List<IdeaRsvp>) = IdeaDetailUiState(
    idea = LoadState.Content(idea),
    group = group(),
    members = members(),
    rsvps = rsvps,
    comments = LoadState.Empty("还没有评论"),
    currentUserId = "bob",
)

private fun scheduledIdea(
    startAt: Instant = Instant.parse("2026-07-27T10:30:00Z"),
) = baseIdea().copy(
    status = IdeaStatus.SCHEDULED,
    schedule = IdeaSchedule(
        startAt = startAt,
        timezone = "Asia/Shanghai",
        meetingPoint = "江边入口",
        note = "带水",
        scheduledBy = "alice",
        schedulerSnapshot = MemberSnapshot("小林", null),
        scheduledAt = Instant.EPOCH,
        updatedBy = "alice",
        updatedAt = Instant.EPOCH,
        revision = 2,
    ),
    rsvpCounts = RsvpCounts(going = 2, maybe = 1),
)

private fun completedIdea() = scheduledIdea().copy(
    status = IdeaStatus.COMPLETED,
    completion = IdeaCompletion(
        completedOn = LocalDate.of(2026, 7, 24),
        timezone = "Asia/Shanghai",
        photo = null,
        review = "晚霞很好看",
        rating = 4,
        completedBy = "bob",
        completerSnapshot = MemberSnapshot("小周", null),
        completedAt = Instant.EPOCH,
        updatedBy = "alice",
        updatedAt = Instant.EPOCH,
    ),
)

private fun baseIdea() = Idea(
    id = "idea-1",
    groupId = "group-1",
    title = "去看日落",
    category = IdeaCategory.ACTIVITY,
    note = null,
    media = null,
    locationOrLink = null,
    createdBy = "bob",
    creatorSnapshot = MemberSnapshot("小周", null),
    status = IdeaStatus.IDEA,
    reactionCounts = ReactionCounts(want = 2),
    commentCount = 0,
    lastModifiedBy = "bob",
    createdAt = Instant.EPOCH,
    updatedAt = Instant.EPOCH,
)

private fun group() = Group(
    id = "group-1",
    name = "周末去哪",
    adminId = "alice",
    status = GroupStatus.ACTIVE,
    memberCount = 3,
    ideaCount = 0,
    scheduledCount = 1,
    completedCount = 1,
    lastActivityAt = Instant.EPOCH,
)

private fun members() = listOf(
    GroupMember("alice", GroupRole.ADMIN, "小林", null),
    GroupMember("bob", GroupRole.MEMBER, "小周", null),
    GroupMember("charlie", GroupRole.MEMBER, "小陈", null),
)
