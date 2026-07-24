package com.example.nextlist.core.validation

object TextValidator {
    fun unicodeLength(value: String): Int =
        value.codePointCount(0, value.length)

    fun normalizedOrNull(
        value: String,
        minLength: Int,
        maxLength: Int,
    ): String? {
        require(minLength >= 0)
        require(maxLength >= minLength)

        return value.trim().takeIf { unicodeLength(it) in minLength..maxLength }
    }
}
