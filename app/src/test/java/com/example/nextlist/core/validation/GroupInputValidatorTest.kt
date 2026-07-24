package com.example.nextlist.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GroupInputValidatorTest {
    @Test
    fun `group name is trimmed and counts Unicode code points`() {
        assertEquals("周末去哪", GroupInputValidator.normalizedName("  周末去哪  "))
        assertNull(GroupInputValidator.nameError("𠮷野"))
        assertEquals("小组名称至少需要 2 个字符", GroupInputValidator.nameError(" 组 "))
        assertEquals(
            "小组名称不能超过 30 个字符",
            GroupInputValidator.nameError("一".repeat(31)),
        )
    }

    @Test
    fun `invite code normalizes case separators and rejects ambiguous characters`() {
        assertEquals(
            "ABCDEF23",
            GroupInputValidator.normalizedInviteCode("ab-cd ef23"),
        )
        assertNull(GroupInputValidator.inviteCodeError("ab-cd ef23"))
        assertEquals(
            "请输入 8 位有效邀请码",
            GroupInputValidator.inviteCodeError("ABCD0F23"),
        )
    }
}
