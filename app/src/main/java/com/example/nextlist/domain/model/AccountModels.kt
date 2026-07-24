package com.example.nextlist.domain.model

import com.example.nextlist.core.result.AppError
import java.time.Instant

data class AuthUser(
    val id: String,
    val email: String,
    val suggestedNickname: String?,
    val emailVerified: Boolean,
)

data class UserProfile(
    val userId: String,
    val nickname: String,
    val avatarPath: String?,
    val emailVerified: Boolean,
    val updatedAt: Instant?,
)

sealed interface AccountSession {
    data object Loading : AccountSession

    data object SignedOut : AccountSession

    data class NeedsProfile(val user: AuthUser) : AccountSession

    data class SignedIn(
        val user: AuthUser,
        val profile: UserProfile,
    ) : AccountSession

    data class Error(val kind: AppError) : AccountSession
}
