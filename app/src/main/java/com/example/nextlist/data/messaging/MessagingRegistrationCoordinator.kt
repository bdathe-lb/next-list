package com.example.nextlist.data.messaging

import com.example.nextlist.domain.repository.AuthRepository
import com.example.nextlist.domain.repository.DeviceRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Singleton
class MessagingRegistrationCoordinator @Inject constructor(
    private val authRepository: AuthRepository,
    private val deviceRepository: DeviceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun start() {
        if (started) return
        started = true
        scope.launch {
            authRepository.observeCurrentUser().collectLatest { user ->
                if (user != null) {
                    deviceRepository.registerCurrentDevice()
                }
            }
        }
    }
}
