package dev.slne.surf.hud.core.config

import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DurationsTest {
    @Test fun parses() {
        assertEquals(500.milliseconds, Durations.parse("500ms"))
        assertEquals(5.seconds, Durations.parse("5s"))
        assertEquals(1.days, Durations.parse(" 1d "))
    }
    @Test fun rejects() {
        assertFailsWith<IllegalArgumentException> { Durations.parse("5 parsecs") }
    }
}
