package com.example.nextlist.data.firestore

import com.example.nextlist.BuildConfig
import com.example.nextlist.core.result.AppError
import com.example.nextlist.core.result.AppResult
import com.example.nextlist.data.firebase.awaitTask
import com.example.nextlist.data.firebase.toAppError
import com.example.nextlist.domain.model.Group
import com.example.nextlist.domain.model.GroupMember
import com.example.nextlist.domain.model.GroupRole
import com.example.nextlist.domain.model.GroupStatus
import com.example.nextlist.domain.model.GroupSummary
import com.example.nextlist.domain.model.InviteCredential
import com.example.nextlist.domain.model.InviteCredentialKind
import com.example.nextlist.domain.model.InviteCredentials
import com.example.nextlist.domain.model.InvitePreview
import com.example.nextlist.domain.model.MemberSnapshot
import com.example.nextlist.domain.repository.GroupRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Singleton
class FirebaseGroupRepository @Inject constructor() : GroupRepository {
    private val auth: FirebaseAuth? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseAuth.getInstance() else null
    }
    private val firestore: FirebaseFirestore? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) FirebaseFirestore.getInstance() else null
    }
    private val functions: FirebaseFunctions? by lazy {
        if (BuildConfig.FIREBASE_CONFIGURED) {
            FirebaseFunctions.getInstance(BuildConfig.FIREBASE_FUNCTIONS_REGION)
        } else {
            null
        }
    }

    override fun currentUserId(): String? = auth?.currentUser?.uid

    override fun observeMyGroups(): Flow<AppResult<List<GroupSummary>>> = callbackFlow {
        val database = firestore
        val uid = currentUserId()
        if (database == null || uid == null) {
            trySend(AppResult.Failure(AppError.UNAUTHENTICATED))
            close()
            return@callbackFlow
        }

        val lock = Any()
        val groupDocuments = mutableMapOf<String, DocumentSnapshot>()
        val memberDocuments = mutableMapOf<String, List<DocumentSnapshot>>()
        val groupRegistrations = mutableMapOf<String, ListenerRegistration>()
        val memberRegistrations = mutableMapOf<String, ListenerRegistration>()

        fun emitGroups() {
            val summaries = synchronized(lock) {
                groupDocuments.mapNotNull { (groupId, document) ->
                    if (!document.exists()) return@mapNotNull null
                    GroupDocumentMapper.toSummary(
                        id = groupId,
                        data = document.data.orEmpty(),
                        memberDocuments = memberDocuments[groupId].orEmpty()
                            .map { it.data.orEmpty() },
                    )
                }.sortedByDescending { it.lastActivityAt }
            }
            trySend(AppResult.Success(summaries))
        }

        fun removeGroup(groupId: String) {
            synchronized(lock) {
                groupRegistrations.remove(groupId)?.remove()
                memberRegistrations.remove(groupId)?.remove()
                groupDocuments.remove(groupId)
                memberDocuments.remove(groupId)
            }
        }

        fun addGroup(groupId: String) {
            synchronized(lock) {
                if (groupRegistrations.containsKey(groupId)) return
                groupRegistrations[groupId] = database.collection("groups")
                    .document(groupId)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            if (error.toAppError() == AppError.PERMISSION_DENIED) {
                                removeGroup(groupId)
                                emitGroups()
                            } else {
                                trySend(AppResult.Failure(error.toAppError()))
                            }
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            synchronized(lock) { groupDocuments[groupId] = snapshot }
                            emitGroups()
                        }
                    }
                memberRegistrations[groupId] = database.collection("groups")
                    .document(groupId)
                    .collection("members")
                    .whereEqualTo("status", "active")
                    .limit(5)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            if (error.toAppError() != AppError.PERMISSION_DENIED) {
                                trySend(AppResult.Failure(error.toAppError()))
                            }
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            synchronized(lock) { memberDocuments[groupId] = snapshot.documents }
                            emitGroups()
                        }
                    }
            }
        }

        val membershipRegistration = database.collectionGroup("members")
            .whereEqualTo("userId", uid)
            .whereEqualTo("status", "active")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(AppResult.Failure(error.toAppError()))
                    return@addSnapshotListener
                }
                val activeIds = snapshot?.documents.orEmpty()
                    .mapNotNull { it.getString("groupId") }
                    .toSet()
                val previousIds = synchronized(lock) { groupRegistrations.keys.toSet() }
                (previousIds - activeIds).forEach(::removeGroup)
                (activeIds - previousIds).forEach(::addGroup)
                emitGroups()
            }

        awaitClose {
            membershipRegistration.remove()
            synchronized(lock) {
                groupRegistrations.values.forEach(ListenerRegistration::remove)
                memberRegistrations.values.forEach(ListenerRegistration::remove)
            }
        }
    }

    override fun observeGroup(groupId: String): Flow<AppResult<Group>> = callbackFlow {
        val database = firestore
        if (database == null) {
            trySend(AppResult.Failure(AppError.UNKNOWN))
            close()
            return@callbackFlow
        }
        val registration = database.collection("groups")
            .document(groupId)
            .addSnapshotListener { snapshot, error ->
                when {
                    error != null -> trySend(AppResult.Failure(error.toAppError()))
                    snapshot == null || !snapshot.exists() ->
                        trySend(AppResult.Failure(AppError.NOT_FOUND))
                    else -> {
                        val group = GroupDocumentMapper.toGroup(
                            snapshot.id,
                            snapshot.data.orEmpty(),
                        )
                        trySend(
                            group?.let { AppResult.Success(it) }
                                ?: AppResult.Failure(AppError.UNKNOWN),
                        )
                    }
                }
            }
        awaitClose { registration.remove() }
    }

    override fun observeMembers(groupId: String): Flow<AppResult<List<GroupMember>>> =
        callbackFlow {
            val database = firestore
            if (database == null) {
                trySend(AppResult.Failure(AppError.UNKNOWN))
                close()
                return@callbackFlow
            }
            val registration = database.collection("groups")
                .document(groupId)
                .collection("members")
                .whereEqualTo("status", "active")
                .addSnapshotListener { snapshot, error ->
                    when {
                        error != null -> trySend(AppResult.Failure(error.toAppError()))
                        snapshot == null -> trySend(AppResult.Failure(AppError.UNKNOWN))
                        else -> {
                            val members = snapshot.documents.mapNotNull { document ->
                                GroupDocumentMapper.toMember(
                                    document.id,
                                    document.data.orEmpty(),
                                )
                            }.sortedWith(
                                compareBy<GroupMember> { it.role != GroupRole.ADMIN }
                                    .thenBy { it.nickname },
                            )
                            trySend(AppResult.Success(members))
                        }
                    }
                }
            awaitClose { registration.remove() }
        }

    override suspend fun createGroup(name: String, requestId: String): AppResult<String> =
        call("createGroup", mapOf("name" to name, "requestId" to requestId))
            .mapValue { it.string("groupId") }

    override suspend fun updateGroupName(groupId: String, name: String): AppResult<Unit> =
        callUnit("updateGroupName", mapOf("groupId" to groupId, "name" to name))

    override suspend fun getOrCreateInvite(groupId: String): AppResult<InviteCredentials> =
        call("getOrCreateInvite", mapOf("groupId" to groupId))
            .mapValue(::mapInviteCredentials)

    override suspend fun rotateInvite(
        groupId: String,
        requestId: String,
    ): AppResult<InviteCredentials> =
        call(
            "rotateInvite",
            mapOf("groupId" to groupId, "requestId" to requestId),
        ).mapValue(::mapInviteCredentials)

    override suspend fun previewInvite(
        credential: InviteCredential,
    ): AppResult<InvitePreview> =
        call("previewInvite", credential.toFunctionData())
            .mapValue(::mapInvitePreview)

    override suspend fun acceptInvite(credential: InviteCredential): AppResult<String> =
        call("acceptInvite", credential.toFunctionData())
            .mapValue { it.string("groupId") }

    override suspend fun sendDirectInvite(groupId: String, email: String): AppResult<Unit> =
        callUnit(
            "sendDirectInvite",
            mapOf("groupId" to groupId, "email" to email),
        )

    override suspend fun acceptDirectInvite(invitationId: String): AppResult<String> =
        call("acceptDirectInvite", mapOf("invitationId" to invitationId))
            .mapValue { it.string("groupId") }

    override suspend fun declineDirectInvite(invitationId: String): AppResult<Unit> {
        val database = firestore ?: return AppResult.Failure(AppError.UNKNOWN)
        val uid = currentUserId() ?: return AppResult.Failure(AppError.UNAUTHENTICATED)
        return try {
            database.collection("users")
                .document(uid)
                .collection("invitations")
                .document(invitationId)
                .update(
                    mapOf(
                        "status" to "declined",
                        "respondedAt" to FieldValue.serverTimestamp(),
                    ),
                )
                .awaitTask()
            AppResult.Success(Unit)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }

    override suspend fun leaveGroup(groupId: String): AppResult<Unit> =
        callUnit("leaveGroup", mapOf("groupId" to groupId))

    override suspend fun removeMember(groupId: String, userId: String): AppResult<Unit> =
        callUnit(
            "removeMember",
            mapOf("groupId" to groupId, "userId" to userId),
        )

    override suspend fun transferAdmin(groupId: String, userId: String): AppResult<Unit> =
        callUnit(
            "transferAdmin",
            mapOf("groupId" to groupId, "userId" to userId),
        )

    override suspend fun dissolveGroup(
        groupId: String,
        confirmationName: String,
    ): AppResult<Unit> =
        callUnit(
            "dissolveGroup",
            mapOf("groupId" to groupId, "confirmationName" to confirmationName),
        )

    private suspend fun callUnit(
        name: String,
        data: Map<String, Any>,
    ): AppResult<Unit> = when (val result = call(name, data)) {
        is AppResult.Success -> AppResult.Success(Unit)
        is AppResult.Failure -> result
    }

    private suspend fun call(
        name: String,
        data: Map<String, Any>,
    ): AppResult<Map<*, *>> {
        val cloudFunctions = functions ?: return AppResult.Failure(AppError.UNKNOWN)
        return try {
            val result = cloudFunctions.getHttpsCallable(name).call(data).awaitTask().data
            @Suppress("UNCHECKED_CAST")
            val map = result as? Map<*, *> ?: return AppResult.Failure(AppError.UNKNOWN)
            AppResult.Success(map)
        } catch (error: Exception) {
            AppResult.Failure(error.toAppError())
        }
    }
}

