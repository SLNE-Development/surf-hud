package dev.slne.surf.hud.core.config.definition.definitions

import org.spongepowered.configurate.objectmapping.ConfigSerializable

/** One layout element; which fields apply depends on [type] (`text`, `image`, `bar`, `head`). */
@ConfigSerializable
data class ElementDefinition(
    val type: String? = null,
    val id: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val conditions: List<String>? = null,
    val font: String? = null,
    val text: String? = null,
    val align: String? = null,
    val shadow: Boolean? = null,
    val image: String? = null,
    val width: String? = null,
    val color: String? = null,
    val value: String? = null,
    val direction: String? = null,
    val player: String? = null,
    val brightness: String? = null,
)