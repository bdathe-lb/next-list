package com.example.nextlist.data.preferences

import androidx.test.platform.app.InstrumentationRegistry
import com.example.nextlist.domain.model.NotificationType
import com.example.nextlist.domain.model.PendingNotificationTarget
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PendingNotificationRepositoryTest {
    @Test
    fun pendingTargetSurvivesRepositoryRecreationAndConsumedMessageDoesNotReplay() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .applicationContext
        val messageId = "message-${System.nanoTime()}"
        val target = PendingNotificationTarget(
            messageId = messageId,
            type = NotificationType.SCHEDULE_UPDATED,
            groupId = "group-1",
            ideaId = "idea-1",
            invitationId = null,
        )
        val firstRepository = DataStorePendingNotificationRepository(context)
        firstRepository.save(target)

        val recreatedRepository = DataStorePendingNotificationRepository(context)
        assertEquals(target, recreatedRepository.observe().first())

        recreatedRepository.consume(messageId)
        assertNull(recreatedRepository.observe().first())
        firstRepository.save(target)
        assertNull(firstRepository.observe().first())
    }
}
