package com.example.nextlist.domain.repository

import com.example.nextlist.core.result.AppResult

interface AvatarRepository {
    suspend fun uploadAvatar(userId: String, sourceUri: String): AppResult<String>

    suspend fun getDownloadUrl(storagePath: String): AppResult<String>

    suspend fun deleteAvatar(storagePath: String): AppResult<Unit>
}
