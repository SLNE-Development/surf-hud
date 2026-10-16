package dev.slne.surf.hud.example.paper

import dev.slne.surf.hud.api.SurfHud
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin

val plugin get() = JavaPlugin.getPlugin(PaperExampleMain::class.java)

class PaperExampleMain : JavaPlugin() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var vote: VoteLoop? = null

    override fun onEnable() {
        ExamplePlaceholders.register()
        ExampleInstaller.install()
        scope.launch {
            val result = SurfHud.reload()
            if (!result.success) {
                logger.warning("Demo HUD did not load: ${result.errors}")
                return@launch
            }
            vote = VoteLoop(scope, "Notch", "jeb_").also { it.start() }
        }
    }

    override fun onDisable() {
        vote?.stop()
        scope.cancel()
    }
}
