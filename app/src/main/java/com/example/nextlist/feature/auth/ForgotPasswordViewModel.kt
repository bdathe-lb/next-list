package com.example.nextlist.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppOperation
import com.example.nextlist.core.result.AppError
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

data class ForgotPasswordUiState(
    val email: String = "",
    val emailError: String? = null,
    val isSubmitting: Boolean = false,
    val emailSent: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = mutableState.asStateFlow()

    fun onEmailChanged(value: String) {
        mutableState.update {
            it.copy(email = value, emailError = null, emailSent = false, message = null)
        }
    }

    fun submit() {
        val current = mutableState.value
        if (current.isSubmitting) return
        val emailError = AccountInputValidator.emailError(current.email)
        if (emailError != null) {
            mutableState.update { it.copy(emailError = emailError) }
            return
        }
        mutableState.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            when (
                val result = authRepository.sendPasswordResetEmail(
                    AccountInputValidator.normalizedEmail(current.email),
                )
            ) {
                is AppResult.Success -> mutableState.update {
                    it.copy(
                        isSubmitting = false,
                        emailSent = true,
                        message = "如果该邮箱已注册，重置邮件会很快送达",
                    )
                }
                is AppResult.Failure -> {
                    if (
                        result.error == AppError.NOT_FOUND ||
                        result.error == AppError.UNAUTHENTICATED
                    ) {
                        mutableState.update {
                            it.copy(
                                isSubmitting = false,
                                emailSent = true,
                                message = "如果该邮箱已注册，重置邮件会很快送达",
                            )
                        }
                    } else {
                        mutableState.update {
                            it.copy(
                                isSubmitting = false,
                                message = result.error.toUserMessage(
                                    AppOperation.PASSWORD_RESET,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
