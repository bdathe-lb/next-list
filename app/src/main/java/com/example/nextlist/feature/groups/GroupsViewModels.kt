package com.example.nextlist.feature.groups

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.core.validation.GroupInputValidator
import com.example.nextlist.domain.model.Group
import com.example.nextlist.domain.model.GroupMember
import com.example.nextlist.domain.model.GroupRole
import com.example.nextlist.domain.model.GroupSummary
import com.example.nextlist.domain.model.InviteCredential
import com.example.nextlist.domain.model.InviteCredentialKind
import com.example.nextlist.domain.model.InviteCredentials
import com.example.nextlist.domain.model.InvitePreview
import com.example.nextlist.domain.repository.GroupRepository
import com.example.nextlist.domain.repository.AvatarRepository
import com.example.nextlist.domain.repository.PendingInviteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class GroupsViewModel @Inject constructor(
    repository: GroupRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {
    private val retryKey = MutableStateFlow(0)
    val uiState: StateFlow<LoadState<List<GroupSummary>>> = retryKey
        .flatMapLatest { repository.observeMyGroups() }
        .map { result ->
            when (result) {
                is AppResult.Success -> if (result.value.isEmpty()) {
                    LoadState.Empty("还没有小组")
                } else {
                    LoadState.Content(
                        result.value.map { group ->
                            group.copy(
                                members = resolveMemberSnapshots(
                                    group.members,
                                    avatarRepository,
                                ),
                            )
                        },
                    )
                }
                is AppResult.Failure -> LoadState.Error(
                    result.error,
                    result.error != AppError.PERMISSION_DENIED,
                )
            }
        }
        .onStart { emit(LoadState.Loading) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LoadState.Loading,
        )

    fun retry() {
        retryKey.update { it + 1 }
    }
}

data class CreateGroupUiState(
    val name: String = "",
    val nameError: String? = null,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val errorKind: AppError? = null,
    val createdGroupId: String? = null,
)

@HiltViewModel
class CreateGroupViewModel @Inject constructor(
    private val repository: GroupRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CreateGroupUiState())
    val uiState = mutableState.asStateFlow()
    private var requestId = UUID.randomUUID().toString()

    fun onNameChanged(value: String) {
        if (mutableState.value.isSubmitting) return
        requestId = UUID.randomUUID().toString()
        mutableState.update {
            it.copy(name = value, nameError = null, message = null, errorKind = null)
        }
    }

    fun submit() {
        val current = mutableState.value
        if (current.isSubmitting || current.createdGroupId != null) return
        val error = GroupInputValidator.nameError(current.name)
        if (error != null) {
            mutableState.update { it.copy(nameError = error) }
            return
        }
        mutableState.update {
            it.copy(isSubmitting = true, message = null, errorKind = null)
        }
        viewModelScope.launch {
            when (
                val result = repository.createGroup(
                    GroupInputValidator.normalizedName(current.name),
                    requestId,
                )
            ) {
                is AppResult.Success -> mutableState.update {
                    it.copy(isSubmitting = false, createdGroupId = result.value)
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isSubmitting = false,
                        message = result.error.toUserMessage(),
                        errorKind = result.error,
                    )
                }
            }
        }
    }
}

data class JoinGroupUiState(
    val preview: LoadState<InvitePreview> = LoadState.Loading,
    val isSubmitting: Boolean = false,
    val message: String? = null,
    val errorKind: AppError? = null,
    val joinedGroupId: String? = null,
    val isDirect: Boolean = false,
    val declined: Boolean = false,
)

