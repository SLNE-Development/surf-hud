package dev.slne.surf.hud.paper.listener

import dev.slne.surf.hud.paper.PaperSurfHud
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

object HudConnectionListener : Listener {
    @EventHandler(priority = EventPriority.LOWEST)
    fun onJoin(event: PlayerJoinEvent) {
        PaperSurfHud.INSTANCE.handleJoin(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        PaperSurfHud.INSTANCE.handleQuit(event.player.uniqueId)
    }
}
