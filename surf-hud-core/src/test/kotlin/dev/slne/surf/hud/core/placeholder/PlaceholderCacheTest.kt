package dev.slne.surf.hud.core.placeholder

import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.Value
import java.util.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class PlaceholderCacheTest {
    private var now = 0L
    private val clock = HudClock { now }
    private val registry = PlaceholderRegistryImpl()
    private val warnings = mutableListOf<String>()
    private val errors = mutableListOf<String>()
    private val cache = PlaceholderCache(
        registry, clock, mapOf("papi" to 1000L), WarningSink { warnings += it },
        ResolverErrorLog(clock) { name, _ -> errors += name },
    )
    private val viewer = UUID.randomUUID()

    @Test fun `resolves once per frame without interval`() {
        var calls = 0
        registry.register("money") { _, _ -> ++calls }
        assertEquals(Value.Num(1.0), cache.resolve(viewer, PlaceholderRef("money")))
        assertEquals(Value.Num(1.0), cache.resolve(viewer, PlaceholderRef("money")))
        cache.nextFrame()
        assertEquals(Value.Num(2.0), cache.resolve(viewer, PlaceholderRef("money")))
    }

    @Test fun `caches for the registered interval`() {
        var calls = 0
        registry.register("slow", 2.seconds) { _, _ -> ++calls }
        cache.resolve(viewer, PlaceholderRef("slow"))
        cache.nextFrame(); now = 1999
        assertEquals(Value.Num(1.0), cache.resolve(viewer, PlaceholderRef("slow")))
        now = 2000
        assertEquals(Value.Num(2.0), cache.resolve(viewer, PlaceholderRef("slow")))
    }

    @Test fun `bridge names use the default interval and arguments are separate entries`() {
        registry.register("papi") { _, argument -> argument + now }
        assertEquals(Value.Str("a0"), cache.resolve(viewer, PlaceholderRef("papi", "a")))
        assertEquals(Value.Str("b0"), cache.resolve(viewer, PlaceholderRef("papi", "b")))
        cache.nextFrame(); now = 500
        assertEquals(Value.Str("a0"), cache.resolve(viewer, PlaceholderRef("papi", "a")))
    }

    @Test fun `unknown placeholder is missing and warns once`() {
        repeat(3) { cache.nextFrame(); assertEquals(Value.Missing, cache.resolve(viewer, PlaceholderRef("nope"))) }
        assertEquals(1, warnings.size)
    }

    @Test fun `throwing resolver is missing and logged at most once per minute`() {
        registry.register("bad") { _, _ -> error("boom") }
        repeat(3) { cache.nextFrame(); assertEquals(Value.Missing, cache.resolve(viewer, PlaceholderRef("bad"))) }
        now = 60_000; cache.nextFrame(); cache.resolve(viewer, PlaceholderRef("bad"))
        assertEquals(listOf("bad", "bad"), errors)
    }

    @Test fun `names are case insensitive and validated`() {
        registry.register("Money") { _, _ -> 1 }
        assertTrue(registry.isRegistered("money"))
        assertFailsWith<IllegalArgumentException> { registry.register("width") { _, _ -> 1 } }
        assertFailsWith<IllegalArgumentException> { registry.register("a b") { _, _ -> 1 } }
    }

    @Test fun `scope prefers variables then widths then registry`() {
        registry.register("left") { _, _ -> "registry" }
        val scope = RenderScope(viewer, cache, mapOf("left" to Value.Str("variable")), WarningSink {}).withWidths(mapOf("t" to 12))
        assertEquals(Value.Str("variable"), scope.resolve(PlaceholderRef("left")))
        assertEquals(Value.Num(12.0), scope.resolve(PlaceholderRef("width", "t")))
        assertEquals(Value.Missing, scope.resolve(PlaceholderRef("width", "other")))
    }
}
