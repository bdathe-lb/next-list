package com.example.nextlist.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppOperation
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.core.validation.AccountInputValidator
import com.example.nextlist.domain.model.AccountSession
import com.example.nextlist.domain.repository.AvatarRepository
import com.example.nextlist.domain.repository.UserProfileRepository
import com.example.nextlist.domain.usecase.ObserveAccountSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditProfileUiState(
    val isLoading: Boolean = true,
    val nickname: String = "",
    val nicknameError: String? = null,
    val originalAvatarPath: String? = null,
    val avatarUrl: String? = null,
    val selectedAvatarUri: String? = null,
    val removeAvatar: Boolean = false,
    val isSubmitting: Boolean = false,
    val statusText: String? = null,
    val message: String? = null,
    val completed: Boolean = false,
)

@HiltViewModel
class EditProfileViewModel @Inject constructor(
    observeAccountSession: ObserveAccountSessionUseCase,
    private val userProfileRepository: UserProfileRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(EditProfileUiState())
    val uiState: StateFlow<EditProfileUiState> = mutableState.asStateFlow()
    private var userId: String? = null
    private var initializedAvatarPath: String? = null
    private var pendingAvatarPath: String? = null
    private var userHasEdited = false

    init {
        viewModelScope.launch {
            observeAccountSession().collect { session ->
                if (session is AccountSession.SignedIn) {
                    userId = session.user.id
                    if (!userHasEdited) {
                        mutableState.update {
                            it.copy(
                                isLoading = false,
                                nickname = session.profile.nickname,
                                originalAvatarPath = session.profile.avatarPath,
                            )
                        }
                    }
                    loadAvatarIfNeeded(session.profile.avatarPath)
                }
            }
        }
    }

    fun onNicknameChanged(value: String) {
        userHasEdited = true
        mutableState.update { it.copy(nickname = value, nicknameError = null, message = null) }
    }

    fun onAvatarSelected(uri: String?) {
        userHasEdited = true
        pendingAvatarPath = null
        mutableState.update {
            it.copy(
                selectedAvatarUri = uri,
                removeAvatar = false,
                message = null,
            )
        }
    }

    fun removeAvatar() {
        userHasEdited = true
        pendingAvatarPath = null
        mutableState.update {
            it.copy(
                selectedAvatarUri = null,
                removeAvatar = true,
                message = null,
            )
        }
    }

    fun submit() {
        val current = mutableState.value
        val currentUserId = userId ?: return
        if (current.isSubmitting) return
        val nicknameError = AccountInputValidator.nicknameError(current.nickname)
        if (nicknameError != null) {
            mutableState.update { it.copy(nicknameError = nicknameError) }
            return
        }

        mutableState.update {
            it.copy(isSubmitting = true, statusText = "正在保存资料…", message = null)
        }
        viewModelScope.launch {
            var avatarPath = when {
                current.removeAvatar -> null
                pendingAvatarPath != null -> pendingAvatarPath
                else -> current.originalAvatarPath
            }

            if (current.selectedAvatarUri != null && pendingAvatarPath == null) {
                mutableState.update { it.copy(statusText = "正在处理并上传头像…") }
                when (
                    val upload = avatarRepository.uploadAvatar(
                        currentUserId,
                        current.selectedAvatarUri,
                    )
                ) {
                    is AppResult.Success -> {
                        avatarPath = upload.value
                        pendingAvatarPath = upload.value
                    }
                    is AppResult.Failure -> {
                        fail(upload.error.toUserMessage(AppOperation.AVATAR_UPLOAD))
                        return@launch
                    }
                }
            }

            mutableState.update { it.copy(statusText = "正在写入资料…") }
            when (
                val save = userProfileRepository.updateProfile(
                    userId = currentUserId,
                    nickname = AccountInputValidator.normalizedNickname(current.nickname),
                    avatarPath = avatarPath,
                )
            ) {
                is AppResult.Success -> {
                    val oldPath = current.originalAvatarPath
                    if (oldPath != null && oldPath != avatarPath) {
                        avatarRepository.deleteAvatar(oldPath)
                    }
                    mutableState.update {
                        it.copy(
                            isSubmitting = false,
                            statusText = null,
                            completed = true,
                        )
                    }
                }
                is AppResult.Failure -> fail(
                    save.error.toUserMessage(AppOperation.PROFILE_SAVE),
                )
            }
        }
    }

    fun consumeCompletion() {
        mutableState.update { it.copy(completed = false) }
    }

    private fun loadAvatarIfNeeded(storagePath: String?) {
        if (storagePath == null || initializedAvatarPath == storagePath) return
        initializedAvatarPath = storagePath
        viewModelScope.launch {
            val result = avatarRepository.getDownloadUrl(storagePath)
            if (result is AppResult.Success) {
                mutableState.update { it.copy(avatarUrl = result.value) }
            }
        }
    }

    private fun fail(message: String) {
        mutableState.update {
            it.copy(isSubmitting = false, statusText = null, message = message)
        }
    }
}
