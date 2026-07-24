package com.example.nextlist.core.validation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextValidatorTest {
    @Test
    fun trimsValidInput() {
        assertEquals(
            "周末去哪",
            TextValidator.normalizedOrNull("  周末去哪  ", minLength = 2, maxLength = 30),
        )
    }

    @Test
    fun rejectsInputOutsideBounds() {
        assertNull(TextValidator.normalizedOrNull("a", minLength = 2, maxLength = 30))
        assertNull(TextValidator.normalizedOrNull("abcd", minLength = 1, maxLength = 3))
    }
}
