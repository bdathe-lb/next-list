package com.example.nextlist.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.nextlist.domain.model.InviteCredentialKind
import com.example.nextlist.domain.model.PendingInvite
import com.example.nextlist.domain.repository.PendingInviteRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nextListDataStore by preferencesDataStore(name = "nextlist_private_state")

@Singleton
class DataStorePendingInviteRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : PendingInviteRepository {
    override fun observe(): Flow<PendingInvite?> = context.nextListDataStore.data.map { values ->
        val kind = values[KIND_KEY]?.let { stored ->
            InviteCredentialKind.entries.firstOrNull { it.name == stored }
        }
        val value = values[VALUE_KEY]
        if (kind == null || value.isNullOrBlank()) null else PendingInvite(kind, value)
    }

    override suspend fun save(invite: PendingInvite) {
        context.nextListDataStore.edit { values ->
            values[KIND_KEY] = invite.kind.name
            values[VALUE_KEY] = invite.value
        }
    }

    override suspend fun clear() {
        context.nextListDataStore.edit { values ->
            values.remove(KIND_KEY)
            values.remove(VALUE_KEY)
        }
    }

    private companion object {
        val KIND_KEY = stringPreferencesKey("pending_invite_kind")
        val VALUE_KEY = stringPreferencesKey("pending_invite_value")
    }
}
