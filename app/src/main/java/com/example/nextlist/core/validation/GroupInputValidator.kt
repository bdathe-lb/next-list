package com.example.nextlist.core.validation

import com.example.nextlist.domain.model.InviteCredential
import com.example.nextlist.domain.model.InviteCredentialKind
import java.util.Locale

object GroupInputValidator {
    private val inviteCodePattern = Regex("^[A-HJ-NP-Z2-9]{8}$")

    fun normalizedName(value: String): String = value.trim()

    fun nameError(value: String): String? {
        val normalized = normalizedName(value)
        val length = normalized.codePointCount(0, normalized.length)
        return when {
            normalized.isEmpty() -> "请输入小组名称"
            length < 2 -> "小组名称至少需要 2 个字符"
            length > 30 -> "小组名称不能超过 30 个字符"
            else -> null
        }
    }

    fun normalizedInviteCode(value: String): String =
        value.replace(" ", "")
            .replace("-", "")
            .uppercase(Locale.ROOT)

    fun inviteCodeError(value: String): String? {
        val normalized = normalizedInviteCode(value)
        return when {
            normalized.isEmpty() -> "请输入邀请码"
            !inviteCodePattern.matches(normalized) -> "请输入 8 位有效邀请码"
            else -> null
        }
    }

    fun codeCredential(value: String): InviteCredential? =
        normalizedInviteCode(value).takeIf(inviteCodePattern::matches)?.let {
            InviteCredential(InviteCredentialKind.CODE, it)
        }
}
