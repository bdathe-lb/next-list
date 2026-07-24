package com.example.nextlist.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppOperation
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.domain.model.AccountSession
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

data class ProfileUiState(
    val avatarUrl: String? = null,
    val avatarLoading: Boolean = false,
    val isSendingVerification: Boolean = false,
    val isRefreshingVerification: Boolean = false,
    val isSigningOut: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
    private val avatarRepository: AvatarRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = mutableState.asStateFlow()
    private var loadedAvatarPath: String? = null

    fun loadAvatar(storagePath: String?) {
        if (storagePath == loadedAvatarPath) return
        loadedAvatarPath = storagePath
        if (storagePath == null) {
            mutableState.update { it.copy(avatarUrl = null, avatarLoading = false) }
            return
        }
        mutableState.update { it.copy(avatarLoading = true) }
        viewModelScope.launch {
            when (val result = avatarRepository.getDownloadUrl(storagePath)) {
                is AppResult.Success -> mutableState.update {
                    it.copy(avatarUrl = result.value, avatarLoading = false)
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(avatarUrl = null, avatarLoading = false)
                }
            }
        }
    }

    fun sendVerificationEmail() {
        if (mutableState.value.isSendingVerification) return
        mutableState.update {
            it.copy(isSendingVerification = true, message = null)
        }
        viewModelScope.launch {
            when (val result = authRepository.sendEmailVerification()) {
                is AppResult.Success -> mutableState.update {
                    it.copy(
                        isSendingVerification = false,
                        message = "验证邮件已发送，请前往邮箱查看",
                    )
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isSendingVerification = false,
                        message = result.error.toUserMessage(AppOperation.EMAIL_VERIFICATION),
                    )
                }
            }
        }
    }

    fun refreshVerificationStatus(session: AccountSession.SignedIn) {
        if (mutableState.value.isRefreshingVerification) return
        mutableState.update {
            it.copy(isRefreshingVerification = true, message = null)
        }
        viewModelScope.launch {
            when (val refresh = authRepository.refreshCurrentUser()) {
                is AppResult.Success -> {
                    when (
                        val update = userProfileRepository.updateEmailVerified(
                            userId = refresh.value.id,
                            emailVerified = refresh.value.emailVerified,
                        )
                    ) {
                        is AppResult.Success -> mutableState.update {
                            it.copy(
                                isRefreshingVerification = false,
                                message = if (refresh.value.emailVerified) {
                                    "邮箱已验证"
                                } else {
                                    "邮箱暂未验证，请完成邮件中的步骤后再刷新"
                                },
                            )
                        }
                        is AppResult.Failure -> mutableState.update {
                            it.copy(
                                isRefreshingVerification = false,
                                message = update.error.toUserMessage(
                                    AppOperation.EMAIL_VERIFICATION,
                                ),
                            )
                        }
                    }
                }
                is AppResult.Failure -> mutableState.update {
                    it.copy(
                        isRefreshingVerification = false,
                        message = refresh.error.toUserMessage(
                            AppOperation.EMAIL_VERIFICATION,
                        ),
                    )
                }
            }
        }
    }

    fun signOut() {
        if (mutableState.value.isSigningOut) return
        mutableState.update { it.copy(isSigningOut = true, message = null) }
        viewModelScope.launch {
            val result = authRepository.signOut()
            if (result is AppResult.Failure) {
                mutableState.update {
                    it.copy(
                        isSigningOut = false,
                        message = result.error.toUserMessage(),
                    )
                }
            }
        }
    }
}
