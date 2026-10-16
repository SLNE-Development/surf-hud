package dev.slne.surf.hud.core.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable

/** Cache time in ticks of the PlaceholderAPI (`papi`) and MiniPlaceholders (`mini`) bridges. */
@ConfigSerializable
data class PlaceholderIntervalsConfig(val papi: Int = 20, val mini: Int = 20) {
    fun millis(): Map<String, Long> = mapOf("papi" to papi * 50L, "mini" to mini * 50L)
}