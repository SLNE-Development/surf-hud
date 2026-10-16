package dev.slne.surf.hud.core.config.definition

import dev.slne.surf.hud.core.config.*
import dev.slne.surf.hud.core.config.definition.definitions.HudDefinition
import dev.slne.surf.hud.core.config.definition.definitions.ImageDefinition
import dev.slne.surf.hud.core.config.definition.definitions.LayoutDefinition
import dev.slne.surf.hud.core.config.definition.definitions.PopupDefinition
import dev.slne.surf.hud.core.config.definition.definitions.TextDefinition

/** Everything read from the plugin folder, plus read errors (unreadable files, duplicate ids). */
data class DefinitionSet(
    val settings: HudSettingsConfig,
    val images: Map<String, SourcedDefinition<ImageDefinition>>,
    val texts: Map<String, SourcedDefinition<TextDefinition>>,
    val layouts: Map<String, SourcedDefinition<LayoutDefinition>>,
    val huds: Map<String, SourcedDefinition<HudDefinition>>,
    val popups: Map<String, SourcedDefinition<PopupDefinition>>,
    val errors: List<String>,
)