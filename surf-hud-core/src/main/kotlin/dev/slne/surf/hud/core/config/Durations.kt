package dev.slne.surf.hud.core.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Parses config durations such as `500ms`, `5s`, `2m`, `1h`, `1d`. */
object Durations {
    private val PATTERN = Regex("""(\d+)\s*(ms|s|m|h|d)""")

    fun parse(text: String): Duration {
        val match = PATTERN.matchEntire(text.trim())
            ?: throw IllegalArgumentException("invalid duration '$text' (expected e.g. 500ms, 5s, 2m, 1h, 1d)")
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2]) {
            "ms" -> amount.milliseconds
            "s" -> amount.seconds
            "m" -> amount.minutes
            "h" -> amount.hours
            else -> amount.days
        }
    }
}
