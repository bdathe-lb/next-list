package com.example.nextlist.domain.model

import java.time.Instant

enum class GroupRole {
    ADMIN,
    MEMBER,
}

enum class GroupStatus {
    ACTIVE,
    DISSOLVED,
}

data class MemberSnapshot(
    val nickname: String,
    val avatarPath: String?,
    val avatarUrl: String? = null,
)

data class GroupSummary(
    val id: String,
    val name: String,
    val memberCount: Int,
    val ideaCount: Int,
    val scheduledCount: Int,
    val members: List<MemberSnapshot>,
    val lastActivityAt: Instant?,
)

data class Group(
    val id: String,
    val name: String,
    val adminId: String,
    val status: GroupStatus,
    val memberCount: Int,
    val ideaCount: Int,
    val scheduledCount: Int,
    val completedCount: Int,
    val lastActivityAt: Instant?,
)

data class GroupMember(
    val userId: String,
    val role: GroupRole,
    val nickname: String,
    val avatarPath: String?,
    val avatarUrl: String? = null,
)

enum class InviteCredentialKind {
    TOKEN,
    CODE,
    DIRECT,
}

data class InviteCredential(
    val kind: InviteCredentialKind,
    val value: String,
)

data class PendingInvite(
    val kind: InviteCredentialKind,
    val value: String,
)

data class InviteCredentials(
    val groupId: String,
    val inviteId: String,
    val token: String,
    val code: String,
    val expiresAt: Instant,
)

data class InvitePreview(
    val groupId: String,
    val groupName: String,
    val memberCount: Int,
    val members: List<MemberSnapshot>,
)