@HiltViewModel
class JoinGroupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GroupRepository,
    private val pendingInviteRepository: PendingInviteRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {
    private val credential = InviteCredential(
        kind = when (savedStateHandle.get<String>("kind")) {
            "token" -> InviteCredentialKind.TOKEN
            "direct" -> InviteCredentialKind.DIRECT
            else -> InviteCredentialKind.CODE
        },
        value = savedStateHandle.get<String>("value").orEmpty(),
    )
    private val mutableState = MutableStateFlow(
        JoinGroupUiState(isDirect = credential.kind == InviteCredentialKind.DIRECT),
    )
    val uiState = mutableState.asStateFlow()

    init {
        loadPreview()
    }

    fun loadPreview() {
        if (credential.value.isBlank()) {
            mutableState.update {
                it.copy(preview = LoadState.Error(AppError.INVITE_INVALID, false))
            }
            return
        }
        mutableState.update {
            it.copy(preview = LoadState.Loading, message = null, errorKind = null)
        }
        viewModelScope.launch {
            when (val result = repository.previewInvite(credential)) {
                is AppResult.Success -> {
                    val preview = result.value.copy(
                        members = resolveMemberSnapshots(
                            result.value.members,
                            avatarRepository,
                        ),
                    )
                    mutableState.update {
                        it.copy(preview = LoadState.Content(preview))
                    }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        preview = LoadState.Error(
                            result.error,
                            result.error == AppError.NETWORK_UNAVAILABLE,
                        ),
                    )
                }
            }
        }
    }

    fun accept() {
        val current = mutableState.value
        if (current.isSubmitting || current.joinedGroupId != null) return
        if (current.preview !is LoadState.Content) return
        mutableState.update {
            it.copy(isSubmitting = true, message = null, errorKind = null)
        }
        viewModelScope.launch {
            val result = if (credential.kind == InviteCredentialKind.DIRECT) {
                repository.acceptDirectInvite(credential.value)
            } else {
                repository.acceptInvite(credential)
            }
            when (result) {
                is AppResult.Success -> {
                    pendingInviteRepository.clear()
                    mutableState.update {
                        it.copy(isSubmitting = false, joinedGroupId = result.value)
                    }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isSubmitting = false,
                        message = result.error.toUserMessage(),
                        errorKind = result.error,
                    )
                }
            }
        }
    }

    fun decline() {
        val current = mutableState.value
        if (!current.isDirect || current.isSubmitting || current.declined) return
        mutableState.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.declineDirectInvite(credential.value)) {
                is AppResult.Success -> {
                    pendingInviteRepository.clear()
                    mutableState.update {
                        it.copy(
                            isSubmitting = false,
                            declined = true,
                            message = "已拒绝邀请",
                        )
                    }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(isSubmitting = false, message = result.error.toUserMessage())
                }
            }
        }
    }
}

data class GroupDetailUiState(
    val group: LoadState<Group> = LoadState.Loading,
    val members: List<GroupMember> = emptyList(),
    val currentUserId: String? = null,
    val accessLostMessage: String? = null,
)

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    repository: GroupRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()

    val uiState: StateFlow<GroupDetailUiState> = combine(
        repository.observeGroup(groupId),
        repository.observeMembers(groupId),
    ) { groupResult, memberResult ->
        val groupState = when (groupResult) {
            is AppResult.Success -> LoadState.Content(groupResult.value)
            is AppResult.Failure -> LoadState.Error(
                groupResult.error,
                groupResult.error == AppError.NETWORK_UNAVAILABLE,
            )
        }
        GroupDetailUiState(
            group = groupState,
            members = resolveMembers(
                (memberResult as? AppResult.Success)?.value.orEmpty(),
                avatarRepository,
            ),
            currentUserId = repository.currentUserId(),
            accessLostMessage = when {
                groupResult is AppResult.Failure &&
                    groupResult.error == AppError.PERMISSION_DENIED ->
                    "你已不在这个小组中"
                groupResult is AppResult.Failure &&
                    groupResult.error == AppError.GROUP_DISSOLVED ->
                    "小组已解散"
                else -> null
            },
        )
    }.onStart { emit(GroupDetailUiState()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = GroupDetailUiState(),
        )
}

data class MembersUiState(
    val group: Group? = null,
    val members: List<GroupMember> = emptyList(),
    val loading: Boolean = true,
    val submittingUserId: String? = null,
    val isLeaving: Boolean = false,
    val message: String? = null,
    val exitGroup: Boolean = false,
    val currentUserId: String? = null,
)

