package com.example.nextlist.feature.profile

import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.AuthUser
import com.example.nextlist.domain.model.UserProfile
import com.example.nextlist.domain.repository.AuthRepository
import com.example.nextlist.domain.repository.AvatarRepository
import com.example.nextlist.domain.repository.DeviceRepository
import com.example.nextlist.domain.repository.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileDeviceCleanupTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `device cleanup failure never blocks sign out`() = runTest(dispatcher) {
        val auth = CleanupAuthRepository()
        val device = FailingDeviceRepository()
        val viewModel = ProfileViewModel(
            authRepository = auth,
            userProfileRepository = CleanupUserProfileRepository(),
            avatarRepository = CleanupAvatarRepository(),
            deviceRepository = device,
        )

        viewModel.signOut()
        advanceUntilIdle()

        assertEquals(1, device.unregisterCalls)
        assertEquals(1, auth.signOutCalls)
    }
}

private class CleanupAuthRepository : AuthRepository {
    var signOutCalls = 0
    override fun observeCurrentUser(): Flow<AuthUser?> = flowOf(null)
    override suspend fun signIn(email: String, password: String) = AppResult.Success(Unit)
    override suspend fun register(email: String, password: String, nickname: String) =
        AppResult.Success(Unit)
    override suspend fun sendPasswordResetEmail(email: String) = AppResult.Success(Unit)
    override suspend fun sendEmailVerification() = AppResult.Success(Unit)
    override suspend fun refreshCurrentUser() =
        AppResult.Failure(AppError.UNAUTHENTICATED)
    override suspend fun signOut(): AppResult<Unit> {
        signOutCalls += 1
        return AppResult.Success(Unit)
    }
}

private class FailingDeviceRepository : DeviceRepository {
    var unregisterCalls = 0
    override suspend fun registerCurrentDevice() = AppResult.Success(Unit)
    override suspend fun registerToken(token: String) = AppResult.Success(Unit)
    override suspend fun unregisterCurrentDevice(): AppResult<Unit> {
        unregisterCalls += 1
        return AppResult.Failure(AppError.NETWORK_UNAVAILABLE)
    }
}

private class CleanupUserProfileRepository : UserProfileRepository {
    override fun observeProfile(userId: String): Flow<AppResult<UserProfile?>> = flowOf(
        AppResult.Success(null),
    )
    override suspend fun createProfile(
        user: AuthUser,
        nickname: String,
        avatarPath: String?,
    ) = AppResult.Success(Unit)
    override suspend fun updateProfile(
        userId: String,
        nickname: String,
        avatarPath: String?,
    ) = AppResult.Success(Unit)
    override suspend fun updateEmailVerified(
        userId: String,
        emailVerified: Boolean,
    ) = AppResult.Success(Unit)
}

private class CleanupAvatarRepository : AvatarRepository {
    override suspend fun uploadAvatar(userId: String, sourceUri: String) =
        AppResult.Success("users/$userId/avatar/test.webp")
    override suspend fun getDownloadUrl(storagePath: String) =
        AppResult.Success("https://example.invalid/avatar.webp")
    override suspend fun deleteAvatar(storagePath: String) = AppResult.Success(Unit)
}
