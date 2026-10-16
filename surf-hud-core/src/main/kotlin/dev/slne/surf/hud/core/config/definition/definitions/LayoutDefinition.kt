package dev.slne.surf.hud.core.config.definition.definitions

import org.spongepowered.configurate.objectmapping.ConfigSerializable

/** `layouts/`: a reusable group of elements. */
@ConfigSerializable
data class LayoutDefinition(
    val align: String? = null,
    val conditions: List<String>? = null,
    val elements: List<ElementDefinition>? = null,
)