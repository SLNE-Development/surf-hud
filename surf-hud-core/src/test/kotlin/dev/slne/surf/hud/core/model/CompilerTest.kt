package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.TestDefinitions
import dev.slne.surf.hud.core.expression.PlaceholderRef
import net.kyori.adventure.key.Key
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.*

class CompilerTest {
    @TempDir lateinit var root: Path

    private fun errors(vararg changes: Pair<String, String>): List<String> =
        (TestDefinitions.compileResult(root, TestDefinitions.standardFiles() + changes) as CompileResult.Failure).errors

    @Test fun `assigns slots in stable order`() {
        val model = TestDefinitions.compile(root)
        // huds sorted by id: extra (1 placement), main (2); popups sorted: feed (2 stack slots), vote (1)
        assertEquals(listOf(0), model.huds.getValue("extra").placements.map { it.slot })
        assertEquals(listOf(1, 2), model.huds.getValue("main").placements.map { it.slot })
        assertEquals(listOf(listOf(3), listOf(4)), model.popups.getValue("feed").stacks.map { s -> s.map { it.slot } })
        assertEquals(listOf(listOf(5)), model.popups.getValue("vote").stacks.map { s -> s.map { it.slot } })
        assertEquals(6, model.slots.size)
    }

    @Test fun `popup slots include position and stack offset`() {
        val model = TestDefinitions.compile(root)
        val second = model.slots[4]
        assertEquals(Anchor.TOP_RIGHT, second.anchor)
        assertEquals(-4, second.offsetX)
        assertEquals(42, second.offsetY)
        assertEquals(100, model.popups.getValue("feed").durationTicks)
        assertEquals(-1, model.popups.getValue("vote").durationTicks)
    }

    @Test fun `fonts per slot and y, ascii separated`() {
        val model = TestDefinitions.compile(root)
        val money = model.huds.getValue("main").placements[0]
        val text = money.layout.elements[2]
        assertEquals(Key.key("surf", "hud/s1_3_default"), model.fontKey(1, text))
        assertEquals(Key.key("surf", "hud/s1_0"), model.fontKey(1, money.layout.elements[0]))
        assertEquals(Key.key("surf", "hud/s1_2"), model.fontKey(1, money.layout.elements[1]))
        assertTrue(model.fonts.getValue(Key.key("surf", "hud/s1_0")).glyphSets.single() is StripSet)
    }

    @Test fun `layout placeholders exclude widths`() {
        val model = TestDefinitions.compile(root)
        assertEquals(setOf(PlaceholderRef("money")), model.layouts.getValue("money").placeholders)
    }

    @Test fun `reports errors with file and path`() {
        val errors = errors("layouts/stats.yml" to """
            money:
              elements:
                - { type: image, image: panel, width: "mx(1, 2)" }
                - { type: text, font: nope, text: "x" }
                - { type: image, image: missing }
            health: { elements: [ { type: bar, image: panel, width: 60, value: "1" } ] }
            head: { elements: [ { type: head, image: head_small, player: "[target]" } ] }
        """)
        assertContains(errors, "layouts/stats.yml > money > elements[0].width: unknown function 'mx' at position 0")
        assertContains(errors, "layouts/stats.yml > money > elements[1].font: unknown text 'nope'")
        assertContains(errors, "layouts/stats.yml > money > elements[2].image: unknown image 'missing'")
    }

    @Test fun `width references must name a text element of the layout`() {
        val errors = errors("layouts/stats.yml" to """
            money: { elements: [ { type: image, image: panel, width: "[width:nope]" } ] }
            health: { elements: [ { type: bar, image: panel, width: 60, value: "1" } ] }
            head: { elements: [ { type: head, image: head_small, player: "[target]" } ] }
        """)
        assertTrue(errors.any { it.startsWith("layouts/stats.yml > money > elements[0].width") && "unknown text element 'nope'" in it }, errors.toString())
    }

    @Test fun `unknown layout and anchor in huds`() {
        val errors = errors("huds/main.yml" to """
            main:
              layouts:
                - { layout: nope, anchor: bottom_right }
                - { layout: money, anchor: sideways }
        """)
        assertContains(errors, "huds/main.yml > main > layouts[0].layout: unknown layout 'nope'")
        assertContains(errors.joinToString(), "huds/main.yml > main > layouts[1].anchor: unknown anchor 'sideways'")
    }

    @Test fun `element y must stay inside the slot`() {
        val errors = errors("layouts/stats.yml" to """
            money: { elements: [ { type: text, y: 600, text: "x" } ] }
            health: { elements: [ { type: bar, image: panel, width: 60, value: "1" } ] }
            head: { elements: [ { type: head, image: head_small, player: "[target]" } ] }
        """)
        assertTrue(errors.any { it.startsWith("layouts/stats.yml > money > elements[0].y") }, errors.toString())
    }

    @Test fun `invalid ids are rejected`() {
        val errors = errors("texts/default.yml" to "Default: { type: ascii }")
        assertTrue(errors.any { "Default" in it && "[a-z0-9_-]" in it })
    }

    @Test fun `invalid bossbar color is reported`() {
        val errors = errors("config.yml" to "bossbar-color: magenta")
        assertTrue(errors.any { it.startsWith("config.yml > bossbar-color") })
    }

    @Test fun `popup placements cannot set their own anchor`() {
        val errors = errors("popups/feed.yml" to """
            feed: { anchor: center, layouts: [ { layout: head, anchor: top_left } ] }
        """)
        assertContains(errors.single(), "anchor is set on the popup")
    }

    @Test fun `output paths must stay strictly inside the data folder`() {
        val outside = root.parent.resolve("elsewhere").toString().replace('\\', '/')
        for (folder in listOf("", ".", "..", "../pack", "build/../..", outside, "assets", "assets/pack", "cache", "images", "texts",
            "layouts", "huds", "popups", "config.yml")) {
            val errors = errors("config.yml" to "output: { folder: \"$folder\" }")
            assertTrue(errors.any { it.startsWith("config.yml > output.folder: ") }, "$folder: $errors")
        }
        for (zip in listOf("", "../surf-hud.zip", "build/pack/surf-hud.zip", "config.yml", "assets/surf-hud.zip")) {
            val errors = errors("config.yml" to "output: { zip: \"$zip\" }")
            assertTrue(errors.any { it.startsWith("config.yml > output.zip: ") }, "$zip: $errors")
        }
    }

    @Test fun `ordinary output paths compile`() {
        TestDefinitions.compile(root, TestDefinitions.standardFiles() + ("config.yml" to "output: { folder: out/pack, zip: out/hud.zip }"))
    }
}
