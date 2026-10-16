package dev.slne.surf.hud.paper

import dev.slne.surf.hud.core.platform.HudPlatform
import org.bukkit.Bukkit
import java.nio.file.Path
import java.util.*

/** Folia-safe platform: per-player work runs on the player's entity scheduler. */
object PaperHudPlatform : HudPlatform {
    override val dataFolder: Path get() = plugin.dataPath

    override fun viewer(uuid: UUID) = Bukkit.getPlayer(uuid)?.takeIf { it.isOnline }

    override fun execute(uuid: UUID, task: () -> Unit): Boolean {
        val player = Bukkit.getPlayer(uuid) ?: return false
        return player.scheduler.run(plugin, { task() }, null) != null
    }
}
