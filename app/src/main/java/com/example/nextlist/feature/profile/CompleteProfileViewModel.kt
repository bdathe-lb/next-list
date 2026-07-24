package com.example.nextlist.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppOperation
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.core.validation.AccountInputValidator
import com.example.nextlist.domain.model.AuthUser
import com.example.nextlist.domain.repository.AuthRepository
import com.example.nextlist.domain.repository.AvatarRepository
import com.example.nextlist.domain.repository.UserProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CompleteProfileUiState(
    val nickname: String = "",
    val nicknameError: String? = null,
    val selectedAvatarUri: String? = null,
    val isSubmitting: Boolean = false,
    val statusText: String? = null,
    val message: String? = null,
)

@HiltViewModel
class CompleteProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CompleteProfileUiState())
    val uiState: StateFlow<CompleteProfileUiState> = mutableState.asStateFlow()
    private var user: AuthUser? = null
    private var pendingAvatarPath: String? = null

    fun onUserChanged(updatedUser: AuthUser) {
        user = updatedUser
        if (mutableState.value.nickname.isBlank()) {
            mutableState.update {
                it.copy(nickname = updatedUser.suggestedNickname.orEmpty())
            }
        }
    }

    fun onNicknameChanged(value: String) {
        mutableState.update { it.copy(nickname = value, nicknameError = null, message = null) }
    }

    fun onAvatarSelected(uri: String?) {
        pendingAvatarPath = null
        mutableState.update {
            it.copy(selectedAvatarUri = uri, message = null, statusText = null)
        }
    }

    fun submit() {
        val current = mutableState.value
        val sessionUser = user ?: return
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
            val refreshedUser = when (val refresh = authRepository.refreshCurrentUser()) {
                is AppResult.Success -> refresh.value
                is AppResult.Failure -> {
                    fail(refresh.error.toUserMessage(AppOperation.PROFILE_SAVE))
                    return@launch
                }
            }

            var avatarPath = pendingAvatarPath
            if (avatarPath == null && current.selectedAvatarUri != null) {
                mutableState.update { it.copy(statusText = "正在处理并上传头像…") }
                when (
                    val upload = avatarRepository.uploadAvatar(
                        sessionUser.id,
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
                val save = userProfileRepository.createProfile(
                    user = refreshedUser,
                    nickname = AccountInputValidator.normalizedNickname(current.nickname),
                    avatarPath = avatarPath,
                )
            ) {
                is AppResult.Success -> Unit
                is AppResult.Failure -> fail(
                    save.error.toUserMessage(AppOperation.PROFILE_SAVE),
                )
            }
        }
    }

    private fun fail(message: String) {
        mutableState.update {
            it.copy(isSubmitting = false, statusText = null, message = message)
        }
    }
}
