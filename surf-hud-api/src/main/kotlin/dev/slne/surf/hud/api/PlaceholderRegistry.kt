package dev.slne.surf.hud.api

import java.util.*
import kotlin.time.Duration

/**
 * Supplies the value of a placeholder for a viewer. [argument] is the part after the colon in
 * `[name:argument]`, or `null`. Return a `Number`, `String`, `Boolean`, Adventure `Component` or `null`.
 * Called on the viewer's thread.
 */
fun interface PlaceholderResolver {
    fun resolve(viewer: UUID, argument: String?): Any?
}

/** Registry of placeholders usable as `[name]` in HUD definitions. Names are case-insensitive. */
interface PlaceholderRegistry {
    /**
     * Registers [resolver] under [name], replacing an existing registration. With an [interval] the value is
     * cached per player for that long; without one it is resolved at most once per HUD update.
     */
    fun register(name: String, interval: Duration? = null, resolver: PlaceholderResolver)

    fun unregister(name: String)

    fun isRegistered(name: String): Boolean
}
