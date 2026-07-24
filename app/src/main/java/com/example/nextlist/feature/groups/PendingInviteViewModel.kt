package com.example.nextlist.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.domain.model.PendingInvite
import com.example.nextlist.domain.repository.PendingInviteRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PendingInviteViewModel @Inject constructor(
    private val repository: PendingInviteRepository,
) : ViewModel() {
    val pendingInvite: StateFlow<PendingInvite?> = repository.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    fun clear() {
        viewModelScope.launch { repository.clear() }
    }
}
