package com.example.nextlist.feature.profile

import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.core.result.LoadState
import com.example.nextlist.domain.model.NotificationPreferenceType
import com.example.nextlist.domain.model.NotificationPreferences
import com.example.nextlist.domain.model.NotificationPreferencesSnapshot
import com.example.nextlist.domain.repository.NotificationPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NotificationSettingsViewModelTest {
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
    fun `preferences load toggle fail and retry without changing feed behavior`() =
        runTest(dispatcher) {
            val repository = FakePreferencesRepository()
            val viewModel = NotificationSettingsViewModel(repository)
            runCurrent()
            val initial = viewModel.uiState.value.content as LoadState.Content
            assertTrue(initial.data.groupInvite)
            assertTrue(viewModel.uiState.value.isFromCache)

            repository.updateResult = AppResult.Failure(AppError.NETWORK_UNAVAILABLE)
            viewModel.toggle(NotificationPreferenceType.GROUP_INVITE, false)
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.canRetrySave)
            assertFalse(
                (viewModel.uiState.value.content as LoadState.Content)
                    .data.groupInvite,
            )

            repository.updateResult = AppResult.Success(Unit)
            viewModel.retrySave()
            advanceUntilIdle()
            assertEquals(2, repository.updateCalls)
            assertFalse(viewModel.uiState.value.canRetrySave)
        }
}

private class FakePreferencesRepository : NotificationPreferencesRepository {
    val snapshots = MutableStateFlow<AppResult<NotificationPreferencesSnapshot>>(
        AppResult.Success(
            NotificationPreferencesSnapshot(
                preferences = NotificationPreferences(),
                isFromCache = true,
                hasPendingWrites = false,
            ),
        ),
    )
    var updateResult: AppResult<Unit> = AppResult.Success(Unit)
    var updateCalls = 0

    override fun observe(): Flow<AppResult<NotificationPreferencesSnapshot>> = snapshots

    override suspend fun update(preferences: NotificationPreferences): AppResult<Unit> {
        updateCalls += 1
        return updateResult
    }
}
