package dev.slne.surf.hud.core.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable

/** Output paths of the generated pack, relative to the plugin folder. */
@ConfigSerializable
data class OutputSettingsConfig(
    val zip: String = "build/surf-hud.zip",
    val folder: String = "build/pack"
)