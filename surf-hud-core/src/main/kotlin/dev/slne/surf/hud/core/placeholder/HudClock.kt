package dev.slne.surf.hud.core.placeholder

/** Wall clock in milliseconds; replaced in tests. */
fun interface HudClock {
    fun millis(): Long

    companion object {
        val SYSTEM = HudClock { System.currentTimeMillis() }
    }
}
