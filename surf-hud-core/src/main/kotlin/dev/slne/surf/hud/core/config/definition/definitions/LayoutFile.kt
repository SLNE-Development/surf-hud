package dev.slne.surf.hud.core.config.definition.definitions

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

/** File wrapper of `layouts/`. */
@ConfigSerializable
data class LayoutFile(@Setting(nodeFromParent = true) val entries: Map<String, LayoutDefinition> = emptyMap())