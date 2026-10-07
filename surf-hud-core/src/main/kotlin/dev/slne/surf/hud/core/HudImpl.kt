package dev.slne.surf.hud.core

import dev.slne.surf.hud.api.Hud
import dev.slne.surf.hud.api.HudElement
import dev.slne.surf.hud.api.theme.HudTheme
import dev.slne.surf.hud.core.layout.HudLayout
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.identity.Identity
import net.kyori.adventure.text.Component
import net.kyori.adventure.translation.GlobalTranslator
import java.util.*

class HudImpl(
    override val viewer: UUID,
    private val service: AbstractSurfHud,
) : Hud {
    private val lock = Any()
    private val content = LinkedHashMap<String, HudElement>()
    private val spacer = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)
    private val bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS)

    @Volatile
    private var dirty = true
    private var shownTo: Audience? = null
    private var lastContent: Component? = null

    override var visible: Boolean = true
        set(value) {
            field = value
            dirty = true
        }

    override val elements: List<HudElement>
        get() = synchronized(lock) { content.values.toList() }

    override fun get(id: String): HudElement? = synchronized(lock) { content[id] }

    override fun add(element: HudElement) = modify {
        content[element.id] = element
    }

    override fun add(elements: Collection<HudElement>) = modify {
        elements.forEach { content[it.id] = it }
    }

    override fun content(id: String, content: Component): Boolean = synchronized(lock) {
        val element = this.content[id] ?: return false
        if (element.content != content) {
            this.content[id] = element.copy(content = content)
            dirty = true
        }
        true
    }

    override fun remove(id: String): HudElement? = synchronized(lock) {
        content.remove(id)?.also { dirty = true }
    }

    override fun remove(ids: Collection<String>) = synchronized(lock) {
        if (content.keys.removeAll(ids.toSet())) {
            dirty = true
        }
    }

    override fun clear() = modify {
        content.clear()
    }

    override fun refresh() {
        dirty = true
        service.flush(this)
    }

    fun invalidate() {
        synchronized(lock) {
            shownTo = null
            lastContent = null
            dirty = true
        }
    }

    fun flush(audience: Audience, theme: HudTheme) {
        if (!dirty) {
            return
        }

        synchronized(lock) {
            dirty = false

            if (!visible || content.isEmpty()) {
                hide()
                return
            }

            val locale = audience.get(Identity.LOCALE).orElse(Locale.US)
            val composed = HudLayout.compose(theme, content.values) { GlobalTranslator.render(it, locale) }

            if (lastContent != composed) {
                lastContent = composed
                bar.name(composed)
            }

            if (shownTo !== audience) {
                shownTo?.let(::hideFrom)
                audience.showBossBar(spacer)
                audience.showBossBar(bar)
                shownTo = audience
            }
        }
    }

    fun hide() = synchronized(lock) {
        shownTo?.let(::hideFrom)
        shownTo = null
    }

    private fun hideFrom(audience: Audience) {
        audience.hideBossBar(spacer)
        audience.hideBossBar(bar)
    }

    private inline fun modify(block: () -> Unit) {
        synchronized(lock) {
            block()
            dirty = true
        }
    }
}
