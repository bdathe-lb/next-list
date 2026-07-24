package com.example.nextlist.domain.usecase

import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.AccountSession
import com.example.nextlist.domain.repository.AuthRepository
import com.example.nextlist.domain.repository.UserProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveAccountSessionUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val userProfileRepository: UserProfileRepository,
) {
    operator fun invoke(): Flow<AccountSession> =
        authRepository.observeCurrentUser()
            .flatMapLatest { user ->
                if (user == null) {
                    flowOf(AccountSession.SignedOut)
                } else {
                    userProfileRepository.observeProfile(user.id).map { result ->
                        when (result) {
                            is AppResult.Success -> {
                                val profile = result.value
                                if (profile == null) {
                                    AccountSession.NeedsProfile(user)
                                } else {
                                    AccountSession.SignedIn(user, profile)
                                }
                            }
                            is AppResult.Failure -> AccountSession.Error(result.error)
                        }
                    }
                }
            }
            .onStart { emit(AccountSession.Loading) }
}
