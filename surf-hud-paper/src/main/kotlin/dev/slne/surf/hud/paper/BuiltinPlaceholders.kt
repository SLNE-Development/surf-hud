package dev.slne.surf.hud.paper

import dev.slne.surf.hud.api.PlaceholderRegistry
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute

/** Player data every HUD can use without other plugins. */
object BuiltinPlaceholders {
    fun register(registry: PlaceholderRegistry) = with(registry) {
        player("name") { p, _ -> p.name }
        player("display_name") { p, _ -> p.displayName() }
        player("health") { p, _ -> p.health }
        player("max_health") { p, _ -> p.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0 }
        player("food") { p, _ -> p.foodLevel }
        player("saturation") { p, _ -> p.saturation }
        player("armor") { p, _ -> p.getAttribute(Attribute.ARMOR)?.value ?: 0.0 }
        player("level") { p, _ -> p.level }
        player("exp") { p, _ -> p.exp }
        player("exp_percent") { p, _ -> p.exp * 100.0 }
        player("air") { p, _ -> p.remainingAir }
        player("max_air") { p, _ -> p.maximumAir }
        player("world") { p, _ -> p.world.name }
        player("x") { p, _ -> p.location.x }
        player("y") { p, _ -> p.location.y }
        player("z") { p, _ -> p.location.z }
        player("yaw") { p, _ -> p.location.yaw }
        player("ping") { p, _ -> p.ping }
        player("gamemode") { p, _ -> p.gameMode.name.lowercase() }
        player("sneaking") { p, _ -> p.isSneaking }
        player("flying") { p, _ -> p.isFlying }
        register("online") { _, _ -> Bukkit.getOnlinePlayers().size }
        register("max_players") { _, _ -> Bukkit.getMaxPlayers() }
    }
}
