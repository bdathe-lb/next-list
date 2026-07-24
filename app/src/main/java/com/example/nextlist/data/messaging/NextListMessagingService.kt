package com.example.nextlist.data.messaging

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.nextlist.MainActivity
import com.example.nextlist.R
import com.example.nextlist.domain.model.NotificationType
import com.example.nextlist.domain.repository.DeviceRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class NextListMessagingService : FirebaseMessagingService() {
    @Inject
    lateinit var deviceRepository: DeviceRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch { deviceRepository.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val messageId = message.messageId ?: return
        val target = NotificationRouter.parse(message.data, messageId) ?: return
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_MESSAGE_ID, target.messageId)
            putExtra(EXTRA_TYPE, target.type.wireValue)
            putExtra(EXTRA_GROUP_ID, target.groupId)
            target.ideaId?.let { putExtra(EXTRA_IDEA_ID, it) }
            target.invitationId?.let { putExtra(EXTRA_INVITATION_ID, it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            messageId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val (title, body) = safeCopy(target.type)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(messageId.hashCode(), notification)
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "活动与邀请",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "小组邀请、活动安排、临近提醒和想法评论"
            },
        )
    }

    private fun safeCopy(type: NotificationType): Pair<String, String> = when (type) {
        NotificationType.GROUP_INVITED ->
            "小组邀请" to "打开下次查看邀请"
        NotificationType.SCHEDULE_CREATED ->
            "新活动已安排" to "打开下次查看活动安排"
        NotificationType.SCHEDULE_UPDATED ->
            "活动安排已更新" to "打开下次查看最新时间"
        NotificationType.UPCOMING_REMINDER ->
            "活动即将开始" to "打开下次查看活动详情"
        NotificationType.IDEA_COMMENTED ->
            "你的想法有新评论" to "打开下次查看；通知不会显示评论正文"
    }

    companion object {
        const val EXTRA_MESSAGE_ID = "nextlist.message_id"
        const val EXTRA_TYPE = "nextlist.type"
        const val EXTRA_GROUP_ID = "nextlist.group_id"
        const val EXTRA_IDEA_ID = "nextlist.idea_id"
        const val EXTRA_INVITATION_ID = "nextlist.invitation_id"
        private const val CHANNEL_ID = "nextlist_activity"
    }
}
