package dev.slne.surf.hud.core.config.definition.definitions

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

/** `popups/`: temporary layouts, optionally stacking. */
@ConfigSerializable
data class PopupDefinition(
    val anchor: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val duration: Int? = null,
    val unique: Boolean? = null,
    @Setting("max-stack") val maxStack: Int? = null,
    @Setting("stack-offset") val stackOffset: List<Int>? = null,
    val layouts: List<PlacementDefinition>? = null,
)