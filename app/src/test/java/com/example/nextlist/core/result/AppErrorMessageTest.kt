package com.example.nextlist.core.result

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppErrorMessageTest {
    @Test
    fun `login credential errors use a safe Chinese message`() {
        assertEquals(
            "邮箱或密码不正确",
            AppError.UNAUTHENTICATED.toUserMessage(AppOperation.LOGIN),
        )
    }

    @Test
    fun `messages never expose Firebase implementation details`() {
        AppError.entries.forEach { error ->
            val message = error.toUserMessage()
            assertFalse(message.contains("Firebase", ignoreCase = true))
            assertFalse(message.contains("PERMISSION_DENIED"))
            assertFalse(message.contains("Exception"))
        }
    }
}
