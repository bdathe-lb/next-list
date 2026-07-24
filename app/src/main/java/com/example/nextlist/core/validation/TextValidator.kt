package com.example.nextlist.core.validation

object TextValidator {
    fun normalizedOrNull(
        value: String,
        minLength: Int,
        maxLength: Int,
    ): String? {
        require(minLength >= 0)
        require(maxLength >= minLength)

        return value.trim().takeIf { it.length in minLength..maxLength }
    }
}
