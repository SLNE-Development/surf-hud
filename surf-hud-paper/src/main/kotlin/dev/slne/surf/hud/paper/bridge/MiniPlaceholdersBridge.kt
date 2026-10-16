package dev.slne.surf.hud.paper.bridge

import dev.slne.surf.hud.api.PlaceholderRegistry
import dev.slne.surf.hud.paper.player
import io.github.miniplaceholders.api.MiniPlaceholders
import net.kyori.adventure.text.minimessage.MiniMessage

/** `[mini:player_name]` → the MiniPlaceholders tag `<player_name>` for this player, as a component. */
object MiniPlaceholdersBridge {
    fun register(registry: PlaceholderRegistry) {
        registry.player("mini") { player, tag ->
            tag?.let { MiniMessage.miniMessage().deserialize("<$it>", player, MiniPlaceholders.audienceGlobalPlaceholders()) }
        }
    }
}
