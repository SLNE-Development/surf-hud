package dev.slne.surf.hud.core.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

/** `config.yml`: global settings of the HUD engine and its generated pack. */
@ConfigSerializable
data class HudSettings(
    /** Namespace of generated fonts, textures and the shader include; must not clash with the main pack. */
    val namespace: String = "surf",
    /** Client version whose jar provides the vanilla shader, ascii font and pack format. */
    @Setting("minecraft-version") val minecraftVersion: String = "26.3",
    /** Bossbar color whose sprites are blanked; the HUD bossbar uses it. */
    @Setting("bossbar-color") val bossbarColor: String = "pink",
    /** Ticks between HUD updates. */
    @Setting("update-interval") val updateInterval: Int = 2,
    @Setting("placeholder-intervals") val placeholderIntervals: PlaceholderIntervals = PlaceholderIntervals(),
    val heads: HeadSettings = HeadSettings(),
    val output: OutputSettings = OutputSettings(),
)

/** Cache time in ticks of the PlaceholderAPI (`papi`) and MiniPlaceholders (`mini`) bridges. */
@ConfigSerializable
data class PlaceholderIntervals(val papi: Int = 20, val mini: Int = 20) {
    fun millis(): Map<String, Long> = mapOf("papi" to papi * 50L, "mini" to mini * 50L)
}

/** Player head loading and caching. */
@ConfigSerializable
data class HeadSettings(
    /** Wait for heads to load before showing a HUD or popup. */
    @Setting("await-loaded") val awaitLoaded: Boolean = true,
    val timeout: String = "5s",
    @Setting("cache-ttl") val cacheTtl: String = "1d",
    /** Fallback head image, relative to `assets/`. A generated face is used when the file is missing. */
    val fallback: String = "heads/fallback.png",
)

/** Output paths of the generated pack, relative to the plugin folder. */
@ConfigSerializable
data class OutputSettings(val zip: String = "build/surf-hud.zip", val folder: String = "build/pack")
