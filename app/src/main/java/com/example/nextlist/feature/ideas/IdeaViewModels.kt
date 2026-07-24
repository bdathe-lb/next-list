package com.example.nextlist.feature.ideas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.core.validation.IdeaInputValidator
import com.example.nextlist.domain.model.Group
import com.example.nextlist.domain.model.GroupMember
import com.example.nextlist.domain.model.Idea
import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaComment
import com.example.nextlist.domain.model.IdeaDraft
import com.example.nextlist.domain.model.IdeaMedia
import com.example.nextlist.domain.model.IdeaReaction
import com.example.nextlist.domain.model.IdeaRsvp
import com.example.nextlist.domain.model.IdeaStatus
import com.example.nextlist.domain.model.MemberSnapshot
import com.example.nextlist.domain.model.ReactionValue
import com.example.nextlist.domain.model.RsvpValue
import com.example.nextlist.domain.repository.AvatarRepository
import com.example.nextlist.domain.repository.GroupRepository
import com.example.nextlist.domain.repository.IdeaImageRepository
import com.example.nextlist.domain.repository.IdeaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class IdeaFormUiState(
    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val title: String = "",
    val category: IdeaCategory = IdeaCategory.OTHER,
    val note: String = "",
    val locationOrLink: String = "",
    val existingMedia: IdeaMedia? = null,
    val selectedImageUri: String? = null,
    val titleError: String? = null,
    val noteError: String? = null,
    val locationOrLinkError: String? = null,
    val isDirty: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitStage: String? = null,
    val message: String? = null,
    val savedIdeaId: String? = null,
)

