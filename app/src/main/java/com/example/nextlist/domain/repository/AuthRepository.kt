package com.example.nextlist.domain.repository

import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeCurrentUser(): Flow<AuthUser?>

    suspend fun signIn(email: String, password: String): AppResult<Unit>

    suspend fun register(email: String, password: String, nickname: String): AppResult<Unit>

    suspend fun sendPasswordResetEmail(email: String): AppResult<Unit>

    suspend fun sendEmailVerification(): AppResult<Unit>

    suspend fun refreshCurrentUser(): AppResult<AuthUser>

    suspend fun signOut(): AppResult<Unit>
}
