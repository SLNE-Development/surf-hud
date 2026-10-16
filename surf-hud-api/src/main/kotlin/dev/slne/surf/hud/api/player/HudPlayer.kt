package dev.slne.surf.hud.api

import java.util.*

/** What one player sees: the HUDs shown to them and their open popups. */
interface HudPlayer {
    val uuid: UUID

    /** Master switch; `false` hides everything without changing [shownHuds]. */
    var enabled: Boolean

    /** Ids of the HUDs currently shown to this player. */
    val shownHuds: Set<String>

    /**
     * Shows HUD [hud]. With `heads.await-loaded` this suspends until every head the HUD draws is loaded
     * (or timed out). Throws [IllegalArgumentException] for an unknown id.
     */
    suspend fun show(hud: String)

    /** Hides HUD [hud]. Throws [IllegalArgumentException] for an unknown id. */
    fun hide(hud: String)

    /**
     * Opens popup [id] with [variables], which its layouts read as placeholders. With `heads.await-loaded` this
     * suspends until its heads are loaded; the popup's duration starts when it appears. A `unique` popup that is
     * already open is updated and restarted instead, and its existing handle is returned.
     */
    suspend fun popup(id: String, variables: Map<String, Any?> = emptyMap()): PopupHandle

    /** Open popups, optionally only those with [id]. */
    fun popups(id: String? = null): List<PopupHandle>

    /** Re-renders this player's HUD on the next tick instead of waiting for the update interval. */
    fun refresh()
}