@HiltViewModel
class IdeaFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val ideaRepository: IdeaRepository,
    private val imageRepository: IdeaImageRepository,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val routeIdeaId = savedStateHandle.get<String>("ideaId")
    private val isEditing = !routeIdeaId.isNullOrBlank()
    private val ideaId = routeIdeaId ?: ideaRepository.newIdeaId(groupId)
    private val mutableState = MutableStateFlow(
        IdeaFormUiState(isLoading = isEditing, isEditing = isEditing),
    )
    val uiState = mutableState.asStateFlow()
    private var initialDraft: IdeaDraft? = null

    init {
        if (isEditing) loadIdea()
    }

    fun onTitleChanged(value: String) = edit {
        copy(title = value, titleError = null)
    }

    fun onCategoryChanged(value: IdeaCategory) = edit {
        copy(category = value)
    }

    fun onNoteChanged(value: String) = edit {
        copy(note = value, noteError = null)
    }

    fun onLocationOrLinkChanged(value: String) = edit {
        copy(locationOrLink = value, locationOrLinkError = null)
    }

    fun onImageSelected(uri: String) = edit {
        copy(selectedImageUri = uri, message = null)
    }

    fun removeImage() = edit {
        copy(existingMedia = null, selectedImageUri = null, message = null)
    }

    fun submit() {
        val current = mutableState.value
        if (current.isSubmitting || current.isLoading || current.savedIdeaId != null) return
        val titleError = IdeaInputValidator.titleError(current.title)
        val noteError = IdeaInputValidator.noteError(current.note)
        val locationError = IdeaInputValidator.locationOrLinkError(current.locationOrLink)
        if (titleError != null || noteError != null || locationError != null) {
            mutableState.update {
                it.copy(
                    titleError = titleError,
                    noteError = noteError,
                    locationOrLinkError = locationError,
                )
            }
            return
        }
        mutableState.update {
            it.copy(isSubmitting = true, submitStage = "正在保存…", message = null)
        }
        viewModelScope.launch {
            val oldMedia = current.existingMedia
            var uploadedMedia: IdeaMedia? = null
            val selectedUri = current.selectedImageUri
            val media = if (selectedUri != null) {
                mutableState.update { it.copy(submitStage = "正在上传图片…") }
                when (val upload = imageRepository.uploadCover(groupId, ideaId, selectedUri)) {
                    is AppResult.Success -> upload.value.also { uploadedMedia = it }
                    is AppResult.Failure -> {
                        mutableState.update {
                            it.copy(
                                isSubmitting = false,
                                submitStage = null,
                                message = if (upload.error == AppError.NETWORK_UNAVAILABLE) {
                                    "图片上传需要网络，表单内容已保留"
                                } else if (upload.error == AppError.VALIDATION) {
                                    "图片无效或处理失败，请重试或移除图片"
                                } else {
                                    upload.error.toUserMessage()
                                },
                            )
                        }
                        return@launch
                    }
                }
            } else {
                current.existingMedia
            }
            val draft = IdeaDraft(
                title = current.title.trim(),
                category = current.category,
                note = IdeaInputValidator.normalizedOptional(current.note),
                locationOrLink = IdeaInputValidator.normalizedOptional(current.locationOrLink),
                media = media,
            )
            mutableState.update { it.copy(submitStage = "正在保存…") }
            val result = if (isEditing) {
                ideaRepository.updateIdea(
                    groupId,
                    ideaId,
                    draft,
                    requireServerAck = selectedUri != null,
                )
            } else {
                ideaRepository.createIdea(
                    groupId,
                    ideaId,
                    draft,
                    requireServerAck = selectedUri != null,
                )
            }
            when (result) {
                is AppResult.Success -> {
                    if (
                        selectedUri != null &&
                        oldMedia != null &&
                        oldMedia.storagePath != media?.storagePath
                    ) {
                        imageRepository.delete(oldMedia.storagePath)
                    }
                    mutableState.update {
                        it.copy(
                            isSubmitting = false,
                            submitStage = null,
                            isDirty = false,
                            savedIdeaId = ideaId,
                        )
                    }
                }
                is AppResult.Failure -> {
                    uploadedMedia?.let { imageRepository.delete(it.storagePath) }
                    mutableState.update {
                        it.copy(
                            isSubmitting = false,
                            submitStage = null,
                            message = result.error.toUserMessage(),
                        )
                    }
                }
            }
        }
    }

    private fun loadIdea() {
        viewModelScope.launch {
            when (val result = ideaRepository.observeIdea(groupId, ideaId).first()) {
                is AppResult.Success -> {
                    val idea = result.value
                    val draft = IdeaDraft(
                        title = idea.title,
                        category = idea.category,
                        note = idea.note,
                        locationOrLink = idea.locationOrLink,
                        media = idea.media,
                    )
                    initialDraft = draft
                    mutableState.update {
                        it.copy(
                            isLoading = false,
                            title = draft.title,
                            category = draft.category,
                            note = draft.note.orEmpty(),
                            locationOrLink = draft.locationOrLink.orEmpty(),
                            existingMedia = draft.media,
                            isDirty = false,
                        )
                    }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isLoading = false,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    private inline fun edit(transform: IdeaFormUiState.() -> IdeaFormUiState) {
        val current = mutableState.value
        if (current.isSubmitting || current.isLoading) return
        mutableState.update {
            transform(it).let { changed ->
                changed.copy(
                    isDirty = currentDraft(changed) != initialDraft &&
                        (
                            changed.title.isNotEmpty() ||
                                changed.note.isNotEmpty() ||
                                changed.locationOrLink.isNotEmpty() ||
                                changed.selectedImageUri != null ||
                                changed.category != IdeaCategory.OTHER ||
                                isEditing
                            ),
                    message = null,
                )
            }
        }
    }

    private fun currentDraft(state: IdeaFormUiState) = IdeaDraft(
        title = state.title,
        category = state.category,
        note = state.note.ifEmpty { null },
        locationOrLink = state.locationOrLink.ifEmpty { null },
        media = state.existingMedia,
    )
}

data class IdeaDetailUiState(
    val idea: LoadState<Idea> = LoadState.Loading,
    val group: Group? = null,
    val members: List<GroupMember> = emptyList(),
    val reactions: List<IdeaReaction> = emptyList(),
    val rsvps: List<IdeaRsvp> = emptyList(),
    val comments: LoadState<List<IdeaComment>> = LoadState.Loading,
    val currentUserId: String? = null,
    val imageUrl: String? = null,
    val completionPhotoUrl: String? = null,
    val commentInput: String = "",
    val commentError: String? = null,
    val isReactionSubmitting: Boolean = false,
    val isRsvpSubmitting: Boolean = false,
    val isCommentSubmitting: Boolean = false,
    val deletingCommentId: String? = null,
    val isDeletingIdea: Boolean = false,
    val message: String? = null,
    val ideaDeleted: Boolean = false,
)

@HiltViewModel
class IdeaDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val groupRepository: GroupRepository,
    private val ideaRepository: IdeaRepository,
    private val imageRepository: IdeaImageRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val ideaId = savedStateHandle.get<String>("ideaId").orEmpty()
    private val mutableState = MutableStateFlow(
        IdeaDetailUiState(currentUserId = groupRepository.currentUserId()),
    )
    val uiState = mutableState.asStateFlow()
    private var resolvedImagePath: String? = null
    private var resolvedCompletionPhotoPath: String? = null
    private val avatarUrls = mutableMapOf<String, String>()

    init {
        observeHeader()
        observeIdea()
        observeDiscussion()
        observeRsvps()
    }

    fun onCommentChanged(value: String) {
        if (mutableState.value.isCommentSubmitting) return
        mutableState.update { it.copy(commentInput = value, commentError = null, message = null) }
    }

    fun setReaction(value: ReactionValue) {
        val current = mutableState.value
        if (current.isReactionSubmitting) return
        val existing = current.reactions.firstOrNull { it.userId == current.currentUserId }
        mutableState.update { it.copy(isReactionSubmitting = true, message = null) }
        viewModelScope.launch {
            val result = if (existing?.value == value) {
                ideaRepository.clearReaction(groupId, ideaId)
            } else {
                ideaRepository.setReaction(
                    groupId,
                    ideaId,
                    value,
                    isExisting = existing != null,
                )
            }
            mutableState.update {
                it.copy(
                    isReactionSubmitting = false,
                    message = (result as? AppResult.Failure)?.error?.toUserMessage(),
                )
            }
        }
    }

    fun setRsvp(value: RsvpValue) {
        val current = mutableState.value
        if (current.isRsvpSubmitting) return
        val idea = (current.idea as? LoadState.Content)?.data ?: return
        val scheduleRevision = idea.schedule?.revision ?: return
        if (idea.status != IdeaStatus.SCHEDULED) return
        val existing = current.rsvps.firstOrNull { it.userId == current.currentUserId }
        val isStale = existing != null && existing.scheduleRevision < scheduleRevision
        mutableState.update { it.copy(isRsvpSubmitting = true, message = null) }
        viewModelScope.launch {
            val result = if (existing?.value == value && !isStale) {
                ideaRepository.clearRsvp(groupId, ideaId)
            } else {
                ideaRepository.setRsvp(
                    groupId = groupId,
                    ideaId = ideaId,
                    value = value,
                    scheduleRevision = scheduleRevision,
                    isExisting = existing != null,
                )
            }
            mutableState.update {
                it.copy(
                    isRsvpSubmitting = false,
                    message = (result as? AppResult.Failure)?.error?.toUserMessage(),
                )
            }
        }
    }

    fun addComment() {
        val current = mutableState.value
        if (current.isCommentSubmitting) return
        val error = IdeaInputValidator.commentError(current.commentInput)
        if (error != null) {
            mutableState.update { it.copy(commentError = error) }
            return
        }
        mutableState.update { it.copy(isCommentSubmitting = true, message = null) }
        viewModelScope.launch {
            when (
                val result = ideaRepository.addComment(
                    groupId,
                    ideaId,
                    current.commentInput.trim(),
                )
            ) {
                is AppResult.Success -> mutableState.update {
                    it.copy(isCommentSubmitting = false, commentInput = "")
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isCommentSubmitting = false,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun deleteComment(commentId: String) {
        val current = mutableState.value
        if (current.deletingCommentId != null) return
        mutableState.update { it.copy(deletingCommentId = commentId, message = null) }
        viewModelScope.launch {
            val result = ideaRepository.deleteComment(groupId, ideaId, commentId)
            mutableState.update {
                it.copy(
                    deletingCommentId = null,
                    message = (result as? AppResult.Failure)?.error?.toUserMessage(),
                )
            }
        }
    }

    fun deleteIdea() {
        val current = mutableState.value
        if (current.isDeletingIdea) return
        mutableState.update { it.copy(isDeletingIdea = true, message = null) }
        viewModelScope.launch {
            when (val result = ideaRepository.deleteIdea(groupId, ideaId)) {
                is AppResult.Success -> {
                    mutableState.update { it.copy(isDeletingIdea = false, ideaDeleted = true) }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isDeletingIdea = false,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }

    fun canEditIdea(): Boolean {
        val state = mutableState.value
        val idea = (state.idea as? LoadState.Content)?.data ?: return false
        return state.currentUserId == idea.createdBy
    }

    fun canDeleteIdea(): Boolean {
        val state = mutableState.value
        val idea = (state.idea as? LoadState.Content)?.data ?: return false
        return state.currentUserId == idea.createdBy ||
            state.currentUserId == state.group?.adminId
    }

    fun canDeleteComment(comment: IdeaComment): Boolean =
        comment.createdBy == mutableState.value.currentUserId ||
            mutableState.value.group?.adminId == mutableState.value.currentUserId

    private fun observeHeader() {
        viewModelScope.launch {
            combine(
                groupRepository.observeGroup(groupId),
                groupRepository.observeMembers(groupId),
            ) { group, members -> group to members }
                .collect { (groupResult, memberResult) ->
                    mutableState.update {
                        it.copy(
                            group = (groupResult as? AppResult.Success)?.value,
                            members = (memberResult as? AppResult.Success)?.value.orEmpty(),
                            message = if (groupResult is AppResult.Failure) {
                                groupResult.error.toUserMessage()
                            } else {
                                it.message
                            },
                        )
                    }
                }
        }
    }

    private fun observeIdea() {
        viewModelScope.launch {
            ideaRepository.observeIdea(groupId, ideaId).collect { result ->
                when (result) {
                    is AppResult.Success -> {
                        val idea = result.value.copy(
                            creatorSnapshot = resolveAvatar(result.value.creatorSnapshot),
                        )
                        mutableState.update {
                            it.copy(
                                idea = LoadState.Content(
                                    idea,
                                    idea.hasPendingWrites,
                                ),
                            )
                        }
                        resolveImage(idea.media)
                        resolveCompletionPhoto(idea.completion?.photo)
                    }
                    is AppResult.Failure -> mutableState.update {
                        it.copy(
                            idea = LoadState.Error(
                                result.error,
                                result.error == AppError.NETWORK_UNAVAILABLE,
                            ),
                            ideaDeleted = result.error == AppError.NOT_FOUND ||
                                result.error == AppError.PERMISSION_DENIED,
                        )
                    }
                }
            }
        }
    }

    private fun observeDiscussion() {
        viewModelScope.launch {
            ideaRepository.observeReactions(groupId, ideaId).collect { result ->
                if (result is AppResult.Success) {
                    mutableState.update {
                        it.copy(
                            reactions = result.value.items.map { reaction ->
                                reaction.copy(
                                    userSnapshot = resolveAvatar(reaction.userSnapshot),
                                )
                            },
                        )
                    }
                }
            }
        }
        viewModelScope.launch {
            ideaRepository.observeComments(groupId, ideaId).collect { result ->
                mutableState.update {
                    when (result) {
                        is AppResult.Success -> {
                            val comments = result.value.items.map { comment ->
                                comment.copy(
                                    creatorSnapshot = resolveAvatar(comment.creatorSnapshot),
                                )
                            }
                            it.copy(
                                comments = if (comments.isEmpty()) {
                                    LoadState.Empty("还没有评论")
                                } else {
                                    LoadState.Content(
                                        comments,
                                        result.value.hasPendingWrites,
                                    )
                                },
                            )
                        }
                        is AppResult.Failure -> it.copy(
                            comments = LoadState.Error(result.error, true),
                        )
                    }
                }
            }
        }
    }

    private fun observeRsvps() {
        viewModelScope.launch {
            ideaRepository.observeRsvps(groupId, ideaId).collect { result ->
                if (result is AppResult.Success) {
                    mutableState.update {
                        it.copy(
                            rsvps = result.value.items.map { rsvp ->
                                rsvp.copy(
                                    userSnapshot = resolveAvatar(rsvp.userSnapshot),
                                )
                            },
                        )
                    }
                }
            }
        }
    }

    private suspend fun resolveAvatar(snapshot: MemberSnapshot): MemberSnapshot {
        val path = snapshot.avatarPath ?: return snapshot
        snapshot.avatarUrl?.let { return snapshot }
        avatarUrls[path]?.let { return snapshot.copy(avatarUrl = it) }
        val result = avatarRepository.getDownloadUrl(path)
        return if (result is AppResult.Success) {
            avatarUrls[path] = result.value
            snapshot.copy(avatarUrl = result.value)
        } else {
            snapshot
        }
    }

    private fun resolveImage(media: IdeaMedia?) {
        val path = media?.storagePath
        if (path == null || path == resolvedImagePath) return
        resolvedImagePath = path
        viewModelScope.launch {
            val result = imageRepository.getDownloadUrl(path)
            if (result is AppResult.Success && resolvedImagePath == path) {
                mutableState.update { it.copy(imageUrl = result.value) }
            }
        }
    }

    private fun resolveCompletionPhoto(media: IdeaMedia?) {
        val path = media?.storagePath
        if (path == null) {
            resolvedCompletionPhotoPath = null
            mutableState.update { it.copy(completionPhotoUrl = null) }
            return
        }
        if (path == resolvedCompletionPhotoPath) return
        resolvedCompletionPhotoPath = path
        viewModelScope.launch {
            val result = imageRepository.getDownloadUrl(path)
            if (result is AppResult.Success && resolvedCompletionPhotoPath == path) {
                mutableState.update { it.copy(completionPhotoUrl = result.value) }
            }
        }
    }
}
