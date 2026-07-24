package com.example.nextlist.domain.repository

import com.example.nextlist.domain.model.PendingInvite
import kotlinx.coroutines.flow.Flow

interface PendingInviteRepository {
    fun observe(): Flow<PendingInvite?>

    suspend fun save(invite: PendingInvite)

    suspend fun clear()
}
