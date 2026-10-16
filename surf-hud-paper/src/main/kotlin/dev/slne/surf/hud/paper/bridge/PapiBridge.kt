package dev.slne.surf.hud.paper.bridge

import dev.slne.surf.hud.api.PlaceholderRegistry
import dev.slne.surf.hud.core.placeholder.LegacyText
import dev.slne.surf.hud.paper.player
import me.clip.placeholderapi.PlaceholderAPI

/** `[papi:vault_eco_balance]` → `%vault_eco_balance%`; `§` colors become components. Cached by `placeholder-intervals.papi`. */
object PapiBridge {
    fun register(registry: PlaceholderRegistry) {
        registry.player("papi") { player, identifier ->
            identifier?.let { LegacyText.value(PlaceholderAPI.setPlaceholders(player, "%$it%")) }
        }
    }
}
