package com.example.nextlist.domain.repository

import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.AuthUser
import com.example.nextlist.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

interface UserProfileRepository {
    fun observeProfile(userId: String): Flow<AppResult<UserProfile?>>

    suspend fun createProfile(
        user: AuthUser,
        nickname: String,
        avatarPath: String?,
    ): AppResult<Unit>

    suspend fun updateProfile(
        userId: String,
        nickname: String,
        avatarPath: String?,
    ): AppResult<Unit>

    suspend fun updateEmailVerified(
        userId: String,
        emailVerified: Boolean,
    ): AppResult<Unit>
}
