package dev.slne.surf.hud.core.config

import dev.slne.surf.hud.core.ConfigurateTestReader
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.*

class DefinitionLoaderTest {
    @TempDir lateinit var root: Path

    private fun file(path: String, text: String) {
        root.resolve(path).also { it.parent.createDirectories() }.writeText(text.trimIndent())
    }

    @Test fun `loads every folder with several entries per file`() {
        file("images/a.yml", """
            coin: { type: single, file: icons/coin.png }
            panel: { type: strip, file: strips/panel.png }
        """)
        file("layouts/sub/b.yml", """
            money:
              align: right
              elements:
                - { type: text, x: 3, text: "[money]" }
        """)
        file("huds/main.yml", """
            main:
              default: true
              layouts:
                - { layout: money, anchor: bottom_right, x: -4, y: -17 }
        """)
        file("popups/vote.yml", """
            vote:
              anchor: center
              max-stack: 3
              stack-offset: [0, 12]
              layouts:
                - money
                - { layout: money, y: 10 }
        """)
        val set = DefinitionLoader(ConfigurateTestReader).load(root)

        assertEquals(emptyList(), set.errors)
        assertEquals(setOf("coin", "panel"), set.images.keys)
        assertEquals("images/a.yml", set.images.getValue("coin").file)
        assertEquals("layouts/sub/b.yml", set.layouts.getValue("money").file)
        assertEquals("[money]", set.layouts.getValue("money").value.elements!!.single().text)
        assertEquals(PlacementDefinition("money", "bottom_right", -4, -17), set.huds.getValue("main").value.layouts!!.single())
        val vote = set.popups.getValue("vote").value
        assertEquals(3, vote.maxStack)
        assertEquals(listOf(0, 12), vote.stackOffset)
        assertEquals(listOf(PlacementDefinition("money"), PlacementDefinition("money", y = 10)), vote.layouts)
    }

    @Test fun `numbers are read into expression strings`() {
        file("layouts/a.yml", """
            bar:
              elements:
                - { type: image, image: panel, width: 60 }
        """)
        assertEquals("60", DefinitionLoader(ConfigurateTestReader).load(root).layouts.getValue("bar").value.elements!!.single().width)
    }

    @Test fun `duplicate ids are reported with both files`() {
        file("texts/a.yml", "default: { type: ascii }")
        file("texts/b.yml", "default: { type: ascii, height: 16 }")
        val error = DefinitionLoader(ConfigurateTestReader).load(root).errors.single()
        assertContains(error, "texts/b.yml > default")
        assertContains(error, "texts/a.yml")
    }

    @Test fun `unreadable files are reported and the rest still loads`() {
        file("texts/broken.yml", "default: [ unclosed")
        file("texts/ok.yml", "title: { type: ascii }")
        val set = DefinitionLoader(ConfigurateTestReader).load(root)
        assertEquals(setOf("title"), set.texts.keys)
        assertContains(set.errors.single(), "texts/broken.yml")
    }

    @Test fun `missing config yml gives defaults`() {
        val settings = DefinitionLoader(ConfigurateTestReader).load(root).settings
        assertEquals("surf", settings.namespace)
        assertEquals(2, settings.updateInterval)
        assertEquals(mapOf("papi" to 1000L, "mini" to 1000L), settings.placeholderIntervals.millis())
    }

    @Test fun `config yml keys are dashed`() {
        file("config.yml", """
            minecraft-version: "26.4"
            update-interval: 5
            heads: { await-loaded: false, cache-ttl: 2h }
        """)
        val settings = DefinitionLoader(ConfigurateTestReader).load(root).settings
        assertEquals("26.4", settings.minecraftVersion)
        assertEquals(5, settings.updateInterval)
        assertFalse(settings.heads.awaitLoaded)
        assertEquals("2h", settings.heads.cacheTtl)
        assertEquals("5s", settings.heads.timeout)
    }
}
