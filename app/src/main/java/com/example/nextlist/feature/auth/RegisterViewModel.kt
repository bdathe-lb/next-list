package com.example.nextlist.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppOperation
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.core.validation.AccountInputValidator
import com.example.nextlist.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val nickname: String = "",
    val email: String = "",
    val password: String = "",
    val passwordConfirmation: String = "",
    val nicknameError: String? = null,
    val emailError: String? = null,
    val passwordError: String? = null,
    val passwordConfirmationError: String? = null,
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class RegisterViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = mutableState.asStateFlow()

    fun onNicknameChanged(value: String) {
        mutableState.update { it.copy(nickname = value, nicknameError = null, message = null) }
    }

    fun onEmailChanged(value: String) {
        mutableState.update { it.copy(email = value, emailError = null, message = null) }
    }

    fun onPasswordChanged(value: String) {
        mutableState.update {
            it.copy(
                password = value,
                passwordError = null,
                passwordConfirmationError = null,
                message = null,
            )
        }
    }

    fun onPasswordConfirmationChanged(value: String) {
        mutableState.update {
            it.copy(
                passwordConfirmation = value,
                passwordConfirmationError = null,
                message = null,
            )
        }
    }

    fun togglePasswordVisibility() {
        mutableState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun submit() {
        val current = mutableState.value
        if (current.isSubmitting) return
        val nicknameError = AccountInputValidator.nicknameError(current.nickname)
        val emailError = AccountInputValidator.emailError(current.email)
        val passwordError = AccountInputValidator.newPasswordError(current.password)
        val confirmationError = AccountInputValidator.passwordConfirmationError(
            current.password,
            current.passwordConfirmation,
        )
        if (
            nicknameError != null ||
            emailError != null ||
            passwordError != null ||
            confirmationError != null
        ) {
            mutableState.update {
                it.copy(
                    nicknameError = nicknameError,
                    emailError = emailError,
                    passwordError = passwordError,
                    passwordConfirmationError = confirmationError,
                    message = null,
                )
            }
            return
        }

        mutableState.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            val result = authRepository.register(
                email = AccountInputValidator.normalizedEmail(current.email),
                password = current.password,
                nickname = AccountInputValidator.normalizedNickname(current.nickname),
            )
            if (result is AppResult.Failure) {
                mutableState.update {
                    it.copy(
                        isSubmitting = false,
                        message = result.error.toUserMessage(AppOperation.REGISTER),
                    )
                }
            }
        }
    }
}
