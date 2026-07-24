package com.example.nextlist.core.validation

import com.example.nextlist.domain.model.IdeaCategory

object IdeaInputValidator {
    fun titleError(value: String): String? {
        val normalized = value.trim()
        return when {
            normalized.isEmpty() -> "请输入想法标题"
            TextValidator.unicodeLength(normalized) > 80 -> "标题最多 80 个字符"
            else -> null
        }
    }

    fun noteError(value: String): String? =
        if (TextValidator.unicodeLength(value.trim()) > 1_000) {
            "备注最多 1,000 个字符"
        } else {
            null
        }

    fun locationOrLinkError(value: String): String? =
        if (TextValidator.unicodeLength(value.trim()) > 500) {
            "地址或链接最多 500 个字符"
        } else {
            null
        }

    fun commentError(value: String): String? {
        val normalized = value.trim()
        return when {
            normalized.isEmpty() -> "请输入评论"
            TextValidator.unicodeLength(normalized) > 500 -> "评论最多 500 个字符"
            else -> null
        }
    }

    fun formIsValid(
        title: String,
        category: IdeaCategory,
        note: String,
        locationOrLink: String,
    ): Boolean = titleError(title) == null &&
        category in IdeaCategory.entries &&
        noteError(note) == null &&
        locationOrLinkError(locationOrLink) == null

    fun normalizedOptional(value: String): String? = value.trim().ifEmpty { null }
}
