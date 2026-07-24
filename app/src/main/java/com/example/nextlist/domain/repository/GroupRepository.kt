package com.example.nextlist.domain.repository

import com.example.nextlist.core.result.AppResult
import com.example.nextlist.domain.model.Group
import com.example.nextlist.domain.model.GroupMember
import com.example.nextlist.domain.model.GroupSummary
import com.example.nextlist.domain.model.InviteCredential
import com.example.nextlist.domain.model.InviteCredentials
import com.example.nextlist.domain.model.InvitePreview
import kotlinx.coroutines.flow.Flow

interface GroupRepository {
    fun currentUserId(): String?

    fun observeMyGroups(): Flow<AppResult<List<GroupSummary>>>

    fun observeGroup(groupId: String): Flow<AppResult<Group>>

    fun observeMembers(groupId: String): Flow<AppResult<List<GroupMember>>>

    suspend fun createGroup(name: String, requestId: String): AppResult<String>

    suspend fun updateGroupName(groupId: String, name: String): AppResult<Unit>

    suspend fun getOrCreateInvite(groupId: String): AppResult<InviteCredentials>

    suspend fun rotateInvite(
        groupId: String,
        requestId: String,
    ): AppResult<InviteCredentials>

    suspend fun previewInvite(credential: InviteCredential): AppResult<InvitePreview>

    suspend fun acceptInvite(credential: InviteCredential): AppResult<String>

    suspend fun sendDirectInvite(groupId: String, email: String): AppResult<Unit>

    suspend fun acceptDirectInvite(invitationId: String): AppResult<String>

    suspend fun declineDirectInvite(invitationId: String): AppResult<Unit>

    suspend fun leaveGroup(groupId: String): AppResult<Unit>

    suspend fun removeMember(groupId: String, userId: String): AppResult<Unit>

    suspend fun transferAdmin(groupId: String, userId: String): AppResult<Unit>

    suspend fun dissolveGroup(groupId: String, confirmationName: String): AppResult<Unit>
}
