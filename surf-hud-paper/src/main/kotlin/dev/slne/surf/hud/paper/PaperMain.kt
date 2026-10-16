package dev.slne.surf.hud.paper

import com.github.shynixn.mccoroutine.folia.SuspendingJavaPlugin
import dev.slne.surf.api.paper.event.register
import dev.slne.surf.hud.api.SurfHud
import dev.slne.surf.hud.paper.bridge.MiniPlaceholdersBridge
import dev.slne.surf.hud.paper.bridge.PapiBridge
import dev.slne.surf.hud.paper.command.hudCommand
import dev.slne.surf.hud.paper.listener.HudConnectionListener
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

val plugin get() = JavaPlugin.getPlugin(PaperMain::class.java)

class PaperMain : SuspendingJavaPlugin() {
    override fun onEnable() {
        DefaultFiles.install(dataPath)
        BuiltinPlaceholders.register(SurfHud.placeholders)

        registerBridges()
        HudConnectionListener.register()
        hudCommand()
        Bukkit.getOnlinePlayers().forEach(HudConnectionListener::attach)

        val engine = PaperSurfHud.INSTANCE
        
        engine.scope.launch {
            val result = engine.reload()
            if (!result.success) logger.warning("surf-hud started without HUDs; fix the errors above and run /hud reload")
        }
    }

    private fun registerBridges() {
        val plugins = Bukkit.getPluginManager()

        if (plugins.isPluginEnabled("PlaceholderAPI")) PapiBridge.register(SurfHud.placeholders)
        if (plugins.isPluginEnabled("MiniPlaceholders")) MiniPlaceholdersBridge.register(SurfHud.placeholders)
    }

    override fun onDisable() {
        PaperSurfHud.INSTANCE.shutdown()
    }
}
