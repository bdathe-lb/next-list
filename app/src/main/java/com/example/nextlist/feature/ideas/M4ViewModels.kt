package com.example.nextlist.feature.ideas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.core.time.AppClock
import com.example.nextlist.core.validation.IdeaInputValidator
import com.example.nextlist.core.validation.ScheduleCompletionValidator
import com.example.nextlist.domain.model.CompletionDraft
import com.example.nextlist.domain.model.Idea
import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaMedia
import com.example.nextlist.domain.model.IdeaStatus
import com.example.nextlist.domain.model.RandomIdeaSelector
import com.example.nextlist.domain.model.ScheduleDraft
import com.example.nextlist.domain.repository.GroupRepository
import com.example.nextlist.domain.repository.IdeaImageRepository
import com.example.nextlist.domain.repository.IdeaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleFormUiState(
    val isLoading: Boolean = true,
    val ideaTitle: String = "",
    val isEditing: Boolean = false,
    val date: String = "",
    val time: String = "",
    val timezone: String = ZoneId.systemDefault().id,
    val meetingPoint: String = "",
    val note: String = "",
    val dateError: String? = null,
    val timeError: String? = null,
    val timezoneError: String? = null,
    val meetingPointError: String? = null,
    val noteError: String? = null,
    val expectedRevision: Int = 0,
    val isSubmitting: Boolean = false,
    val conflict: Boolean = false,
    val message: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ScheduleFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ideaRepository: IdeaRepository,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val ideaId = savedStateHandle.get<String>("ideaId").orEmpty()
    private val mutableState = MutableStateFlow(ScheduleFormUiState())
    val uiState = mutableState.asStateFlow()

    init {
        loadInitial()
    }

    fun onDateChanged(value: String) = edit {
        copy(date = value, dateError = null)
    }

    fun onTimeChanged(value: String) = edit {
        copy(time = value, timeError = null)
    }

    fun onTimezoneChanged(value: String) = edit {
        copy(timezone = value, timezoneError = null)
    }

    fun onMeetingPointChanged(value: String) = edit {
        copy(meetingPoint = value, meetingPointError = null)
    }

    fun onNoteChanged(value: String) = edit {
        copy(note = value, noteError = null)
    }

    fun submit() {
        val current = mutableState.value
        if (current.isLoading || current.isSubmitting || current.saved) return
        val dateError = ScheduleCompletionValidator.dateError(current.date)
        val timeError = ScheduleCompletionValidator.timeError(current.time)
        val timezoneError = ScheduleCompletionValidator.timezoneError(current.timezone)
        val meetingPointError =
            ScheduleCompletionValidator.meetingPointError(current.meetingPoint)
        val noteError = ScheduleCompletionValidator.scheduleNoteError(current.note)
        if (
            dateError != null ||
            timeError != null ||
            timezoneError != null ||
            meetingPointError != null ||
            noteError != null
        ) {
            mutableState.update {
                it.copy(
                    dateError = dateError,
                    timeError = timeError,
                    timezoneError = timezoneError,
                    meetingPointError = meetingPointError,
                    noteError = noteError,
                )
            }
            return
        }
        val date = requireNotNull(ScheduleCompletionValidator.parseDate(current.date))
        val time = requireNotNull(ScheduleCompletionValidator.parseTime(current.time))
        val zone = requireNotNull(ScheduleCompletionValidator.zoneId(current.timezone))
        val draft = ScheduleDraft(
            startAt = date.atTime(time).atZone(zone).toInstant(),
            timezone = zone.id,
            meetingPoint = IdeaInputValidator.normalizedOptional(current.meetingPoint),
            note = IdeaInputValidator.normalizedOptional(current.note),
            expectedRevision = current.expectedRevision,
        )
        mutableState.update {
            it.copy(isSubmitting = true, conflict = false, message = null)
        }
        viewModelScope.launch {
            when (val result = ideaRepository.saveSchedule(groupId, ideaId, draft)) {
                is AppResult.Success -> mutableState.update {
                    it.copy(isSubmitting = false, saved = true)
                }
                is AppResult.Failure -> {
                    if (result.error == AppError.CONFLICT) {
                        reloadAfterConflict()
                    } else {
                        mutableState.update {
                            it.copy(
                                isSubmitting = false,
                                message = if (result.error == AppError.NETWORK_UNAVAILABLE) {
                                    "安排需要联网保存，请恢复网络后重试"
                                } else {
                                    result.error.toUserMessage()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun loadInitial() {
        viewModelScope.launch {
            when (val result = ideaRepository.observeIdea(groupId, ideaId).first()) {
                is AppResult.Success -> applyIdea(result.value)
                is AppResult.Failure -> mutableState.update {
                    it.copy(isLoading = false, message = result.error.toUserMessage())
                }
            }
        }
    }

    private suspend fun reloadAfterConflict() {
        when (val latest = ideaRepository.getIdeaFromServer(groupId, ideaId)) {
            is AppResult.Success -> {
                applyIdea(latest.value)
                mutableState.update {
                    it.copy(
                        conflict = true,
                        message = "安排已被其他成员更新，已载入最新安排，请确认后重试",
                    )
                }
            }
            is AppResult.Failure -> mutableState.update {
                it.copy(
                    isLoading = false,
                    isSubmitting = false,
                    conflict = true,
                    message = latest.error.toUserMessage(),
                )
            }
        }
    }

    private fun applyIdea(idea: Idea) {
        if (idea.status == IdeaStatus.COMPLETED) {
            mutableState.update {
                it.copy(
                    isLoading = false,
                    isSubmitting = false,
                    message = "已完成内容不能再修改安排",
                )
            }
            return
        }
        val schedule = idea.schedule
        val zone = schedule?.timezone?.let(ScheduleCompletionValidator::zoneId)
            ?: ZoneId.systemDefault()
        val local = schedule?.startAt?.atZone(zone)
        mutableState.update {
            it.copy(
                isLoading = false,
                isSubmitting = false,
                ideaTitle = idea.title,
                isEditing = schedule != null,
                date = local?.toLocalDate()?.toString().orEmpty(),
                time = local?.toLocalTime()?.format(TIME_FORMAT).orEmpty(),
                timezone = zone.id,
                meetingPoint = schedule?.meetingPoint.orEmpty(),
                note = schedule?.note.orEmpty(),
                expectedRevision = schedule?.revision ?: 0,
            )
        }
    }

    private inline fun edit(transform: ScheduleFormUiState.() -> ScheduleFormUiState) {
        if (mutableState.value.isSubmitting || mutableState.value.isLoading) return
        mutableState.update { transform(it).copy(message = null, conflict = false) }
    }
}

data class CompletionFormUiState(
    val isLoading: Boolean = true,
    val ideaTitle: String = "",
    val isEditing: Boolean = false,
    val completedOn: String = "",
    val timezone: String = ZoneId.systemDefault().id,
    val existingPhoto: IdeaMedia? = null,
    val selectedImageUri: String? = null,
    val review: String = "",
    val rating: Int? = null,
    val dateError: String? = null,
    val timezoneError: String? = null,
    val reviewError: String? = null,
    val ratingError: String? = null,
    val isSubmitting: Boolean = false,
    val submitStage: String? = null,
    val message: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class CompletionFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ideaRepository: IdeaRepository,
    private val imageRepository: IdeaImageRepository,
    appClock: AppClock,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val ideaId = savedStateHandle.get<String>("ideaId").orEmpty()
    private val defaultZone = ZoneId.systemDefault()
    private val today = appClock.now().atZone(defaultZone).toLocalDate().toString()
    private val mutableState = MutableStateFlow(
        CompletionFormUiState(completedOn = today, timezone = defaultZone.id),
    )
    val uiState = mutableState.asStateFlow()

    init {
        load()
    }

    fun onDateChanged(value: String) = edit { copy(completedOn = value, dateError = null) }
    fun onTimezoneChanged(value: String) = edit {
        copy(timezone = value, timezoneError = null)
    }
    fun onReviewChanged(value: String) = edit { copy(review = value, reviewError = null) }
    fun onRatingChanged(value: Int?) = edit { copy(rating = value, ratingError = null) }
    fun onImageSelected(uri: String) = edit {
        copy(selectedImageUri = uri, message = null)
    }
    fun removeImage() = edit {
        copy(existingPhoto = null, selectedImageUri = null, message = null)
    }

    fun submit() {
        val current = mutableState.value
        if (current.isLoading || current.isSubmitting || current.saved) return
        val dateError = ScheduleCompletionValidator.dateError(current.completedOn)
        val timezoneError = ScheduleCompletionValidator.timezoneError(current.timezone)
        val reviewError = ScheduleCompletionValidator.reviewError(current.review)
        val ratingError = ScheduleCompletionValidator.ratingError(current.rating)
        if (
            dateError != null ||
            timezoneError != null ||
            reviewError != null ||
            ratingError != null
        ) {
            mutableState.update {
                it.copy(
                    dateError = dateError,
                    timezoneError = timezoneError,
                    reviewError = reviewError,
                    ratingError = ratingError,
                )
            }
            return
        }
        mutableState.update {
            it.copy(isSubmitting = true, submitStage = "正在保存…", message = null)
        }
        viewModelScope.launch {
            val oldPhoto = current.existingPhoto
            var uploadedPhoto: IdeaMedia? = null
            val photo = if (current.selectedImageUri != null) {
                mutableState.update { it.copy(submitStage = "正在上传照片…") }
                when (
                    val upload = imageRepository.uploadCompletion(
                        groupId,
                        ideaId,
                        current.selectedImageUri,
                    )
                ) {
                    is AppResult.Success -> upload.value.also { uploadedPhoto = it }
                    is AppResult.Failure -> {
                        mutableState.update {
                            it.copy(
                                isSubmitting = false,
                                submitStage = null,
                                message = if (upload.error == AppError.NETWORK_UNAVAILABLE) {
                                    "完成照片上传需要网络，表单内容已保留"
                                } else {
                                    upload.error.toUserMessage()
                                },
                            )
                        }
                        return@launch
                    }
                }
            } else {
                current.existingPhoto
            }
            val draft = CompletionDraft(
                completedOn = requireNotNull(
                    ScheduleCompletionValidator.parseDate(current.completedOn),
                ),
                timezone = requireNotNull(
                    ScheduleCompletionValidator.zoneId(current.timezone),
                ).id,
                photo = photo,
                review = IdeaInputValidator.normalizedOptional(current.review),
                rating = current.rating,
            )
            mutableState.update { it.copy(submitStage = "正在保存完成记录…") }
            when (val result = ideaRepository.saveCompletion(groupId, ideaId, draft)) {
                is AppResult.Success -> {
                    if (oldPhoto != null && oldPhoto.storagePath != photo?.storagePath) {
                        imageRepository.delete(oldPhoto.storagePath)
                    }
                    mutableState.update {
                        it.copy(isSubmitting = false, submitStage = null, saved = true)
                    }
                }
                is AppResult.Failure -> {
                    uploadedPhoto?.let { imageRepository.delete(it.storagePath) }
                    mutableState.update {
                        it.copy(
                            isSubmitting = false,
                            submitStage = null,
                            message = if (result.error == AppError.NETWORK_UNAVAILABLE) {
                                "完成记录需要联网保存，请恢复网络后重试"
                            } else {
                                result.error.toUserMessage()
                            },
                        )
                    }
                }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            when (val result = ideaRepository.observeIdea(groupId, ideaId).first()) {
                is AppResult.Success -> {
                    val idea = result.value
                    if (idea.status == IdeaStatus.IDEA) {
                        mutableState.update {
                            it.copy(isLoading = false, message = "请先安排这个想法")
                        }
                        return@launch
                    }
                    val completion = idea.completion
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            ideaTitle = idea.title,
                            isEditing = completion != null,
                            completedOn = completion?.completedOn?.toString() ?: today,
                            timezone = completion?.timezone ?: defaultZone.id,
                            existingPhoto = completion?.photo,
                            review = completion?.review.orEmpty(),
                            rating = completion?.rating,
                        )
                    }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(isLoading = false, message = result.error.toUserMessage())
                }
            }
        }
    }

    private inline fun edit(transform: CompletionFormUiState.() -> CompletionFormUiState) {
        if (mutableState.value.isSubmitting || mutableState.value.isLoading) return
        mutableState.update { transform(it).copy(message = null) }
    }
}

data class RandomDecisionUiState(
    val category: IdeaCategory? = null,
    val minimumWant: Int = 0,
    val memberCount: Int = 0,
    val isLoading: Boolean = false,
    val candidates: List<Idea> = emptyList(),
    val result: Idea? = null,
    val hasDrawn: Boolean = false,
    val isCheckingResult: Boolean = false,
    val arrangeTargetId: String? = null,
    val message: String? = null,
)

@HiltViewModel
class RandomDecisionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val ideaRepository: IdeaRepository,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val mutableState = MutableStateFlow(RandomDecisionUiState())
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            groupRepository.observeMembers(groupId).collect { result ->
                if (result is AppResult.Success) {
                    mutableState.update {
                        it.copy(
                            memberCount = result.value.size,
                            minimumWant = it.minimumWant.coerceAtMost(result.value.size),
                        )
                    }
                }
            }
        }
    }

    fun selectCategory(category: IdeaCategory?) {
        if (mutableState.value.isLoading) return
        mutableState.update {
            it.copy(category = category, hasDrawn = false, result = null, message = null)
        }
    }

    fun setMinimumWant(value: Int) {
        val current = mutableState.value
        if (current.isLoading) return
        mutableState.update {
            it.copy(
                minimumWant = value.coerceIn(0, current.memberCount),
                hasDrawn = false,
                result = null,
                message = null,
            )
        }
    }

    fun draw() {
        val current = mutableState.value
        if (current.isLoading || current.isCheckingResult) return
        mutableState.update { it.copy(isLoading = true, message = null) }
        viewModelScope.launch {
            when (
                val loaded = ideaRepository.loadRandomCandidates(
                    groupId,
                    current.category,
                    current.minimumWant,
                )
            ) {
                is AppResult.Success -> {
                    val candidates = RandomIdeaSelector.filter(
                        loaded.value,
                        current.category,
                        current.minimumWant,
                    )
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            candidates = candidates,
                            result = RandomIdeaSelector.choose(candidates),
                            hasDrawn = true,
                        )
                    }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        hasDrawn = true,
                        message = loaded.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun drawAnother(random: Random = Random.Default) {
        val current = mutableState.value
        if (current.isLoading || current.isCheckingResult) return
        mutableState.update {
            it.copy(
                result = RandomIdeaSelector.choose(
                    current.candidates,
                    current.result?.id,
                    random,
                ),
                message = null,
            )
        }
    }

    fun arrangeResult() {
        val current = mutableState.value
        val selected = current.result ?: return
        if (current.isCheckingResult) return
        mutableState.update { it.copy(isCheckingResult = true, message = null) }
        viewModelScope.launch {
            when (val checked = ideaRepository.getIdeaFromServer(groupId, selected.id)) {
                is AppResult.Success -> {
                    if (checked.value.status in setOf(IdeaStatus.IDEA, IdeaStatus.SCHEDULED)) {
                        mutableState.update {
                            it.copy(
                                isCheckingResult = false,
                                arrangeTargetId = checked.value.id,
                            )
                        }
                    } else {
                        replaceInvalid(selected.id)
                    }
                }
                is AppResult.Failure -> {
                    if (checked.error == AppError.NOT_FOUND) {
                        replaceInvalid(selected.id)
                    } else {
                        mutableState.update {
                            it.copy(
                                isCheckingResult = false,
                                message = checked.error.toUserMessage(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun consumeArrangeTarget() {
        mutableState.update { it.copy(arrangeTargetId = null) }
    }

    private fun replaceInvalid(invalidId: String) {
        mutableState.update {
            val remaining = it.candidates.filterNot { idea -> idea.id == invalidId }
            it.copy(
                isCheckingResult = false,
                candidates = remaining,
                result = RandomIdeaSelector.choose(remaining),
                message = if (remaining.isEmpty()) {
                    "结果已被完成或删除，请调整条件后重试"
                } else {
                    "原结果已被完成或删除，已重新抽取"
                },
            )
        }
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm")
