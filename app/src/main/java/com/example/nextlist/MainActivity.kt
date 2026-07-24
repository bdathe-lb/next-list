package com.example.nextlist

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.example.nextlist.core.designsystem.NextListTheme
import com.example.nextlist.core.navigation.NextListAppContent
import com.example.nextlist.data.firebase.FirebaseEmulatorConnector
import com.example.nextlist.data.messaging.NextListMessagingService
import com.example.nextlist.data.messaging.NotificationRouter
import com.example.nextlist.domain.model.InviteCredentialKind
import com.example.nextlist.domain.model.PendingInvite
import com.example.nextlist.domain.repository.PendingInviteRepository
import com.example.nextlist.domain.repository.PendingNotificationRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var firebaseEmulatorConnector: FirebaseEmulatorConnector

    @Inject
    lateinit var pendingInviteRepository: PendingInviteRepository

    @Inject
    lateinit var pendingNotificationRepository: PendingNotificationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        persistInvite(intent?.data)
        persistNotification(intent)
        enableEdgeToEdge()
        setContent {
            NextListTheme {
                NextListAppContent(firebaseStatus = firebaseEmulatorConnector.status)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        persistInvite(intent.data)
        persistNotification(intent)
    }

    private fun persistInvite(uri: Uri?) {
        val pendingInvite = when {
            uri == null -> null
            uri.scheme == "https" &&
                uri.host == "nextlist.example" &&
                uri.pathSegments.firstOrNull() == "invite" ->
                uri.pathSegments.getOrNull(1)
                    ?.takeIf { it.length in 20..512 }
                    ?.let { PendingInvite(InviteCredentialKind.TOKEN, it) }
            uri.scheme == "nextlist" && uri.host == "invite" ->
                uri.pathSegments.firstOrNull()
                    ?.takeIf { it.length in 20..512 }
                    ?.let { PendingInvite(InviteCredentialKind.TOKEN, it) }
            uri.scheme == "nextlist" && uri.host == "direct-invite" ->
                uri.pathSegments.firstOrNull()
                    ?.takeIf { it.length in 1..128 }
                    ?.let { PendingInvite(InviteCredentialKind.DIRECT, it) }
            else -> null
        } ?: return

        lifecycleScope.launch {
            pendingInviteRepository.save(pendingInvite)
        }
    }

    private fun persistNotification(intent: Intent?) {
        if (intent == null) return
        val data = mapOf(
            "type" to (
                intent.getStringExtra(NextListMessagingService.EXTRA_TYPE)
                    ?: intent.getStringExtra("type")
                ),
            "groupId" to (
                intent.getStringExtra(NextListMessagingService.EXTRA_GROUP_ID)
                    ?: intent.getStringExtra("groupId")
                ),
            "ideaId" to (
                intent.getStringExtra(NextListMessagingService.EXTRA_IDEA_ID)
                    ?: intent.getStringExtra("ideaId")
                ),
            "invitationId" to (
                intent.getStringExtra(NextListMessagingService.EXTRA_INVITATION_ID)
                    ?: intent.getStringExtra("invitationId")
                ),
        ).mapNotNull { (key, value) -> value?.let { key to it } }.toMap()
        val messageId = intent.getStringExtra(NextListMessagingService.EXTRA_MESSAGE_ID)
            ?: intent.getStringExtra("google.message_id")
        val target = NotificationRouter.parse(data, messageId) ?: return
        lifecycleScope.launch {
            pendingNotificationRepository.save(target)
        }
    }
}
