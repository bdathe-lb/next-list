package com.example.nextlist.data.firestore

import com.example.nextlist.BuildConfig
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.data.firebase.awaitTask
import com.example.nextlist.data.firebase.toAppError
import com.example.nextlist.domain.model.AuthUser
import com.example.nextlist.domain.model.UserProfile
import com.example.nextlist.domain.repository.UserProfileRepository
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class FirestoreUserProfileRepository @Inject constructor() : UserProfileRepository {
    private val firestore: FirebaseFirestore? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseFirestore.getInstance() else null
    }

    override fun observeProfile(userId: String): Flow<AppResult<UserProfile?>> = callbackFlow {
        val database = firestore
        if (database == null) {
            trySend(AppResult.Failure(AppError.UNKNOWN))
            close()
            return@callbackFlow
        }

        val registration = database.collection(USERS_COLLECTION)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(AppResult.Failure(error.toAppError()))
                    snapshot == null -> trySend(AppResult.Failure(AppError.UNKNOWN))
                    !snapshot.exists() -> trySend(AppResult.Success(null))
                    else -> {
                        val profile = snapshot.toUserProfile(userId)
                        trySend(
                            if (profile == null) {
                                AppResult.Failure(AppError.UNKNOWN)
                            } else {
                                AppResult.Success(profile)
                            },
                        )
                    }
                }
            }

        awaitClose { registration.remove() }
    }

    override suspend fun createProfile(
        user: AuthUser,
        nickname: String,
        avatarPath: String?,
    ): AppResult<Unit> = write {
        collection(USERS_COLLECTION).document(user.id).set(
            mapOf(
                "nickname" to nickname,
                "avatarPath" to avatarPath,
                "emailVerified" to user.emailVerified,
                "notificationPrefs" to mapOf(
                    "groupInvite" to true,
                    "newSchedule" to true,
                    "upcomingReminder" to true,
                    "ideaComment" to true,
                ),
                "status" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
                "schemaVersion" to 1,
            ),
        ).awaitTask()
    }

    override suspend fun updateProfile(
        userId: String,
        nickname: String,
        avatarPath: String?,
    ): AppResult<Unit> = write {
        collection(USERS_COLLECTION).document(userId).update(
            mapOf(
                "nickname" to nickname,
                "avatarPath" to avatarPath,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitTask()
    }

    override suspend fun updateEmailVerified(
        userId: String,
        emailVerified: Boolean,
    ): AppResult<Unit> = write {
        collection(USERS_COLLECTION).document(userId).update(
            mapOf(
                "emailVerified" to emailVerified,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        ).awaitTask()
    }

    private suspend fun write(block: suspend FirebaseFirestore.() -> Unit): AppResult<Unit> {
        val database = firestore ?: return AppResult.Failure(AppError.UNKNOWN)
        return try {
            database.block()
            AppResult.Success(Unit)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    private companion object {
        const val USERS_COLLECTION = "users"
    }
}

private fun DocumentSnapshot.toUserProfile(userId: String): UserProfile? {
    val nickname = getString("nickname") ?: return null
    val avatarPath = get("avatarPath") as? String
    val emailVerified = getBoolean("emailVerified") ?: return null
    return UserProfile(
        userId = userId,
        nickname = nickname,
        avatarPath = avatarPath,
        emailVerified = emailVerified,
        updatedAt = getTimestamp("updatedAt")?.toDate()?.toInstant(),
    )
}
