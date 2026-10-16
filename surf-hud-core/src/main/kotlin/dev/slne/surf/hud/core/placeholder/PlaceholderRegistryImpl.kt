package dev.slne.surf.hud.core.placeholder

import dev.slne.surf.hud.api.PlaceholderRegistry
import dev.slne.surf.hud.api.PlaceholderResolver
import dev.slne.surf.hud.core.expression.PlaceholderRef
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/** Thread-safe placeholder registry; names are stored lower-case. */
class PlaceholderRegistryImpl : PlaceholderRegistry {
    data class Registration(val name: String, val interval: Duration?, val resolver: PlaceholderResolver)

    private val registrations = ConcurrentHashMap<String, Registration>()

    override fun register(name: String, interval: Duration?, resolver: PlaceholderResolver) {
        require(PlaceholderRef.NAME.matches(name)) { "Invalid placeholder name '$name'" }
        require(!name.equals("width", ignoreCase = true)) { "'width' is reserved for element widths" }
        registrations[name.lowercase()] = Registration(name.lowercase(), interval, resolver)
    }

    override fun unregister(name: String) {
        registrations.remove(name.lowercase())
    }

    override fun isRegistered(name: String) = registrations.containsKey(name.lowercase())

    operator fun get(name: String): Registration? = registrations[name.lowercase()]
}
