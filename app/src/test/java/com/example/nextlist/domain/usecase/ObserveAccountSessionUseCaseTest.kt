package com.example.nextlist.domain.usecase

import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.AccountSession
import com.example.nextlist.domain.model.AuthUser
import com.example.nextlist.domain.model.UserProfile
import com.example.nextlist.domain.repository.AuthRepository
import com.example.nextlist.domain.repository.UserProfileRepository
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObserveAccountSessionUseCaseTest {
    private val authUser = AuthUser(
        id = "alice",
        email = "alice@example.com",
        suggestedNickname = "小林",
        emailVerified = false,
    )
    private val profile = UserProfile(
        userId = "alice",
        nickname = "小林",
        avatarPath = null,
        emailVerified = false,
        updatedAt = Instant.EPOCH,
    )

    @Test
    fun `signed out users route to authentication`() = runTest {
        val auth = FakeAuthRepository(null)
        val profiles = FakeUserProfileRepository(AppResult.Success(null))

        val result = ObserveAccountSessionUseCase(auth, profiles)()
            .first { it !is AccountSession.Loading }

        assertEquals(AccountSession.SignedOut, result)
    }

    @Test
    fun `authenticated users without a document route to profile completion`() = runTest {
        val auth = FakeAuthRepository(authUser)
        val profiles = FakeUserProfileRepository(AppResult.Success(null))

        val result = ObserveAccountSessionUseCase(auth, profiles)()
            .first { it !is AccountSession.Loading }

        assertEquals(AccountSession.NeedsProfile(authUser), result)
    }

    @Test
    fun `authenticated users with a profile enter the signed in app`() = runTest {
        val auth = FakeAuthRepository(authUser)
        val profiles = FakeUserProfileRepository(AppResult.Success(profile))

        val result = ObserveAccountSessionUseCase(auth, profiles)()
            .first { it !is AccountSession.Loading }

        assertEquals(AccountSession.SignedIn(authUser, profile), result)
    }

    @Test
    fun `profile listener errors produce retryable session error state`() = runTest {
        val auth = FakeAuthRepository(authUser)
        val profiles = FakeUserProfileRepository(
            AppResult.Failure(AppError.NETWORK_UNAVAILABLE),
        )

        val result = ObserveAccountSessionUseCase(auth, profiles)()
            .first { it !is AccountSession.Loading }

        assertTrue(result is AccountSession.Error)
        assertEquals(
            AppError.NETWORK_UNAVAILABLE,
            (result as AccountSession.Error).kind,
        )
    }
}

private class FakeAuthRepository(initialUser: AuthUser?) : AuthRepository {
    private val user = MutableStateFlow(initialUser)

    override fun observeCurrentUser(): Flow<AuthUser?> = user
    override suspend fun signIn(email: String, password: String) = AppResult.Success(Unit)
    override suspend fun register(email: String, password: String, nickname: String) =
        AppResult.Success(Unit)
    override suspend fun sendPasswordResetEmail(email: String) = AppResult.Success(Unit)
    override suspend fun sendEmailVerification() = AppResult.Success(Unit)
    override suspend fun refreshCurrentUser() =
        user.value?.let { AppResult.Success(it) }
            ?: AppResult.Failure(AppError.UNAUTHENTICATED)
    override suspend fun signOut() = AppResult.Success(Unit)
    override suspend fun deleteAccount(requestId: String) = AppResult.Success(Unit)
}

private class FakeUserProfileRepository(
    initial: AppResult<UserProfile?>,
) : UserProfileRepository {
    private val profile = MutableStateFlow(initial)

    override fun observeProfile(userId: String): Flow<AppResult<UserProfile?>> = profile
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
