package dev.slne.surf.hud.core.platform

import net.kyori.adventure.audience.Audience
import java.util.*

interface HudPlatform {
    fun viewer(uuid: UUID): Audience?
}
