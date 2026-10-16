package dev.slne.surf.hud.core.placeholder

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import kotlin.test.*

class LegacyTextTest {
    @Test fun `legacy section text becomes a component`() {
        val value = LegacyText.value("§aGreen")
        assertIs<Component>(value)
        assertEquals(NamedTextColor.GREEN, value.children().firstOrNull()?.color() ?: value.color())
    }

    @Test fun `plain text stays a string`() = assertEquals("1500", LegacyText.value("1500"))
}
