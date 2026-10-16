package dev.slne.surf.hud.core.runtime

import dev.slne.surf.hud.api.HudPlayer
import dev.slne.surf.hud.api.PopupHandle
import dev.slne.surf.hud.core.HudEngine
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.expression.isTrue
import dev.slne.surf.hud.core.head.HeadPixels
import dev.slne.surf.hud.core.model.HeadSpec
import dev.slne.surf.hud.core.model.HudModel
import dev.slne.surf.hud.core.model.Placement
import dev.slne.surf.hud.core.model.PopupSpec
import dev.slne.surf.hud.core.placeholder.PlaceholderCache
import dev.slne.surf.hud.core.placeholder.RenderScope
import dev.slne.surf.hud.core.render.HeadKey
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * One player's HUD state: shown HUDs, open popups and the last rendered bossbar. API calls may come from any
 * thread; rendering ([update]) runs on the player's thread. All state is guarded by [lock].
 */
class PlayerHud(override val uuid: UUID, private val engine: HudEngine) : HudPlayer {
    private class CachedLayout(val key: List<Any?>, val component: Component)

    private val lock = Any()
    private val shown = LinkedHashSet<String>()
    private val stacks = LinkedHashMap<String, MutableList<PopupInstance>>()
    private val layouts = HashMap<Int, CachedLayout>()
    private val rememberedHeads = HashMap<HeadKey, HeadPixels>()
    private var cache: PlaceholderCache? = null
    private val bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS)
    private var shownTo: Audience? = null
    private var lastName: Component? = null
    private var ticks = 0

    @Volatile
    private var forced = true

    @Volatile
    override var enabled: Boolean = true
        set(value) {
            field = value
            refresh()
        }

    override val shownHuds: Set<String> get() = synchronized(lock) { shown.toSet() }

    override suspend fun show(hud: String) {
        val spec = engine.requireModel().huds[hud] ?: throw IllegalArgumentException("Unknown HUD '$hud'")
        engine.awaitHeads(this, spec.placements, emptyMap())
        synchronized(lock) { shown += hud }
        refresh()
    }

    override fun hide(hud: String) {
        require(hud in engine.requireModel().huds) { "Unknown HUD '$hud'" }
        synchronized(lock) { shown -= hud }
        refresh()
    }

    override suspend fun popup(id: String, variables: Map<String, Any?>): PopupHandle {
        val spec = engine.requireModel().popups[id] ?: throw IllegalArgumentException("Unknown popup '$id'")
        val values = variables.entries.associate { (key, value) -> key.lowercase() to Value.of(value) }
        engine.awaitHeads(this, spec.stacks.first(), values)
        val handle = synchronized(lock) { open(spec, values) }
        refresh()
        return handle
    }

    override fun popups(id: String?): List<PopupHandle> = synchronized(lock) {
        stacks.filterKeys { id == null || it == id }.values.flatten().map { it.handle }
    }

    override fun refresh() {
        forced = true
    }

    /** Opens a new instance at stack index 0 (dropping the oldest beyond `max-stack`), or restarts a unique one. */
    private fun open(spec: PopupSpec, values: Map<String, Value>): PopupHandle {
        val now = engine.clock.millis()
        val stack = stacks.getOrPut(spec.id) { mutableListOf() }
        if (spec.unique && stack.isNotEmpty()) {
            val instance = stack[0]
            instance.variables.clear()
            instance.variables.putAll(values)
            instance.openedAt = now
            return instance.handle
        }
        val instance = PopupInstance(spec, ConcurrentHashMap(values), now)
        instance.handle = PopupHandleImpl(instance, this)
        stack.add(0, instance)
        while (stack.size > spec.maxStack) stack.removeLast().open = false
        return instance.handle
    }

    /** Closes [instance] and removes it from its stack. */
    fun closePopup(instance: PopupInstance) {
        synchronized(lock) {
            instance.open = false
            stacks[instance.spec.id]?.remove(instance)
        }
        refresh()
    }

    /** Head names the given placements would draw now; evaluated on the player's thread. */
    fun headNames(placements: List<Placement>, variables: Map<String, Value>): List<String> = synchronized(lock) {
        val scope = RenderScope(uuid, cache(), variables, engine.warnings)
        placements.flatMap { it.layout.elements }.filterIsInstance<HeadSpec>()
            .map { it.player.evaluate(scope) }
            .filter { it != Value.Missing }
            .map { it.text() }
            .filter { it.isNotBlank() }
    }

    /**
     * Called every tick on the player's thread; renders when forced or when the update interval elapsed. The
     * engine's runtime is read once under [lock], so a frame never mixes two configurations.
     */
    fun update() {
        synchronized(lock) {
            val runtime = engine.runtime ?: return
            val model = runtime.model
            if (!forced && ++ticks < model.settings.updateInterval) return
            ticks = 0
            forced = false
            val audience = engine.platform.viewer(uuid) ?: return
            expirePopups()
            val cache = cache()
            cache.nextFrame()
            val parts = ArrayList<Pair<Int, Component>>()
            if (enabled) {
                val base = RenderScope(uuid, cache, emptyMap(), engine.warnings)
                for (id in shown) {
                    val hud = model.huds[id] ?: continue
                    if (!hud.conditions.all { it.isTrue(base) }) continue
                    hud.placements.forEach { placement ->
                        render(runtime, placement, base, null)?.let { parts += placement.slot to it }
                    }
                }
                for (stack in stacks.values) {
                    stack.forEachIndexed { index, instance ->
                        val scope = RenderScope(uuid, cache, HashMap(instance.variables), engine.warnings)
                        // Popups opened against an older model may still carry its spec; use the active one.
                        val spec = model.popups[instance.spec.id] ?: return@forEachIndexed
                        spec.stacks.getOrNull(index)?.forEach { placement ->
                            render(runtime, placement, scope, instance)?.let { parts += placement.slot to it }
                        }
                    }
                }
            }
            parts.sortBy { it.first }
            send(audience, model, Component.text().append(parts.map { it.second }).build(), parts.isNotEmpty())
        }
    }

    /**
     * Renders [placement] unless a condition hides it. The result is reused while the runtime, the layout's
     * placeholder values, the owning popup and the head version are unchanged.
     */
    private fun render(runtime: HudEngine.Runtime, placement: Placement, scope: RenderScope, owner: PopupInstance?): Component? {
        if (!placement.conditions.all { it.isTrue(scope) } || !placement.layout.conditions.all { it.isTrue(scope) }) return null
        val inputs = placement.layout.placeholders.map { scope.resolve(it) }
        val key = listOf(runtime, owner, inputs, runtime.heads.version)
        val component = layouts[placement.slot]?.takeIf { it.key == key }?.component
            ?: runtime.renderer.render(placement.slot, placement.layout, scope) { headKey, player -> head(runtime, headKey, player) }.also {
                layouts[placement.slot] = CachedLayout(key, it)
            }
        return component.takeUnless { it == Component.empty() }
    }

    /**
     * The head to draw for [player]: the cached one, else (while it loads) the head this element drew before
     * when `await-loaded` is on, else the fallback.
     */
    private fun head(runtime: HudEngine.Runtime, key: HeadKey, player: String): HeadPixels {
        val heads = runtime.heads
        heads.cached(player)?.let { pixels ->
            rememberedHeads[key] = pixels
            return pixels
        }
        heads.request(player)
        if (runtime.model.settings.heads.awaitLoaded) rememberedHeads[key]?.let { return it }
        return heads.fallback
    }

    private fun expirePopups() {
        val now = engine.clock.millis()
        stacks.values.forEach { stack ->
            stack.removeAll { instance ->
                val duration = instance.spec.durationTicks
                (duration > 0 && now - instance.openedAt >= duration * 50L).also { if (it) instance.open = false }
            }
        }
        stacks.values.removeAll { it.isEmpty() }
    }

    /** Shows, renames or hides the bossbar, sending only what changed. */
    private fun send(audience: Audience, model: HudModel, name: Component, visible: Boolean) {
        if (!visible) {
            shownTo?.hideBossBar(bar)
            shownTo = null
            return
        }
        if (bar.color() != model.bossbarColor) bar.color(model.bossbarColor)
        if (name != lastName) {
            bar.name(name)
            lastName = name
        }
        if (shownTo !== audience) {
            shownTo?.hideBossBar(bar)
            audience.showBossBar(bar)
            shownTo = audience
        }
    }

    private fun cache(): PlaceholderCache = cache ?: engine.newPlaceholderCache().also { cache = it }

    /** Applies a new model: drops unknown HUDs, closes removed popups, clears caches; on the first model, shows the defaults. */
    fun onModelChanged(model: HudModel, first: Boolean) {
        synchronized(lock) {
            if (first) model.huds.values.filter { it.default }.forEach { shown += it.id }
            shown.retainAll(model.huds.keys)
            val iterator = stacks.entries.iterator()
            while (iterator.hasNext()) {
                val (id, stack) = iterator.next()
                val spec = model.popups[id]
                if (spec == null) {
                    stack.forEach { it.open = false }
                    iterator.remove()
                } else {
                    stack.forEach { it.spec = spec }
                    while (stack.size > spec.maxStack) stack.removeLast().open = false
                }
            }
            layouts.clear()
            rememberedHeads.clear()
            cache = null
        }
        refresh()
    }

    /** The player (re)joined: the client lost the bossbar, so it must be shown again. */
    fun invalidate() {
        synchronized(lock) {
            shownTo = null
            lastName = null
        }
        refresh()
    }

    /** Hides the bossbar and closes every popup; the player left or the engine shuts down. */
    fun dispose() {
        synchronized(lock) {
            shownTo?.hideBossBar(bar)
            shownTo = null
            stacks.values.flatten().forEach { it.open = false }
            stacks.clear()
        }
    }
}
