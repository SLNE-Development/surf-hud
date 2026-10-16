package dev.slne.surf.hud.core.config.definition.definitions

/** A layout placed in a HUD or popup. In YAML either a map or just the layout id. */
data class PlacementDefinition(
    val layout: String,
    val anchor: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val conditions: List<String>? = null,
)