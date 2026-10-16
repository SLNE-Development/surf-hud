package dev.slne.surf.hud.core.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

@ConfigSerializable
data class HudSettingsConfig(
    val namespace: String = "surf",
    @Setting("minecraft-version") val minecraftVersion: String = "26.3",
    @Setting("bossbar-color") val bossbarColor: String = "pink",
    @Setting("update-interval") val updateInterval: Int = 2,
    @Setting("placeholder-intervals") val placeholderIntervals: PlaceholderIntervalsConfig = PlaceholderIntervalsConfig(),
    val heads: HeadSettingsConfig = HeadSettingsConfig(),
    val output: OutputSettingsConfig = OutputSettingsConfig(),
)

