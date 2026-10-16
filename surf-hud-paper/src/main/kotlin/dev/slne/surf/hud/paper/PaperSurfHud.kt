package dev.slne.surf.hud.paper

import com.google.auto.service.AutoService
import dev.slne.surf.hud.api.SurfHud
import dev.slne.surf.hud.core.HudEngine
import dev.slne.surf.hud.core.config.definition.SurfConfigDefinitionReader
import dev.slne.surf.hud.core.head.MojangSkinSource
import dev.slne.surf.hud.core.pack.JdkHttpGet
import dev.slne.surf.hud.core.pack.VanillaAssetFetcher
import net.kyori.adventure.util.Services

private val http = JdkHttpGet()

@AutoService(SurfHud::class)
class PaperSurfHud : HudEngine(
    platform = PaperHudPlatform,
    reader = SurfConfigDefinitionReader(),
    assetProvider = { cache, version -> VanillaAssetFetcher(http, cache).fetch(version) },
    skinSources = listOf(PaperSkinSource, MojangSkinSource(http)),
    http = http,
), Services.Fallback {
    companion object {
        val INSTANCE: PaperSurfHud get() = SurfHud.instance as PaperSurfHud
    }
}
