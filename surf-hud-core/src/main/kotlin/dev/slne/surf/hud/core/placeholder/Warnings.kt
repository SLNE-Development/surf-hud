package dev.slne.surf.hud.core.placeholder

import java.util.concurrent.ConcurrentHashMap

/** Logs each warning key only once, so a broken HUD does not flood the log every update. */
class WarningSink(private val log: (String) -> Unit) {
    private val seen = ConcurrentHashMap.newKeySet<String>()

    fun once(key: String, message: String) {
        if (seen.add(key)) log(message)
    }
}

/** Logs resolver failures at most once per minute per placeholder name. */
class ResolverErrorLog(private val clock: HudClock, private val log: (String, Throwable) -> Unit) {
    private val last = ConcurrentHashMap<String, Long>()

    fun report(name: String, error: Throwable) {
        val now = clock.millis()
        val previous = last[name]
        if (previous == null || now - previous >= 60_000) {
            last[name] = now
            log(name, error)
        }
    }
}
