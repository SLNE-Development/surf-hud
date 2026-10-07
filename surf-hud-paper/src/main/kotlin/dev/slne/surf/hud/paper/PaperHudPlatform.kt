package dev.slne.surf.hud.paper

import dev.slne.surf.hud.core.platform.HudPlatform
import org.bukkit.Bukkit
import java.util.UUID

object PaperHudPlatform : HudPlatform {
    override fun viewer(uuid: UUID) = Bukkit.getPlayer(uuid)?.takeIf { it.isOnline }
}