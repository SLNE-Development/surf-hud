package dev.slne.surf.hud.core.config.definition.definitions

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

/** File wrapper of `texts/`. */
@ConfigSerializable
data class TextFile(@Setting(nodeFromParent = true) val entries: Map<String, TextDefinition> = emptyMap())