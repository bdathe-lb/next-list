package com.example.nextlist.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AccountInputValidatorTest {
    @Test
    fun `normalizes email and nickname`() {
        assertEquals(
            "alice@example.com",
            AccountInputValidator.normalizedEmail("  Alice@Example.COM "),
        )
        assertEquals("小林", AccountInputValidator.normalizedNickname("  小林  "))
    }

    @Test
    fun `validates email password and confirmation`() {
        assertEquals("请输入有效的邮箱地址", AccountInputValidator.emailError("alice"))
        assertNull(AccountInputValidator.emailError("alice@example.com"))
        assertEquals("密码至少需要 6 位", AccountInputValidator.newPasswordError("12345"))
        assertNull(AccountInputValidator.newPasswordError("123456"))
        assertEquals(
            "两次输入的密码不一致",
            AccountInputValidator.passwordConfirmationError("123456", "654321"),
        )
    }

    @Test
    fun `counts nickname Unicode code points and trims before validation`() {
        assertEquals("昵称至少需要 2 个字符", AccountInputValidator.nicknameError(" 林 "))
        assertNull(AccountInputValidator.nicknameError("𠮷野"))
        assertEquals(
            "昵称不能超过 20 个字符",
            AccountInputValidator.nicknameError("一".repeat(21)),
        )
    }
}
