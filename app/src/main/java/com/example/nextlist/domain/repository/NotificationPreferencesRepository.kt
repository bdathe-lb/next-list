package com.example.nextlist.domain.repository

import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.NotificationPreferences
import com.example.nextlist.domain.model.NotificationPreferencesSnapshot
import kotlinx.coroutines.flow.Flow

interface NotificationPreferencesRepository {
    fun observe(): Flow<AppResult<NotificationPreferencesSnapshot>>

    suspend fun update(preferences: NotificationPreferences): AppResult<Unit>
}
