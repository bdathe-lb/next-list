package com.example.nextlist.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nextlist.domain.model.NotificationType
import com.example.nextlist.domain.model.PendingNotificationTarget
import com.example.nextlist.domain.repository.PendingNotificationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.notificationDataStore by preferencesDataStore(
    name = "nextlist_notification_state",
)

@Singleton
class DataStorePendingNotificationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PendingNotificationRepository {
    override fun observe(): Flow<PendingNotificationTarget?> =
        context.notificationDataStore.data.map { values ->
            val type = NotificationType.fromWire(values[TYPE_KEY])
            val messageId = values[MESSAGE_ID_KEY]
            val groupId = values[GROUP_ID_KEY]
            if (type == null || messageId.isNullOrBlank() || groupId.isNullOrBlank()) {
                null
            } else {
                PendingNotificationTarget(
                    messageId = messageId,
                    type = type,
                    groupId = groupId,
                    ideaId = values[IDEA_ID_KEY],
                    invitationId = values[INVITATION_ID_KEY],
                )
            }
        }

    override suspend fun save(target: PendingNotificationTarget) {
        val current = context.notificationDataStore.data.first()
        if (target.messageId in current.consumedMessageIds()) return
        context.notificationDataStore.edit { values ->
            values[MESSAGE_ID_KEY] = target.messageId
            values[TYPE_KEY] = target.type.wireValue
            values[GROUP_ID_KEY] = target.groupId
            target.ideaId?.let { values[IDEA_ID_KEY] = it } ?: values.remove(IDEA_ID_KEY)
            target.invitationId?.let {
                values[INVITATION_ID_KEY] = it
            } ?: values.remove(INVITATION_ID_KEY)
        }
    }

    override suspend fun consume(messageId: String) {
        context.notificationDataStore.edit { values ->
            if (values[MESSAGE_ID_KEY] == messageId) {
                val consumed = values.consumedMessageIds()
                    .filterNot { it == messageId }
                    .plus(messageId)
                    .takeLast(MAX_CONSUMED_IDS)
                values[CONSUMED_MESSAGE_IDS_KEY] = consumed.joinToString(SEPARATOR)
                values.remove(MESSAGE_ID_KEY)
                values.remove(TYPE_KEY)
                values.remove(GROUP_ID_KEY)
                values.remove(IDEA_ID_KEY)
                values.remove(INVITATION_ID_KEY)
            }
        }
    }

    private companion object {
        val MESSAGE_ID_KEY = stringPreferencesKey("pending_message_id")
        val TYPE_KEY = stringPreferencesKey("pending_type")
        val GROUP_ID_KEY = stringPreferencesKey("pending_group_id")
        val IDEA_ID_KEY = stringPreferencesKey("pending_idea_id")
        val INVITATION_ID_KEY = stringPreferencesKey("pending_invitation_id")
        val CONSUMED_MESSAGE_IDS_KEY = stringPreferencesKey("consumed_message_ids")
        const val MAX_CONSUMED_IDS = 50
        const val SEPARATOR = "\u001F"
    }
}

private fun androidx.datastore.preferences.core.Preferences.consumedMessageIds(): List<String> =
    this[
        stringPreferencesKey("consumed_message_ids")
    ]?.split("\u001F")?.filter(String::isNotBlank).orEmpty()
