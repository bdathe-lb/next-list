package com.example.nextlist.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.nextlist.domain.model.AccountSession
import com.example.nextlist.domain.usecase.ObserveAccountSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class AccountSessionViewModel @Inject constructor(
    observeAccountSession: ObserveAccountSessionUseCase,
) : ViewModel() {
    private val retrySignal = MutableStateFlow(0)

    val session = retrySignal
        .flatMapLatest { observeAccountSession() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = AccountSession.Loading,
        )

    fun retry() {
        retrySignal.update(Int::inc)
    }
}
