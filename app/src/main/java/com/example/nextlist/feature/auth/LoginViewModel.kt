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

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val emailError: String? = null,
    val passwordError: String? = null,
    val passwordVisible: Boolean = false,
    val isSubmitting: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = mutableState.asStateFlow()

    fun onEmailChanged(value: String) {
        mutableState.update { it.copy(email = value, emailError = null, message = null) }
    }

    fun onPasswordChanged(value: String) {
        mutableState.update { it.copy(password = value, passwordError = null, message = null) }
    }

    fun togglePasswordVisibility() {
        mutableState.update { it.copy(passwordVisible = !it.passwordVisible) }
    }

    fun submit() {
        val current = mutableState.value
        if (current.isSubmitting) return
        val emailError = AccountInputValidator.emailError(current.email)
        val passwordError = AccountInputValidator.loginPasswordError(current.password)
        if (emailError != null || passwordError != null) {
            mutableState.update {
                it.copy(emailError = emailError, passwordError = passwordError, message = null)
            }
            return
        }

        mutableState.update { it.copy(isSubmitting = true, message = null) }
        viewModelScope.launch {
            val result = authRepository.signIn(
                email = AccountInputValidator.normalizedEmail(current.email),
                password = current.password,
            )
            if (result is AppResult.Failure) {
                mutableState.update {
                    it.copy(
                        isSubmitting = false,
                        message = result.error.toUserMessage(AppOperation.LOGIN),
                    )
                }
            }
        }
    }
}
