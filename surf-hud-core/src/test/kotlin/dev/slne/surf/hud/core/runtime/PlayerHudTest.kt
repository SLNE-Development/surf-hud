package dev.slne.surf.hud.core.runtime

import dev.slne.surf.hud.core.*
import dev.slne.surf.hud.core.placeholder.HudClock
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.*

class PlayerHudTest {
    @TempDir lateinit var root: Path
    private var now = 0L
    private val uuid = UUID.randomUUID()
    private lateinit var platform: FakePlatform
    private lateinit var engine: HudEngine
    private val audience get() = platform.audiences.getValue(uuid)

    @BeforeTest fun setUp() {
        TestDefinitions.write(root, TestDefinitions.standardFiles())
        platform = FakePlatform(root).also { it.audiences[uuid] = RecordingAudience() }
        engine = testEngine(platform, HudClock { now })
        engine.placeholderRegistry.register("money") { _, _ -> 1500 }
        engine.placeholderRegistry.register("health") { _, _ -> 10 }
        engine.placeholderRegistry.register("max_health") { _, _ -> 20 }
    }

    private fun reload() = runBlocking { engine.reload() }
    private fun tick() = engine.update(uuid)
    private fun name(): Component = audience.bars.single().name()

    @Test fun `reload writes the pack and shows default huds`() {
        val result = reload()
        assertTrue(result.success, result.errors.toString())
        assertNotNull(result.packHash)
        assertTrue(root.resolve("build/surf-hud.zip").exists())
        engine.handleJoin(uuid)
        tick()
        assertEquals(setOf("main"), engine.player(uuid).shownHuds)
        assertEquals(1, audience.bars.size)
    }

    @Test fun `bossbar name is only sent when it changes`() {
        reload(); engine.handleJoin(uuid); tick()
        val first = name()
        tick(); tick()
        assertSame(first, name())
    }

    @Test fun `disabled player sees nothing`() {
        reload(); engine.handleJoin(uuid); tick()
        engine.player(uuid).enabled = false
        tick()
        assertTrue(audience.bars.isEmpty())
    }

    @Test fun `unknown ids throw`() {
        reload()
        assertFailsWith<IllegalArgumentException> { runBlocking { engine.player(uuid).show("nope") } }
        assertFailsWith<IllegalArgumentException> { runBlocking { engine.player(uuid).popup("nope") } }
    }

    @Test fun `stacking popups drop the oldest and expire`() = runBlocking<Unit> {
        reload(); engine.handleJoin(uuid)
        val player = engine.player(uuid)
        val a = player.popup("feed", mapOf("target" to "A"))
        val b = player.popup("feed", mapOf("target" to "B"))
        val c = player.popup("feed", mapOf("target" to "C"))
        assertFalse(a.isOpen)
        assertEquals(listOf(c, b), player.popups("feed"))
        now = 100 * 50L
        tick()
        assertTrue(player.popups("feed").isEmpty())
        assertFalse(b.isOpen)
    }

    @Test fun `unique popups update in place`() = runBlocking<Unit> {
        reload(); engine.handleJoin(uuid)
        val player = engine.player(uuid)
        val first = player.popup("vote", mapOf("target" to "A"))
        val second = player.popup("vote", mapOf("target" to "B"))
        assertSame(first, second)
        assertEquals(1, player.popups("vote").size)
        first.close()
        assertFalse(first.isOpen)
        assertTrue(player.popups().isEmpty())
    }

    @Test fun `reload drops removed huds and popups`() = runBlocking<Unit> {
        reload(); engine.handleJoin(uuid)
        val player = engine.player(uuid)
        player.show("extra")
        val popup = player.popup("vote")
        root.resolve("huds/main.yml").writeText("main: { default: true, layouts: [ { layout: money, anchor: top_left } ] }")
        root.resolve("popups/feed.yml").writeText("feed: { anchor: center, layouts: [ head ] }")
        assertTrue(engine.reload().success)
        tick()
        assertEquals(setOf("main"), player.shownHuds)
        assertFalse(popup.isOpen)
    }

    @Test fun `failed reload keeps the previous model`() {
        reload()
        val model = engine.model
        root.resolve("layouts/stats.yml").writeText("money: { elements: [ { type: nope } ] }")
        val result = reload()
        assertFalse(result.success)
        assertSame(model, engine.model)
    }

    @Test fun `missing vanilla assets are a reload error`() {
        engine = testEngine(platform, HudClock { now }, assets = { _, _ -> error("offline") })
        val result = reload()
        assertContains(result.errors.single(), "offline")
        assertNull(engine.model)
    }

    @Test fun `show on an offline player does not wait`() = runBlocking<Unit> {
        root.resolve("huds/heads.yml").writeText("heads: { layouts: [ { layout: head, anchor: center } ] }")
        reload()
        platform.online = false
        engine.player(uuid).show("heads")
        assertTrue("heads" in engine.player(uuid).shownHuds)
    }
}
