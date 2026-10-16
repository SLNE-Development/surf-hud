package dev.slne.surf.hud.core

import dev.slne.surf.hud.core.config.definition.DefinitionReader
import dev.slne.surf.hud.core.pack.HttpGet
import dev.slne.surf.hud.core.placeholder.HudClock
import dev.slne.surf.hud.core.platform.HudPlatform
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import java.nio.file.Path
import java.util.*

class RecordingAudience : Audience {
    val bars = mutableListOf<BossBar>()
    var shows = 0
    var hides = 0
    override fun showBossBar(bar: BossBar) {
        bars += bar; shows++
    }

    override fun hideBossBar(bar: BossBar) {
        bars -= bar; hides++
    }
}

class FakePlatform(override val dataFolder: Path) : HudPlatform {
    val audiences = HashMap<UUID, RecordingAudience>()
    var online = true

    /** `false` simulates a scheduler that accepts tasks but drops them (e.g. the entity was removed). */
    var runTasks = true
    override fun viewer(uuid: UUID) = audiences[uuid]
    override fun execute(uuid: UUID, task: () -> Unit): Boolean {
        if (!online) return false
        if (runTasks) task()
        return true
    }
}

fun testEngine(
    platform: FakePlatform,
    clock: HudClock,
    reader: DefinitionReader = ConfigurateTestReader,
    assets: (Path, String) -> dev.slne.surf.hud.core.pack.VanillaAssets = { _, _ -> TestAssets.vanilla() },
) = HudEngine(
    platform,
    reader,
    assets,
    emptyList(),
    HttpGet { error("no network in tests") },
    clock
)