@HiltViewModel
class MembersViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GroupRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val mutableState = MutableStateFlow(
        MembersUiState(currentUserId = repository.currentUserId()),
    )
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repository.observeGroup(groupId),
                repository.observeMembers(groupId),
            ) { group, members -> group to members }
                .collect { (groupResult, membersResult) ->
                    if (
                        groupResult is AppResult.Failure &&
                        groupResult.error == AppError.PERMISSION_DENIED
                    ) {
                        mutableState.update {
                            it.copy(
                                loading = false,
                                exitGroup = true,
                                message = "你已不在这个小组中",
                            )
                        }
                        return@collect
                    }
                    val resolvedMembers = resolveMembers(
                        (membersResult as? AppResult.Success)?.value.orEmpty(),
                        avatarRepository,
                    )
                    mutableState.update {
                        it.copy(
                            group = (groupResult as? AppResult.Success)?.value,
                            members = resolvedMembers,
                            loading = false,
                        )
                    }
                }
        }
    }

    fun remove(userId: String) = submitForUser(userId) {
        repository.removeMember(groupId, userId)
    }

    fun transfer(userId: String) = submitForUser(userId) {
        repository.transferAdmin(groupId, userId)
    }

    fun leave() {
        val current = mutableState.value
        if (current.isLeaving || current.submittingUserId != null) return
        mutableState.update { it.copy(isLeaving = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.leaveGroup(groupId)) {
                is AppResult.Success -> mutableState.update {
                    it.copy(isLeaving = false, exitGroup = true, message = "已退出小组")
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(isLeaving = false, message = result.error.toUserMessage())
                }
            }
        }
    }

    private fun submitForUser(
        userId: String,
        action: suspend () -> AppResult<Unit>,
    ) {
        val current = mutableState.value
        if (current.submittingUserId != null || current.isLeaving) return
        mutableState.update { it.copy(submittingUserId = userId, message = null) }
        viewModelScope.launch {
            when (val result = action()) {
                is AppResult.Success -> mutableState.update {
                    it.copy(submittingUserId = null)
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        submittingUserId = null,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }
}

data class InviteUiState(
    val credentials: LoadState<InviteCredentials> = LoadState.Loading,
    val isRotating: Boolean = false,
    val email: String = "",
    val isSendingDirect: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class InviteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GroupRepository,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val mutableState = MutableStateFlow(InviteUiState())
    val uiState = mutableState.asStateFlow()
    private var rotateRequestId = UUID.randomUUID().toString()

    init {
        load()
    }

    fun load() {
        mutableState.update { it.copy(credentials = LoadState.Loading, message = null) }
        viewModelScope.launch {
            when (val result = repository.getOrCreateInvite(groupId)) {
                is AppResult.Success -> mutableState.update {
                    it.copy(credentials = LoadState.Content(result.value))
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(credentials = LoadState.Error(result.error, true))
                }
            }
        }
    }

    fun rotate() {
        val current = mutableState.value
        if (current.isRotating || current.isSendingDirect) return
        mutableState.update { it.copy(isRotating = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.rotateInvite(groupId, rotateRequestId)) {
                is AppResult.Success -> {
                    rotateRequestId = UUID.randomUUID().toString()
                    mutableState.update {
                        it.copy(
                            isRotating = false,
                            credentials = LoadState.Content(result.value),
                            message = "已生成新的邀请，旧邀请立即失效",
                        )
                    }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(isRotating = false, message = result.error.toUserMessage())
                }
            }
        }
    }

    fun onEmailChanged(value: String) {
        if (mutableState.value.isSendingDirect) return
        mutableState.update { it.copy(email = value, message = null) }
    }

    fun sendDirect() {
        val current = mutableState.value
        if (current.isSendingDirect || current.isRotating || current.email.isBlank()) return
        mutableState.update { it.copy(isSendingDirect = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.sendDirectInvite(groupId, current.email.trim())) {
                is AppResult.Success -> mutableState.update {
                    it.copy(
                        isSendingDirect = false,
                        email = "",
                        message = "如果对方已注册，将收到邀请。",
                    )
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isSendingDirect = false,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }
}

data class GroupSettingsUiState(
    val group: Group? = null,
    val name: String = "",
    val nameError: String? = null,
    val confirmationName: String = "",
    val isRenaming: Boolean = false,
    val isDissolving: Boolean = false,
    val isLeaving: Boolean = false,
    val message: String? = null,
    val exitGroup: Boolean = false,
)

@HiltViewModel
class GroupSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: GroupRepository,
) : ViewModel() {
    private val groupId = savedStateHandle.get<String>("groupId").orEmpty()
    private val mutableState = MutableStateFlow(GroupSettingsUiState())
    val uiState = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeGroup(groupId).collect { result ->
                when (result) {
                    is AppResult.Success -> mutableState.update {
                        it.copy(
                            group = result.value,
                            name = if (it.group == null) result.value.name else it.name,
                        )
                    }
                    is AppResult.Failure -> mutableState.update {
                        it.copy(
                            message = result.error.toUserMessage(),
                            exitGroup = result.error == AppError.PERMISSION_DENIED ||
                                result.error == AppError.GROUP_DISSOLVED,
                        )
                    }
                }
            }
        }
    }

    fun onNameChanged(value: String) {
        if (mutableState.value.isRenaming) return
        mutableState.update { it.copy(name = value, nameError = null, message = null) }
    }

    fun onConfirmationChanged(value: String) {
        if (mutableState.value.isDissolving) return
        mutableState.update { it.copy(confirmationName = value, message = null) }
    }

    fun rename() {
        val current = mutableState.value
        if (current.isRenaming || current.isDissolving) return
        val error = GroupInputValidator.nameError(current.name)
        if (error != null) {
            mutableState.update { it.copy(nameError = error) }
            return
        }
        mutableState.update { it.copy(isRenaming = true, message = null) }
        viewModelScope.launch {
            when (
                val result = repository.updateGroupName(
                    groupId,
                    GroupInputValidator.normalizedName(current.name),
                )
            ) {
                is AppResult.Success -> mutableState.update {
                    it.copy(isRenaming = false, message = "小组名称已更新")
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(isRenaming = false, message = result.error.toUserMessage())
                }
            }
        }
    }

    fun dissolve() {
        val current = mutableState.value
        if (current.isDissolving || current.isRenaming) return
        if (current.confirmationName != current.group?.name) {
            mutableState.update { it.copy(message = "请输入完整的小组名称确认解散") }
            return
        }
        mutableState.update { it.copy(isDissolving = true, message = null) }
        viewModelScope.launch {
            when (
                val result = repository.dissolveGroup(
                    groupId,
                    current.confirmationName,
                )
            ) {
                is AppResult.Success -> mutableState.update {
                    it.copy(
                        isDissolving = false,
                        exitGroup = true,
                        message = "小组已解散",
                    )
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(isDissolving = false, message = result.error.toUserMessage())
                }
            }
        }
    }

    fun leave() {
        val current = mutableState.value
        if (current.isLeaving || current.isRenaming || current.isDissolving) return
        mutableState.update { it.copy(isLeaving = true, message = null) }
        viewModelScope.launch {
            when (val result = repository.leaveGroup(groupId)) {
                is AppResult.Success -> mutableState.update {
                    it.copy(isLeaving = false, exitGroup = true, message = "已退出小组")
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(isLeaving = false, message = result.error.toUserMessage())
                }
            }
        }
    }

    fun isCurrentUserAdmin(): Boolean =
        mutableState.value.group?.adminId == repository.currentUserId()
}

private suspend fun resolveMemberSnapshots(
    members: List<com.example.nextlist.domain.model.MemberSnapshot>,
    avatarRepository: AvatarRepository,
): List<com.example.nextlist.domain.model.MemberSnapshot> = coroutineScope {
    members.map { member ->
        async {
            val url = member.avatarPath?.let { path ->
                (avatarRepository.getDownloadUrl(path) as? AppResult.Success)?.value
            }
            member.copy(avatarUrl = url)
        }
    }.map { it.await() }
}

private suspend fun resolveMembers(
    members: List<GroupMember>,
    avatarRepository: AvatarRepository,
): List<GroupMember> = coroutineScope {
    members.map { member ->
        async {
            val url = member.avatarPath?.let { path ->
                (avatarRepository.getDownloadUrl(path) as? AppResult.Success)?.value
            }
            member.copy(avatarUrl = url)
        }
    }.map { it.await() }
}
