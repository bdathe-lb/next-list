package com.example.nextlist.data.firestore

import com.example.nextlist.BuildConfig
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.data.firebase.awaitTask
import com.example.nextlist.data.firebase.toAppError
import com.example.nextlist.domain.model.Idea
import com.example.nextlist.domain.model.IdeaCategory
import com.example.nextlist.domain.model.IdeaComment
import com.example.nextlist.domain.model.IdeaDraft
import com.example.nextlist.domain.model.IdeaMedia
import com.example.nextlist.domain.model.IdeaReaction
import com.example.nextlist.domain.model.IdeaStatus
import com.example.nextlist.domain.model.MemberSnapshot
import com.example.nextlist.domain.model.ReactionCounts
import com.example.nextlist.domain.model.ReactionValue
import com.example.nextlist.domain.model.RealtimeItems
import com.example.nextlist.domain.repository.IdeaRepository
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.android.gms.tasks.Task
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class FirebaseIdeaRepository @Inject constructor() : IdeaRepository {
    private val auth: FirebaseAuth? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null
    }
    private val firestore: FirebaseFirestore? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseFirestore.getInstance() else null
    }

    override fun newIdeaId(groupId: String): String =
        firestore?.collection("groups")?.document(groupId)
            ?.collection("ideas")?.document()?.id.orEmpty()

    override fun observeIdeas(
        groupId: String,
        status: IdeaStatus,
        category: IdeaCategory?,
    ): Flow<AppResult<RealtimeItems<Idea>>> = callbackFlow {
        val database = firestore
        if (database == null) {
            trySend(AppResult.Failure(AppError.UNKNOWN))
            close()
            return@callbackFlow
        }
        var query: Query = database.collection("groups")
            .document(groupId)
            .collection("ideas")
            .whereEqualTo("isDeleted", false)
            .whereEqualTo("status", status.wireValue)
        if (category != null) {
            query = query.whereEqualTo("category", category.wireValue)
        }
        query = when (status) {
            IdeaStatus.IDEA -> query.orderBy("createdAt", Query.Direction.DESCENDING)
            IdeaStatus.SCHEDULED ->
                query.orderBy("schedule.startAt", Query.Direction.ASCENDING)
            IdeaStatus.COMPLETED ->
                query.orderBy("completion.completedOn", Query.Direction.DESCENDING)
        }.limit(200)

        val registration = query.addSnapshotListener { snapshot, error ->
            when {
                error != null -> trySend(AppResult.Failure(error.toAppError()))
                snapshot == null -> trySend(AppResult.Failure(AppError.UNKNOWN))
                else -> {
                    val ideas = snapshot.documents.mapNotNull(IdeaDocumentMapper::toIdea)
                    trySend(
                        AppResult.Success(
                            RealtimeItems(
                                items = ideas,
                                hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                                isFromCache = snapshot.metadata.isFromCache,
                            ),
                        ),
                    )
                }
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeIdea(groupId: String, ideaId: String): Flow<AppResult<Idea>> =
        callbackFlow {
            val database = firestore
            if (database == null) {
                trySend(AppResult.Failure(AppError.UNKNOWN))
                close()
                return@callbackFlow
            }
            val registration = ideaReference(database, groupId, ideaId)
                .addSnapshotListener { snapshot, error ->
                    when {
                        error != null -> trySend(AppResult.Failure(error.toAppError()))
                        snapshot == null || !snapshot.exists() ->
                            trySend(AppResult.Failure(AppError.NOT_FOUND))
                        snapshot.getBoolean("isDeleted") == true ->
                            trySend(AppResult.Failure(AppError.NOT_FOUND))
                        else -> trySend(
                            IdeaDocumentMapper.toIdea(snapshot)
                                ?.let { AppResult.Success(it) }
                                ?: AppResult.Failure(AppError.UNKNOWN),
                        )
                    }
                }
            awaitClose { registration.remove() }
        }

    override fun observeReactions(
        groupId: String,
        ideaId: String,
    ): Flow<AppResult<RealtimeItems<IdeaReaction>>> = callbackFlow {
        val database = firestore
        if (database == null) {
            trySend(AppResult.Failure(AppError.UNKNOWN))
            close()
            return@callbackFlow
        }
        val registration = ideaReference(database, groupId, ideaId)
            .collection("reactions")
            .orderBy("updatedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(AppResult.Failure(error.toAppError()))
                    snapshot == null -> trySend(AppResult.Failure(AppError.UNKNOWN))
                    else -> trySend(
                        AppResult.Success(
                            RealtimeItems(
                                items = snapshot.documents.mapNotNull(
                                    IdeaDocumentMapper::toReaction,
                                ),
                                hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                                isFromCache = snapshot.metadata.isFromCache,
                            ),
                        ),
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeComments(
        groupId: String,
        ideaId: String,
    ): Flow<AppResult<RealtimeItems<IdeaComment>>> = callbackFlow {
        val database = firestore
        if (database == null) {
            trySend(AppResult.Failure(AppError.UNKNOWN))
            close()
            return@callbackFlow
        }
        val registration = ideaReference(database, groupId, ideaId)
            .collection("comments")
            .whereEqualTo("isDeleted", false)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(AppResult.Failure(error.toAppError()))
                    snapshot == null -> trySend(AppResult.Failure(AppError.UNKNOWN))
                    else -> trySend(
                        AppResult.Success(
                            RealtimeItems(
                                items = snapshot.documents
                                    .mapNotNull(IdeaDocumentMapper::toComment)
                                    .reversed(),
                                hasPendingWrites = snapshot.metadata.hasPendingWrites(),
                                isFromCache = snapshot.metadata.isFromCache,
                            ),
                        ),
                    )
                }
            }
        awaitClose { registration.remove() }
    }

    override suspend fun createIdea(
        groupId: String,
        ideaId: String,
        draft: IdeaDraft,
        requireServerAck: Boolean,
    ): AppResult<Unit> = writeWithMembership(groupId) { database, uid, snapshot ->
        val serverTime = FieldValue.serverTimestamp()
        val task = ideaReference(database, groupId, ideaId).set(
            mapOf(
                "groupId" to groupId,
                "title" to draft.title,
                "category" to draft.category.wireValue,
                "note" to draft.note,
                "media" to draft.media?.toFirestoreMap(),
                "locationOrLink" to draft.locationOrLink,
                "createdBy" to uid,
                "creatorSnapshot" to snapshot,
                "status" to IdeaStatus.IDEA.wireValue,
                "schedule" to null,
                "completion" to null,
                "reactionCounts" to mapOf(
                    "want" to 0,
                    "ok" to 0,
                    "notInterested" to 0,
                ),
                "rsvpCounts" to mapOf(
                    "going" to 0,
                    "maybe" to 0,
                    "notGoing" to 0,
                ),
                "commentCount" to 0,
                "reminderClaimedAt" to null,
                "reminderSentAt" to null,
                "reminderSkippedReason" to null,
                "lastModifiedBy" to uid,
                "createdAt" to serverTime,
                "updatedAt" to serverTime,
                "isDeleted" to false,
                "deletedAt" to null,
                "deletedBy" to null,
                "schemaVersion" to 1,
            ),
        )
        if (requireServerAck) task.awaitTask() else task.awaitOrQueue()
    }

    override suspend fun updateIdea(
        groupId: String,
        ideaId: String,
        draft: IdeaDraft,
        requireServerAck: Boolean,
    ): AppResult<Unit> = writeWithMembership(groupId) { database, uid, _ ->
        val task = ideaReference(database, groupId, ideaId).update(
            mapOf(
                "title" to draft.title,
                "category" to draft.category.wireValue,
                "note" to draft.note,
                "media" to draft.media?.toFirestoreMap(),
                "locationOrLink" to draft.locationOrLink,
                "lastModifiedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        if (requireServerAck) task.awaitTask() else task.awaitOrQueue()
    }

    override suspend fun deleteIdea(groupId: String, ideaId: String): AppResult<Unit> =
        writeWithMembership(groupId) { database, uid, _ ->
            ideaReference(database, groupId, ideaId).update(
                mapOf(
                    "isDeleted" to true,
                    "deletedAt" to FieldValue.serverTimestamp(),
                    "deletedBy" to uid,
                    "lastModifiedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).awaitOrQueue()
        }

    override suspend fun setReaction(
        groupId: String,
        ideaId: String,
        value: ReactionValue,
        isExisting: Boolean,
    ): AppResult<Unit> = writeWithMembership(groupId) { database, uid, snapshot ->
        val reference = ideaReference(database, groupId, ideaId)
            .collection("reactions")
            .document(uid)
        val task = if (isExisting) {
            reference.update(
                mapOf(
                    "value" to value.wireValue,
                    "userSnapshot" to snapshot,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
        } else {
            reference.set(
                mapOf(
                    "groupId" to groupId,
                    "ideaId" to ideaId,
                    "userId" to uid,
                    "value" to value.wireValue,
                    "userSnapshot" to snapshot,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "schemaVersion" to 1,
                ),
            )
        }
        task.awaitOrQueue()
    }

    override suspend fun clearReaction(groupId: String, ideaId: String): AppResult<Unit> =
        writeWithMembership(groupId) { database, uid, _ ->
            ideaReference(database, groupId, ideaId)
                .collection("reactions")
                .document(uid)
                .delete()
                .awaitOrQueue()
        }

    override suspend fun addComment(
        groupId: String,
        ideaId: String,
        content: String,
    ): AppResult<Unit> = writeWithMembership(groupId) { database, uid, snapshot ->
            ideaReference(database, groupId, ideaId)
            .collection("comments")
            .document()
            .set(
                mapOf(
                    "content" to content,
                    "createdBy" to uid,
                    "creatorSnapshot" to snapshot,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "isDeleted" to false,
                    "deletedAt" to null,
                    "deletedBy" to null,
                    "schemaVersion" to 1,
                ),
            ).awaitOrQueue()
    }

    override suspend fun deleteComment(
        groupId: String,
        ideaId: String,
        commentId: String,
    ): AppResult<Unit> = writeWithMembership(groupId) { database, uid, _ ->
            ideaReference(database, groupId, ideaId)
            .collection("comments")
            .document(commentId)
            .update(
                mapOf(
                    "isDeleted" to true,
                    "deletedAt" to FieldValue.serverTimestamp(),
                    "deletedBy" to uid,
                ),
            ).awaitOrQueue()
    }

    private suspend fun writeWithMembership(
        groupId: String,
        action: suspend (
            FirebaseFirestore,
            String,
            Map<String, Any?>,
        ) -> Unit,
    ): AppResult<Unit> {
        val database = firestore ?: return AppResult.Failure(AppError.UNKNOWN)
        val uid = auth?.currentUser?.uid ?: return AppResult.Failure(AppError.UNAUTHENTICATED)
        return try {
            val memberReference = database.collection("groups")
                .document(groupId)
                .collection("members")
                .document(uid)
            val member = try {
                memberReference.get(Source.CACHE).awaitTask()
            } catch (_: Exception) {
                memberReference.get().awaitTask()
            }
            if (member.getString("status") != "active") {
                return AppResult.Failure(AppError.PERMISSION_DENIED)
            }
            val rawSnapshot = member.get("profileSnapshot") as? Map<*, *>
                ?: return AppResult.Failure(AppError.UNKNOWN)
            val nickname = rawSnapshot["nickname"] as? String
                ?: return AppResult.Failure(AppError.UNKNOWN)
            val snapshot = mapOf(
                "nickname" to nickname,
                "avatarPath" to (rawSnapshot["avatarPath"] as? String),
            )
            action(database, uid, snapshot)
            AppResult.Success(Unit)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }
}

internal object IdeaDocumentMapper {
    fun toIdea(document: DocumentSnapshot): Idea? {
        return toIdea(
            id = document.id,
            data = document.data ?: return null,
            hasPendingWrites = document.metadata.hasPendingWrites(),
        )
    }

    fun toIdea(
        id: String,
        data: Map<String, Any?>,
        hasPendingWrites: Boolean = false,
    ): Idea? {
        val creator = data["creatorSnapshot"] as? Map<*, *> ?: return null
        val counts = data["reactionCounts"] as? Map<*, *> ?: emptyMap<Any, Any>()
        return Idea(
            id = id,
            groupId = data["groupId"] as? String ?: return null,
            title = data["title"] as? String ?: return null,
            category = IdeaCategory.fromWire(data["category"] as? String) ?: return null,
            note = data["note"] as? String,
            media = (data["media"] as? Map<*, *>)?.toIdeaMedia(),
            locationOrLink = data["locationOrLink"] as? String,
            createdBy = data["createdBy"] as? String ?: return null,
            creatorSnapshot = creator.toMemberSnapshot() ?: return null,
            status = IdeaStatus.fromWire(data["status"] as? String) ?: return null,
            reactionCounts = ReactionCounts(
                want = counts.nonNegativeInt("want"),
                ok = counts.nonNegativeInt("ok"),
                notInterested = counts.nonNegativeInt("notInterested"),
            ),
            commentCount = data.nonNegativeInt("commentCount"),
            lastModifiedBy = data["lastModifiedBy"] as? String ?: return null,
            createdAt = data.instant("createdAt"),
            updatedAt = data.instant("updatedAt"),
            hasPendingWrites = hasPendingWrites,
        )
    }

    fun toReaction(document: DocumentSnapshot): IdeaReaction? {
        val data = document.data ?: return null
        val snapshot = data["userSnapshot"] as? Map<*, *> ?: return null
        return IdeaReaction(
            userId = data["userId"] as? String ?: return null,
            value = ReactionValue.fromWire(data["value"] as? String) ?: return null,
            userSnapshot = snapshot.toMemberSnapshot() ?: return null,
            createdAt = data.instant("createdAt"),
            updatedAt = data.instant("updatedAt"),
            hasPendingWrites = document.metadata.hasPendingWrites(),
        )
    }

    fun toComment(document: DocumentSnapshot): IdeaComment? {
        val data = document.data ?: return null
        val snapshot = data["creatorSnapshot"] as? Map<*, *> ?: return null
        if (data["isDeleted"] != false) return null
        return IdeaComment(
            id = document.id,
            content = data["content"] as? String ?: return null,
            createdBy = data["createdBy"] as? String ?: return null,
            creatorSnapshot = snapshot.toMemberSnapshot() ?: return null,
            createdAt = data.instant("createdAt"),
            hasPendingWrites = document.metadata.hasPendingWrites(),
        )
    }
}

private fun ideaReference(
    database: FirebaseFirestore,
    groupId: String,
    ideaId: String,
) = database.collection("groups")
    .document(groupId)
    .collection("ideas")
    .document(ideaId)

private fun IdeaMedia.toFirestoreMap(): Map<String, Any> = mapOf(
    "storagePath" to storagePath,
    "mimeType" to mimeType,
    "width" to width,
    "height" to height,
    "byteSize" to byteSize,
)

private fun Map<*, *>.toIdeaMedia(): IdeaMedia? = IdeaMedia(
    storagePath = this["storagePath"] as? String ?: return null,
    mimeType = this["mimeType"] as? String ?: return null,
    width = (this["width"] as? Number)?.toInt() ?: return null,
    height = (this["height"] as? Number)?.toInt() ?: return null,
    byteSize = (this["byteSize"] as? Number)?.toLong() ?: return null,
)

private fun Map<*, *>.toMemberSnapshot(): MemberSnapshot? = MemberSnapshot(
    nickname = this["nickname"] as? String ?: return null,
    avatarPath = this["avatarPath"] as? String,
)

private fun Map<*, *>.nonNegativeInt(key: String): Int =
    ((this[key] as? Number)?.toInt() ?: 0).coerceAtLeast(0)

private fun Map<String, Any?>.instant(key: String): Instant? =
    (this[key] as? Timestamp)?.toDate()?.toInstant()

private suspend fun Task<Void>.awaitOrQueue() {
    withTimeoutOrNull(1_500) {
        awaitTask()
    }
}
