package com.example.nextlist.data.firestore

import com.example.nextlist.BuildConfig
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.data.firebase.awaitTask
import com.example.nextlist.data.firebase.toAppError
import com.example.nextlist.domain.model.NotificationPreferences
import com.example.nextlist.domain.model.NotificationPreferencesSnapshot
import com.example.nextlist.domain.repository.NotificationPreferencesRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class FirebaseNotificationPreferencesRepository @Inject constructor() :
    NotificationPreferencesRepository {
    private val auth: FirebaseAuth? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null
    }
    private val firestore: FirebaseFirestore? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseFirestore.getInstance() else null
    }

    override fun observe(): Flow<AppResult<NotificationPreferencesSnapshot>> = callbackFlow {
        val database = firestore
        val uid = auth?.currentUser?.uid
        if (database == null || uid == null) {
            trySend(AppResult.Failure(AppError.UNAUTHENTICATED))
            close()
            return@callbackFlow
        }
        val registration = database.collection("users").document(uid)
            .addSnapshotListener(MetadataChanges.INCLUDE) { document, error ->
                when {
                    error != null -> trySend(AppResult.Failure(error.toAppError()))
                    document == null || !document.exists() ->
                        trySend(AppResult.Failure(AppError.NOT_FOUND))
                    else -> {
                        val prefs = document.get("notificationPrefs") as? Map<*, *>
                        trySend(
                            AppResult.Success(
                                NotificationPreferencesSnapshot(
                                    preferences = NotificationPreferences(
                                        groupInvite = prefs?.get("groupInvite") as? Boolean ?: true,
                                        newSchedule = prefs?.get("newSchedule") as? Boolean ?: true,
                                        upcomingReminder =
                                        prefs?.get("upcomingReminder") as? Boolean ?: true,
                                        ideaComment = prefs?.get("ideaComment") as? Boolean ?: true,
                                    ),
                                    isFromCache = document.metadata.isFromCache,
                                    hasPendingWrites = document.metadata.hasPendingWrites(),
                                ),
                            ),
                        )
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun update(preferences: NotificationPreferences): AppResult<Unit> {
        val database = firestore ?: return AppResult.Failure(AppError.UNKNOWN)
        val uid = auth?.currentUser?.uid
            ?: return AppResult.Failure(AppError.UNAUTHENTICATED)
        return try {
            val acknowledged = withTimeoutOrNull(8_000) {
                database.collection("users").document(uid).update(
                    mapOf(
                        "notificationPrefs" to mapOf(
                            "groupInvite" to preferences.groupInvite,
                            "newSchedule" to preferences.newSchedule,
                            "upcomingReminder" to preferences.upcomingReminder,
                            "ideaComment" to preferences.ideaComment,
                        ),
                        "updatedAt" to FieldValue.serverTimestamp(),
                    ),
                ).awaitTask()
                true
            } ?: false
            if (acknowledged) {
                AppResult.Success(Unit)
            } else {
                AppResult.Failure(AppError.NETWORK_UNAVAILABLE)
            }
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }
}
