package dev.slne.surf.hud.paper

import dev.slne.surf.hud.api.PlaceholderRegistry
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import kotlin.time.Duration

/** Registers a placeholder resolved from the online [Player]; `null` while the player is offline. */
fun PlaceholderRegistry.player(name: String, interval: Duration? = null, resolver: (Player, String?) -> Any?) =
    register(name, interval) { uuid, argument -> Bukkit.getPlayer(uuid)?.let { resolver(it, argument) } }
