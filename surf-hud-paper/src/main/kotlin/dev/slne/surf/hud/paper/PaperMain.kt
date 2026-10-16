package dev.slne.surf.hud.paper

import dev.slne.surf.api.paper.event.register
import dev.slne.surf.hud.api.SurfHud
import dev.slne.surf.hud.paper.listener.HudConnectionListener
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

val plugin get() = JavaPlugin.getPlugin(PaperMain::class.java)

class PaperMain : JavaPlugin() {
    override fun onEnable() {
        DefaultFiles.install(dataPath)
        BuiltinPlaceholders.register(SurfHud.placeholders)
        HudConnectionListener.register()
        Bukkit.getOnlinePlayers().forEach(HudConnectionListener::attach)

        val engine = PaperSurfHud.INSTANCE
        engine.scope.launch {
            val result = engine.reload()
            if (!result.success) logger.warning("surf-hud started without HUDs; fix the errors above and run /hud reload")
        }
    }

    override fun onDisable() {
        PaperSurfHud.INSTANCE.shutdown()
    }
}
