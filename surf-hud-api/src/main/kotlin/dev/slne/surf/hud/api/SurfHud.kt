package dev.slne.surf.hud.api

import dev.slne.surf.api.core.util.requiredService
import dev.slne.surf.hud.api.theme.HudTheme
import net.kyori.adventure.identity.Identified
import java.util.*

private val service = requiredService<SurfHud>()

interface SurfHud {
    var theme: HudTheme

    fun hud(viewer: UUID): Hud

    fun hudOrNull(viewer: UUID): Hud?

    fun remove(viewer: UUID)

    companion object : SurfHud by service {
        val instance: SurfHud get() = service
    }
}

val Identified.hud: Hud
    get() = SurfHud.hud(identity().uuid())
