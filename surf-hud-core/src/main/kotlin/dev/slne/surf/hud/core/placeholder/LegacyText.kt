package dev.slne.surf.hud.core.placeholder

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** Converts bridge output: text with `§` color codes becomes a component, anything else stays a string. */
object LegacyText {
    fun value(text: String): Any =
        if ('§' in text) LegacyComponentSerializer.legacySection().deserialize(text) else text
}
