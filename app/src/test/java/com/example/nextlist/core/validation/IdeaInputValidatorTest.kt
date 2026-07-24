package com.example.nextlist.core.validation

import com.example.nextlist.domain.model.IdeaCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaInputValidatorTest {
    @Test
    fun `unicode length counts supplementary characters once`() {
        assertEquals(1, TextValidator.unicodeLength("😀"))
        assertEquals(3, TextValidator.unicodeLength("A😀中"))
    }

    @Test
    fun `title accepts one to eighty Unicode characters`() {
        assertNull(IdeaInputValidator.titleError("想"))
        assertNull(IdeaInputValidator.titleError("😀".repeat(80)))
        assertEquals(
            "标题最多 80 个字符",
            IdeaInputValidator.titleError("😀".repeat(81)),
        )
        assertEquals("请输入想法标题", IdeaInputValidator.titleError("   "))
    }

    @Test
    fun `optional fields enforce Unicode limits after trimming`() {
        assertNull(IdeaInputValidator.noteError("😀".repeat(1_000)))
        assertEquals(
            "备注最多 1,000 个字符",
            IdeaInputValidator.noteError("😀".repeat(1_001)),
        )
        assertNull(IdeaInputValidator.locationOrLinkError("中".repeat(500)))
        assertEquals(
            "地址或链接最多 500 个字符",
            IdeaInputValidator.locationOrLinkError("中".repeat(501)),
        )
    }

    @Test
    fun `comment is required and limited to five hundred Unicode characters`() {
        assertEquals("请输入评论", IdeaInputValidator.commentError("\n "))
        assertNull(IdeaInputValidator.commentError("😀".repeat(500)))
        assertEquals(
            "评论最多 500 个字符",
            IdeaInputValidator.commentError("😀".repeat(501)),
        )
    }

    @Test
    fun `complete form validation includes every field`() {
        assertTrue(
            IdeaInputValidator.formIsValid(
                "看电影",
                IdeaCategory.MOVIE,
                "",
                "https://example.com",
            ),
        )
        assertFalse(
            IdeaInputValidator.formIsValid(
                "",
                IdeaCategory.OTHER,
                "",
                "",
            ),
        )
    }
}
