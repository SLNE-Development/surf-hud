package dev.slne.surf.hud.core.placeholder

import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.Value
import java.util.*

/**
 * Per-player placeholder values. A placeholder without interval is resolved at most once per frame (one HUD
 * update); one with an interval (registered, or from [defaultIntervals] by name) is reused until it expires.
 * Not thread-safe; the owning player state serializes access.
 */
class PlaceholderCache(
    private val registry: PlaceholderRegistryImpl,
    private val clock: HudClock,
    private val defaultIntervals: Map<String, Long>,
    private val warnings: WarningSink,
    private val errors: ResolverErrorLog,
) {
    private class Entry(val value: Value, val frame: Long, val expiresAt: Long)

    private val entries = HashMap<PlaceholderRef, Entry>()
    private var frame = 0L

    /** Starts a new frame, invalidating all interval-less entries. */
    fun nextFrame() {
        frame++
    }

    /** Resolves [ref] for [viewer]; unknown names and failing resolvers yield [Value.Missing]. */
    fun resolve(viewer: UUID, ref: PlaceholderRef): Value {
        val registration = registry[ref.name] ?: run {
            warnings.once("unknown:${ref.name}", "Unknown placeholder [${ref.name}]")
            return Value.Missing
        }
        val now = clock.millis()
        val interval = registration.interval?.inWholeMilliseconds ?: defaultIntervals[ref.name] ?: 0L
        entries[ref]?.let { entry ->
            val fresh = if (interval > 0) entry.expiresAt > now else entry.frame == frame
            if (fresh) return entry.value
        }
        val value = try {
            Value.of(registration.resolver.resolve(viewer, ref.argument))
        } catch (e: Exception) {
            errors.report(ref.name, e)
            Value.Missing
        }
        entries[ref] = Entry(value, frame, now + interval)
        return value
    }
}
