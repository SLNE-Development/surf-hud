package dev.slne.surf.hud.core.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

/** Player head loading and caching. */
@ConfigSerializable
data class HeadSettingsConfig(
    /** Wait for heads to load before showing a HUD or popup. */
    @Setting("await-loaded") val awaitLoaded: Boolean = true,
    val timeout: String = "5s",
    @Setting("cache-ttl") val cacheTtl: String = "1d",
    /** Fallback head image, relative to `assets/`. A generated face is used when the file is missing. */
    val fallback: String = "heads/fallback.png",
)