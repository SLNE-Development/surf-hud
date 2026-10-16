package dev.slne.surf.hud.paper

import dev.slne.surf.hud.core.head.SkinSource
import org.bukkit.Bukkit
import java.util.*

/** Skins of players on this server, read from their profile without any network request. */
object PaperSkinSource : SkinSource {
    override suspend fun skinUrl(player: String): String? {
        val online = runCatching { UUID.fromString(player) }.getOrNull()?.let(Bukkit::getPlayer) ?: Bukkit.getPlayerExact(player)
        return online?.playerProfile?.textures?.skin?.toString()
    }
}
