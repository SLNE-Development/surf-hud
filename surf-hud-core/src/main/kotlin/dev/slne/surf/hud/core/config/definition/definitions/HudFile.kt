package dev.slne.surf.hud.core.config.definition.definitions

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

/** File wrapper of `huds/`. */
@ConfigSerializable
data class HudFile(@Setting(nodeFromParent = true) val entries: Map<String, HudDefinition> = emptyMap())