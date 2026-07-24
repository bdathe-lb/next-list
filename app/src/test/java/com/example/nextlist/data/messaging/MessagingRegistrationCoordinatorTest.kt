package com.example.nextlist.data.messaging

import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.AuthUser
import com.example.nextlist.domain.repository.AuthRepository
import com.example.nextlist.domain.repository.DeviceRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class MessagingRegistrationCoordinatorTest {
    @Test
    fun `signed in session registers the installation only once when start repeats`() = runBlocking {
        val device = RegistrationDeviceRepository()
        val coordinator = MessagingRegistrationCoordinator(
            authRepository = RegistrationAuthRepository(),
            deviceRepository = device,
        )

        coordinator.start()
        coordinator.start()

        withTimeout(2_000) { device.registered.await() }
        assertEquals(1, device.registerCalls)
    }
}

private class RegistrationAuthRepository : AuthRepository {
    override fun observeCurrentUser(): Flow<AuthUser?> = flowOf(
        AuthUser(
            id = "user-1",
            email = "user@example.invalid",
            suggestedNickname = "用户",
            emailVerified = true,
        ),
    )

    override suspend fun signIn(email: String, password: String) = AppResult.Success(Unit)
    override suspend fun register(email: String, password: String, nickname: String) =
        AppResult.Success(Unit)
    override suspend fun sendPasswordResetEmail(email: String) = AppResult.Success(Unit)
    override suspend fun sendEmailVerification() = AppResult.Success(Unit)
    override suspend fun refreshCurrentUser() = AppResult.Failure(AppError.UNAUTHENTICATED)
    override suspend fun signOut() = AppResult.Success(Unit)
}

private class RegistrationDeviceRepository : DeviceRepository {
    val registered = CompletableDeferred<Unit>()
    var registerCalls = 0

    override suspend fun registerCurrentDevice(): AppResult<Unit> {
        registerCalls += 1
        registered.complete(Unit)
        return AppResult.Success(Unit)
    }

    override suspend fun registerToken(token: String) = AppResult.Success(Unit)
    override suspend fun unregisterCurrentDevice() = AppResult.Success(Unit)
}
