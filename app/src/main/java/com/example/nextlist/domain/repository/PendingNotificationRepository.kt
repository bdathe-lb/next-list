package com.example.nextlist.domain.repository

import com.example.nextlist.domain.model.PendingNotificationTarget
import kotlinx.coroutines.flow.Flow

interface PendingNotificationRepository {
    fun observe(): Flow<PendingNotificationTarget?>

    suspend fun save(target: PendingNotificationTarget)

    suspend fun consume(messageId: String)
}
