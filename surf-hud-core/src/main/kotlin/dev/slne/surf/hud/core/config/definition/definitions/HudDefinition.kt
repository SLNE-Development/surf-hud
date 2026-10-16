package dev.slne.surf.hud.core.config.definition.definitions

import org.spongepowered.configurate.objectmapping.ConfigSerializable

/** `huds/`: what a player sees. */
@ConfigSerializable
data class HudDefinition(
    val default: Boolean? = null,
    val conditions: List<String>? = null,
    val layouts: List<PlacementDefinition>? = null,
)