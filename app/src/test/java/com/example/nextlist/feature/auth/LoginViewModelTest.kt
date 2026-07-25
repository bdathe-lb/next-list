package com.example.nextlist.feature.auth

import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.AuthUser
import com.example.nextlist.domain.repository.AuthRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
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
    fun `submit validates fields before calling the repository`() {
        val repository = DelayedAuthRepository()
        val viewModel = LoginViewModel(repository)

        viewModel.submit()

        assertEquals("请输入邮箱", viewModel.uiState.value.emailError)
        assertEquals("请输入密码", viewModel.uiState.value.passwordError)
        assertEquals(0, repository.signInCalls)
    }

    @Test
    fun `repeated taps while signing in submit only once`() = runTest(dispatcher) {
        val repository = DelayedAuthRepository()
        val viewModel = LoginViewModel(repository)
        viewModel.onEmailChanged("alice@example.com")
        viewModel.onPasswordChanged("123456")

        viewModel.submit()
        viewModel.submit()
        runCurrent()

        assertEquals(1, repository.signInCalls)
        repository.result.complete(AppResult.Success(Unit))
        runCurrent()
    }
}

private class DelayedAuthRepository : AuthRepository {
    var signInCalls = 0
    val result = CompletableDeferred<AppResult<Unit>>()

    override fun observeCurrentUser(): Flow<AuthUser?> = MutableStateFlow(null)

    override suspend fun signIn(email: String, password: String): AppResult<Unit> {
        signInCalls += 1
        return result.await()
    }

    override suspend fun register(email: String, password: String, nickname: String) =
        AppResult.Success(Unit)
    override suspend fun sendPasswordResetEmail(email: String) = AppResult.Success(Unit)
    override suspend fun sendEmailVerification() = AppResult.Success(Unit)
    override suspend fun refreshCurrentUser() =
        AppResult.Failure(AppError.UNAUTHENTICATED)
    override suspend fun signOut() = AppResult.Success(Unit)
    override suspend fun deleteAccount(requestId: String) = AppResult.Success(Unit)
}
