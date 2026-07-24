package com.example.nextlist.core.validation

import java.util.Locale

object AccountInputValidator {
    private const val PASSWORD_MIN_LENGTH = 6
    private const val PASSWORD_MAX_LENGTH = 128
    private val emailPattern = Regex(
        pattern = "^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$",
        option = RegexOption.IGNORE_CASE,
    )

    fun normalizedEmail(value: String): String = value.trim().lowercase(Locale.ROOT)

    fun normalizedNickname(value: String): String = value.trim()

    fun emailError(value: String): String? {
        val normalized = normalizedEmail(value)
        return when {
            normalized.isEmpty() -> "请输入邮箱"
            !emailPattern.matches(normalized) -> "请输入有效的邮箱地址"
            else -> null
        }
    }

    fun loginPasswordError(value: String): String? =
        if (value.isEmpty()) "请输入密码" else null

    fun newPasswordError(value: String): String? = when {
        value.isEmpty() -> "请输入密码"
        value.length < PASSWORD_MIN_LENGTH -> "密码至少需要 6 位"
        value.length > PASSWORD_MAX_LENGTH -> "密码不能超过 128 位"
        else -> null
    }

    fun passwordConfirmationError(password: String, confirmation: String): String? = when {
        confirmation.isEmpty() -> "请再次输入密码"
        confirmation != password -> "两次输入的密码不一致"
        else -> null
    }

    fun nicknameError(value: String): String? {
        val normalized = normalizedNickname(value)
        val length = normalized.codePointCount(0, normalized.length)
        return when {
            normalized.isEmpty() -> "请输入昵称"
            length < 2 -> "昵称至少需要 2 个字符"
            length > 20 -> "昵称不能超过 20 个字符"
            else -> null
        }
    }
}
