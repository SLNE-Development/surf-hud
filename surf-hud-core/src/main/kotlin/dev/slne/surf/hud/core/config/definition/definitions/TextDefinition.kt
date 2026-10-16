package dev.slne.surf.hud.core.config.definition.definitions

import org.spongepowered.configurate.objectmapping.ConfigSerializable

/** `texts/`: an `ascii` font at [height] (default 8). */
@ConfigSerializable
data class TextDefinition(val type: String? = null, val height: Int? = null)