package com.example.nextlist.data.firestore

import com.example.nextlist.BuildConfig
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.data.firebase.awaitTask
import com.example.nextlist.data.firebase.toAppError
import com.example.nextlist.domain.model.ActivityFeedCursor
import com.example.nextlist.domain.model.ActivityFeedItem
import com.example.nextlist.domain.model.ActivityFeedPage
import com.example.nextlist.domain.model.ActivityFeedType
import com.example.nextlist.domain.model.MemberSnapshot
import com.example.nextlist.domain.repository.ActivityFeedRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class FirebaseActivityFeedRepository @Inject constructor() : ActivityFeedRepository {
    private val auth: FirebaseAuth? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null
    }
    private val firestore: FirebaseFirestore? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseFirestore.getInstance() else null
    }

    override fun observeLatest(pageSize: Int): Flow<AppResult<ActivityFeedPage>> = callbackFlow {
        val database = firestore
        val uid = auth?.currentUser?.uid
        if (database == null || uid == null) {
            trySend(AppResult.Failure(AppError.UNAUTHENTICATED))
            close()
            return@callbackFlow
        }
        val registration = feedQuery(database, uid)
            .limit(pageSize.toLong())
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                when {
                    error != null -> trySend(AppResult.Failure(error.toAppError()))
                    snapshot == null -> trySend(AppResult.Failure(AppError.UNKNOWN))
                    else -> trySend(
                        AppResult.Success(
                            ActivityFeedPage(
                                items = snapshot.documents.mapNotNull(::toFeedItem),
                                nextCursor = snapshot.documents
                                    .takeIf { it.size == pageSize }
                                    ?.lastOrNull()
                                    ?.toFeedCursor(),
                                isFromCache = snapshot.metadata.isFromCache,
                            ),
                        ),
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun loadMore(
        cursor: ActivityFeedCursor,
        pageSize: Int,
    ): AppResult<ActivityFeedPage> {
        val database = firestore ?: return AppResult.Failure(AppError.UNKNOWN)
        val uid = auth?.currentUser?.uid
            ?: return AppResult.Failure(AppError.UNAUTHENTICATED)
        return try {
            val snapshot = feedQuery(database, uid)
                .startAfter(
                    Timestamp(cursor.createdAt.epochSecond, cursor.createdAt.nano),
                    cursor.documentId,
                )
                .limit(pageSize.toLong())
                .get()
                .awaitTask()
            AppResult.Success(
                ActivityFeedPage(
                    items = snapshot.documents.mapNotNull(::toFeedItem),
                    nextCursor = snapshot.documents
                        .takeIf { it.size == pageSize }
                        ?.lastOrNull()
                        ?.toFeedCursor(),
                    isFromCache = snapshot.metadata.isFromCache,
                ),
            )
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun markRead(feedId: String): AppResult<Unit> = write { database, uid ->
        val reference = database.collection("users").document(uid)
            .collection("feed").document(feedId)
        database.runTransaction { transaction ->
            val current = transaction.get(reference)
            if (current.exists() && current.getTimestamp("readAt") == null) {
                transaction.update(reference, "readAt", FieldValue.serverTimestamp())
            }
        }.awaitTask()
    }

    override suspend fun markAllRead(): AppResult<Unit> = write { database, uid ->
        val feed = database.collection("users").document(uid).collection("feed")
        while (true) {
            val unread = feed.whereEqualTo("readAt", null)
                .limit(400)
                .get()
                .awaitTask()
            if (unread.isEmpty) break
            val batch = database.batch()
            unread.documents.forEach { document ->
                batch.update(document.reference, "readAt", FieldValue.serverTimestamp())
            }
            batch.commit().awaitTask()
            if (unread.size() < 400) break
        }
    }

    private fun feedQuery(database: FirebaseFirestore, uid: String): Query =
        database.collection("users").document(uid)
            .collection("feed")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)

    private suspend fun write(
        block: suspend (FirebaseFirestore, String) -> Unit,
    ): AppResult<Unit> {
        val database = firestore ?: return AppResult.Failure(AppError.UNKNOWN)
        val uid = auth?.currentUser?.uid
            ?: return AppResult.Failure(AppError.UNAUTHENTICATED)
        return try {
            val acknowledged = withTimeoutOrNull(WRITE_TIMEOUT_MILLIS) {
                block(database, uid)
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

    private companion object {
        const val WRITE_TIMEOUT_MILLIS = 8_000L
    }
}

internal fun toFeedItem(document: DocumentSnapshot): ActivityFeedItem? {
    val type = ActivityFeedType.fromWire(document.getString("type")) ?: return null
    val actor = document.get("actorSnapshot") as? Map<*, *> ?: return null
    val createdAt = document.getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    val expiresAt = document.getTimestamp("expiresAt")?.toDate()?.toInstant() ?: return null
    if (!expiresAt.isAfter(java.time.Instant.now())) return null
    return ActivityFeedItem(
        id = document.id,
        type = type,
        groupId = document.getString("groupId") ?: return null,
        groupName = document.getString("groupNameSnapshot") ?: return null,
        ideaId = document.getString("ideaId"),
        ideaTitle = document.getString("ideaTitleSnapshot"),
        invitationId = document.getString("invitationId"),
        actorId = document.getString("actorId") ?: return null,
        actor = MemberSnapshot(
            nickname = actor["nickname"] as? String ?: return null,
            avatarPath = actor["avatarPath"] as? String,
        ),
        createdAt = createdAt,
        readAt = document.getTimestamp("readAt")?.toDate()?.toInstant(),
    )
}

private fun DocumentSnapshot.toFeedCursor(): ActivityFeedCursor? {
    val createdAt = getTimestamp("createdAt")?.toDate()?.toInstant() ?: return null
    return ActivityFeedCursor(createdAt = createdAt, documentId = id)
}
