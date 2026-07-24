package com.example.nextlist.feature.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.core.result.toUserMessage
import com.example.nextlist.domain.model.NotificationPreferenceType
import com.example.nextlist.domain.model.NotificationPreferences
import com.example.nextlist.domain.model.NotificationPreferencesSnapshot
import com.example.nextlist.domain.model.withValue
import com.example.nextlist.domain.repository.NotificationPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationSettingsUiState(
    val content: LoadState<NotificationPreferences> = LoadState.Loading,
    val isSaving: Boolean = false,
    val isFromCache: Boolean = false,
    val hasPendingWrites: Boolean = false,
    val message: String? = null,
    val canRetrySave: Boolean = false,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModel @Inject constructor(
    private val repository: NotificationPreferencesRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(NotificationSettingsUiState())
    val uiState = mutableState.asStateFlow()
    private val retrySignal = MutableStateFlow(0)
    private var retryPreferences: NotificationPreferences? = null

    init {
        viewModelScope.launch {
            retrySignal
                .flatMapLatest { repository.observe() }
                .collectLatest(::onSnapshot)
        }
    }

    fun toggle(type: NotificationPreferenceType, enabled: Boolean) {
        val current = (mutableState.value.content as? LoadState.Content)?.data ?: return
        if (mutableState.value.isSaving) return
        save(current.withValue(type, enabled))
    }

    fun retryLoad() {
        mutableState.update { it.copy(content = LoadState.Loading, message = null) }
        retrySignal.update(Int::inc)
    }

    fun retrySave() {
        retryPreferences?.let(::save)
    }

    private fun save(preferences: NotificationPreferences) {
        mutableState.update {
            it.copy(
                content = LoadState.Content(preferences),
                isSaving = true,
                message = "正在保存通知设置…",
                canRetrySave = false,
            )
        }
        viewModelScope.launch {
            when (val result = repository.update(preferences)) {
                is AppResult.Success -> {
                    retryPreferences = null
                    mutableState.update {
                        it.copy(
                            isSaving = false,
                            message = "通知设置已保存",
                            canRetrySave = false,
                        )
                    }
                }
                is AppResult.Failure -> {
                    retryPreferences = preferences
                    mutableState.update {
                        it.copy(
                            isSaving = false,
                            message = result.error.toUserMessage(),
                            canRetrySave = true,
                        )
                    }
                }
            }
        }
    }

    private fun onSnapshot(result: AppResult<NotificationPreferencesSnapshot>) {
        when (result) {
            is AppResult.Success -> mutableState.update {
                it.copy(
                    content = LoadState.Content(
                        data = result.value.preferences,
                        hasPendingWrites = result.value.hasPendingWrites,
                    ),
                    isFromCache = result.value.isFromCache,
                    hasPendingWrites = result.value.hasPendingWrites,
                )
            }
            is AppResult.Failure -> mutableState.update {
                it.copy(
                    content = LoadState.Error(result.error, canRetry = true),
                    isSaving = false,
                    message = null,
                )
            }
        }
    }
}
