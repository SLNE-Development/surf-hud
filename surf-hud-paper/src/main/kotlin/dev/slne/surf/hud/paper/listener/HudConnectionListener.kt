package dev.slne.surf.hud.paper.listener

import dev.slne.surf.hud.paper.PaperSurfHud
import dev.slne.surf.hud.paper.plugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/** Creates a player's HUD on join and ticks it every tick on the player's own scheduler. */
object HudConnectionListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = attach(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        PaperSurfHud.INSTANCE.handleQuit(event.player.uniqueId)
    }

    fun attach(player: Player) {
        val engine = PaperSurfHud.INSTANCE
        val uuid = player.uniqueId
        engine.handleJoin(uuid)
        // The entity scheduler retires the task when the player leaves.
        player.scheduler.runAtFixedRate(plugin, { engine.update(uuid) }, null, 1L, 1L)
    }
}
