package com.example.nextlist.core.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Test

class AppClockTest {
    @Test
    fun `fixed clock makes time dependent code deterministic`() {
        val expected = Instant.parse("2026-07-23T12:00:00Z")
        val clock = SystemAppClock(
            Clock.fixed(expected, ZoneOffset.UTC),
        )

        assertEquals(expected, clock.now())
    }
}
