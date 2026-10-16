package dev.slne.surf.hud.core.config.definition.definitions

import org.spongepowered.configurate.objectmapping.ConfigSerializable

/** `images/`: `single` icon, `strip` (3 px wide: left, middle, right column) or `pixel` set. */
@ConfigSerializable
data class ImageDefinition(
    val type: String? = null,
    val file: String? = null,
    val size: Int? = null,
    val rows: Int? = null,
)