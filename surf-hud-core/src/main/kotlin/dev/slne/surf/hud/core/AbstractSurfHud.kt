package dev.slne.surf.hud.core

import dev.slne.surf.hud.api.Hud
import dev.slne.surf.hud.api.SurfHud
import dev.slne.surf.hud.api.theme.HudTheme
import dev.slne.surf.hud.core.platform.HudPlatform
import java.util.*
import java.util.concurrent.ConcurrentHashMap

abstract class AbstractSurfHud : SurfHud {
    protected abstract val platform: HudPlatform

    private val huds = ConcurrentHashMap<UUID, HudImpl>()

    @Volatile
    override var theme: HudTheme = HudTheme()
        set(value) {
            field = value
            huds.values.forEach(HudImpl::refresh)
        }

    override fun hud(viewer: UUID): Hud = huds.computeIfAbsent(viewer) { HudImpl(it, this) }
    override fun hudOrNull(viewer: UUID): Hud? = huds[viewer]

    override fun remove(viewer: UUID) {
        huds.remove(viewer)?.hide()
    }

    fun flush(hud: HudImpl) {
        val audience = platform.viewer(hud.viewer) ?: return
        hud.flush(audience, theme)
    }

    fun tick() {
        huds.values.forEach(::flush)
    }

    fun handleJoin(viewer: UUID) {
        huds[viewer]?.invalidate()
    }

    fun handleQuit(viewer: UUID) {
        huds.remove(viewer)
    }
}
