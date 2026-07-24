package com.example.nextlist.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.domain.model.PendingNotificationTarget
import com.example.nextlist.domain.repository.PendingNotificationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class NotificationNavigationViewModel @Inject constructor(
    private val repository: PendingNotificationRepository,
) : ViewModel() {
    val pendingTarget: StateFlow<PendingNotificationTarget?> = repository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    fun consume(messageId: String) {
        viewModelScope.launch { repository.consume(messageId) }
    }
}
