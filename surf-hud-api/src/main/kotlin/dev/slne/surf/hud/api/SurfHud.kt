package dev.slne.surf.hud.api

import dev.slne.surf.api.core.util.requiredService
import net.kyori.adventure.identity.Identified
import java.util.*

private val service = requiredService<SurfHud>()

/**
 * Entry point of surf-hud. HUDs, layouts and popups are defined in YAML in the plugin folder; code shows and
 * hides them, opens popups and supplies placeholder values.
 */
interface SurfHud {
    /** Placeholders that HUD texts and expressions can use as `[name]` or `[name:argument]`. */
    val placeholders: PlaceholderRegistry

    /** Ids of the HUDs in the active configuration. */
    val huds: Set<String>

    /** Ids of the popups in the active configuration. */
    val popups: Set<String>

    /** The HUD state of a player; created on first access. */
    fun player(uuid: UUID): HudPlayer

    /** Opens popup [id] for every viewer with the same [variables]; see [HudPlayer.popup]. */
    suspend fun popup(
        id: String,
        viewers: Collection<UUID>,
        variables: Map<String, Any?> = emptyMap(),
    ): List<PopupHandle>

    /**
     * Reloads every definition, regenerates the resource pack and swaps the new configuration in. On any error
     * the previous configuration stays active and the result lists the errors.
     */
    suspend fun reload(): ReloadResult

    companion object : SurfHud by service {
        val instance: SurfHud get() = service
    }
}

/** The HUD state of this player or audience. */
val Identified.hudPlayer: HudPlayer
    get() = SurfHud.player(identity().uuid())