internal object GroupDocumentMapper {
    fun toGroup(id: String, data: Map<String, Any?>): Group? {
        val name = data["name"] as? String ?: return null
        val adminId = data["adminId"] as? String ?: return null
        val status = when (data["status"]) {
            "active" -> GroupStatus.ACTIVE
            "dissolved" -> GroupStatus.DISSOLVED
            else -> return null
        }
        return Group(
            id = id,
            name = name,
            adminId = adminId,
            status = status,
            memberCount = data.documentInt("memberCount"),
            ideaCount = data.documentInt("ideaCount"),
            scheduledCount = data.documentInt("scheduledCount"),
            completedCount = data.documentInt("completedCount"),
            lastActivityAt = data.instant("lastActivityAt"),
        )
    }

    fun toMember(id: String, data: Map<String, Any?>): GroupMember? {
        val role = when (data["role"]) {
            "admin" -> GroupRole.ADMIN
            "member" -> GroupRole.MEMBER
            else -> return null
        }
        val profile = data["profileSnapshot"] as? Map<*, *> ?: return null
        return GroupMember(
            userId = id,
            role = role,
            nickname = profile["nickname"] as? String ?: return null,
            avatarPath = profile["avatarPath"] as? String,
        )
    }

    fun toSummary(
        id: String,
        data: Map<String, Any?>,
        memberDocuments: List<Map<String, Any?>>,
    ): GroupSummary? {
        if (data["status"] != "active") return null
        return GroupSummary(
            id = id,
            name = data["name"] as? String ?: return null,
            memberCount = data.documentInt("memberCount"),
            ideaCount = data.documentInt("ideaCount"),
            scheduledCount = data.documentInt("scheduledCount"),
            members = memberDocuments.mapNotNull { member ->
                val profile = member["profileSnapshot"] as? Map<*, *> ?: return@mapNotNull null
                MemberSnapshot(
                    nickname = profile["nickname"] as? String ?: return@mapNotNull null,
                    avatarPath = profile["avatarPath"] as? String,
                )
            },
            lastActivityAt = data.instant("lastActivityAt"),
        )
    }
}

