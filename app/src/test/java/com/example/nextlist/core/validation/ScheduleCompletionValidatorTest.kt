package com.example.nextlist.core.validation

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleCompletionValidatorTest {
    @Test
    fun `date time and IANA timezone validation accepts valid values`() {
        assertNull(ScheduleCompletionValidator.dateError("2026-07-24"))
        assertNull(ScheduleCompletionValidator.timeError("09:05"))
        assertNull(ScheduleCompletionValidator.timezoneError("Asia/Shanghai"))
        assertEquals(
            LocalDate.of(2026, 7, 24),
            ScheduleCompletionValidator.parseDate("2026-07-24"),
        )
        assertEquals(LocalTime.of(9, 5), ScheduleCompletionValidator.parseTime("09:05"))
        assertEquals(
            ZoneId.of("Asia/Shanghai"),
            ScheduleCompletionValidator.zoneId("Asia/Shanghai"),
        )
    }

    @Test
    fun `required date time and invalid timezone expose safe errors`() {
        assertNotNull(ScheduleCompletionValidator.dateError(""))
        assertNotNull(ScheduleCompletionValidator.dateError("2026-02-30"))
        assertNotNull(ScheduleCompletionValidator.timeError(""))
        assertNotNull(ScheduleCompletionValidator.timeError("25:00"))
        assertNotNull(ScheduleCompletionValidator.timezoneError("Shanghai"))
    }

    @Test
    fun `schedule and completion text limits count Unicode code points`() {
        assertNull(ScheduleCompletionValidator.meetingPointError("地".repeat(200)))
        assertNotNull(ScheduleCompletionValidator.meetingPointError("地".repeat(201)))
        assertNull(ScheduleCompletionValidator.scheduleNoteError("🙂".repeat(500)))
        assertNotNull(ScheduleCompletionValidator.scheduleNoteError("🙂".repeat(501)))
        assertNull(ScheduleCompletionValidator.reviewError("好".repeat(500)))
        assertNotNull(ScheduleCompletionValidator.reviewError("好".repeat(501)))
    }

    @Test
    fun `rating allows null or integer one through five`() {
        assertNull(ScheduleCompletionValidator.ratingError(null))
        assertNull(ScheduleCompletionValidator.ratingError(1))
        assertNull(ScheduleCompletionValidator.ratingError(5))
        assertNotNull(ScheduleCompletionValidator.ratingError(0))
        assertNotNull(ScheduleCompletionValidator.ratingError(6))
    }
}
