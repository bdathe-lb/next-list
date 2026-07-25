package com.example.nextlist.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppOperation
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.domain.repository.AuthRepository
import com.example.nextlist.domain.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class DeleteAccountUiState(
    val showConfirmDialog: Boolean = false,
    val isDeleting: Boolean = false,
    val adminBlockedMessage: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class DeleteAccountViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val deviceRepository: DeviceRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(DeleteAccountUiState())
    val uiState: StateFlow<DeleteAccountUiState> = mutableState.asStateFlow()

    fun requestDelete() {
        mutableState.update {
            it.copy(showConfirmDialog = true, adminBlockedMessage = null, errorMessage = null)
        }
    }

    fun dismissDialog() {
        mutableState.update { it.copy(showConfirmDialog = false) }
    }

    fun confirmDelete() {
        if (mutableState.value.isDeleting) return
        mutableState.update { it.copy(showConfirmDialog = false, isDeleting = true, errorMessage = null) }
        viewModelScope.launch {
            withTimeoutOrNull(1_500) { deviceRepository.unregisterCurrentDevice() }
            val requestId = UUID.randomUUID().toString()
            when (val result = authRepository.deleteAccount(requestId)) {
                is AppResult.Success -> Unit // auth state change navigates away
                is AppResult.Failure -> {
                    val isAdminBlocked = result.error == AppError.ADMIN_CANNOT_LEAVE
                    mutableState.update {
                        it.copy(
                            isDeleting = false,
                            adminBlockedMessage = if (isAdminBlocked) {
                                result.error.toUserMessage(AppOperation.ACCOUNT_DELETION)
                            } else null,
                            errorMessage = if (!isAdminBlocked) {
                                result.error.toUserMessage(AppOperation.ACCOUNT_DELETION)
                            } else null,
                        )
                    }
                }
            }
        }
    }
}
