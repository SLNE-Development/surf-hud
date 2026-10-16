package dev.slne.surf.hud.core.platform

import net.kyori.adventure.audience.Audience
import java.nio.file.Path
import java.util.*

/** What the engine needs from the server platform. */
interface HudPlatform {
    /** The plugin folder holding `config.yml`, the definition folders, `assets/`, `cache/` and `build/`. */
    val dataFolder: Path

    /** The online player as an audience, or `null`. */
    fun viewer(uuid: UUID): Audience?

    /** Runs [task] on the player's thread; returns `false` (and drops the task) if the player is offline. */
    fun execute(uuid: UUID, task: () -> Unit): Boolean
}
