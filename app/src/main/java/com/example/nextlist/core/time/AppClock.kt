package com.example.nextlist.core.time

import java.time.Clock
import java.time.Instant

interface AppClock {
    fun now(): Instant
}

class SystemAppClock(
    private val clock: Clock = Clock.systemUTC(),
) : AppClock {
    override fun now(): Instant = clock.instant()
}
