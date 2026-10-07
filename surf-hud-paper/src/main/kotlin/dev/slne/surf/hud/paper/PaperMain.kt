package dev.slne.surf.hud.paper

import dev.slne.surf.api.paper.event.register
import dev.slne.surf.hud.paper.listener.HudConnectionListener
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.TimeUnit

val plugin get() = JavaPlugin.getPlugin(PaperMain::class.java)

class PaperMain : JavaPlugin() {
    private var tickTask: ScheduledTask? = null

    override fun onEnable() {
        HudConnectionListener.register()
        Bukkit.getOnlinePlayers().forEach { PaperSurfHud.INSTANCE.handleJoin(it.uniqueId) }

        tickTask = Bukkit.getAsyncScheduler().runAtFixedRate(
            this, { PaperSurfHud.INSTANCE.tick() }, 50, 50,
            TimeUnit.MILLISECONDS
        )
    }

    override fun onDisable() {
        tickTask?.cancel()
        tickTask = null

        Bukkit.getOnlinePlayers().forEach { PaperSurfHud.INSTANCE.remove(it.uniqueId) }
    }
}
