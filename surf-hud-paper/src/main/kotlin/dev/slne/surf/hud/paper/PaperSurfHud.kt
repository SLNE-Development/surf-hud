package dev.slne.surf.hud.paper

import com.google.auto.service.AutoService
import dev.slne.surf.hud.api.SurfHud
import dev.slne.surf.hud.core.AbstractSurfHud
import dev.slne.surf.hud.core.platform.HudPlatform
import net.kyori.adventure.util.Services
import org.bukkit.Bukkit
import java.util.*

@AutoService(SurfHud::class)
class PaperSurfHud : AbstractSurfHud(), Services.Fallback {
    override val platform: HudPlatform = PaperHudPlatform

    companion object {
        val INSTANCE get() = SurfHud.instance as PaperSurfHud
    }
}
