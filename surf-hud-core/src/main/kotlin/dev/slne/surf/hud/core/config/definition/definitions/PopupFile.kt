package dev.slne.surf.hud.core.config.definition.definitions

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

/** File wrapper of `popups/`. */
@ConfigSerializable
data class PopupFile(@Setting(nodeFromParent = true) val entries: Map<String, PopupDefinition> = emptyMap())