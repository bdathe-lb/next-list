package com.example.nextlist.domain.repository

import com.example.nextlist.core.result.AppResult

interface DeviceRepository {
    suspend fun registerCurrentDevice(): AppResult<Unit>

    suspend fun registerToken(token: String): AppResult<Unit>

    suspend fun unregisterCurrentDevice(): AppResult<Unit>
}
