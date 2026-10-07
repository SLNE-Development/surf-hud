package dev.slne.surf.hud.example.paper.listener

import dev.slne.surf.hud.example.paper.hud.ExampleHud
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent

object ExampleHudListener : Listener {
    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        ExampleHud.apply(event.player)
    }
}