private fun InviteCredential.toFunctionData(): Map<String, Any> = mapOf(
    "kind" to when (kind) {
        InviteCredentialKind.TOKEN -> "token"
        InviteCredentialKind.CODE -> "code"
        InviteCredentialKind.DIRECT -> "direct"
    },
    "value" to value,
)

private fun mapInviteCredentials(data: Map<*, *>): InviteCredentials = InviteCredentials(
    groupId = data.string("groupId"),
    inviteId = data.string("inviteId"),
    token = data.string("token"),
    code = data.string("code"),
    expiresAt = Instant.ofEpochMilli(data.long("expiresAtMillis")),
)

private fun mapInvitePreview(data: Map<*, *>): InvitePreview {
    val members = (data["members"] as? List<*>).orEmpty().mapNotNull { value ->
        val member = value as? Map<*, *> ?: return@mapNotNull null
        MemberSnapshot(
            nickname = member["nickname"] as? String ?: return@mapNotNull null,
            avatarPath = member["avatarPath"] as? String,
        )
    }
    return InvitePreview(
        groupId = data.string("groupId"),
        groupName = data.string("groupName"),
        memberCount = data.wireInt("memberCount"),
        members = members,
    )
}

private inline fun <T, R> AppResult<T>.mapValue(transform: (T) -> R): AppResult<R> =
    when (this) {
        is AppResult.Success -> try {
            AppResult.Success(transform(value))
        } catch (_: Exception) {
            AppResult.Failure(AppError.UNKNOWN)
        }
        is AppResult.Failure -> this
    }

private fun Map<*, *>.string(key: String): String =
    this[key] as? String ?: error("Missing $key")

private fun Map<*, *>.wireInt(key: String): Int =
    (this[key] as? Number)?.toInt() ?: error("Missing $key")

private fun Map<*, *>.long(key: String): Long =
    (this[key] as? Number)?.toLong() ?: error("Missing $key")

private fun Map<String, Any?>.documentInt(key: String): Int =
    (this[key] as? Number)?.toInt() ?: 0

private fun Map<String, Any?>.instant(key: String): Instant? =
    (this[key] as? com.google.firebase.Timestamp)?.toDate()?.toInstant()
