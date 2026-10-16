package dev.slne.surf.hud.core.config.definition

/** A definition with its id and the file (relative to the plugin folder) it came from. */
data class SourcedDefinition<T>(val id: String, val file: String, val value: T)