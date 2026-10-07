package dev.slne.surf.hud.example.paper

import dev.slne.surf.api.paper.event.register
import dev.slne.surf.hud.example.paper.command.hudExampleCommand
import dev.slne.surf.hud.example.paper.hud.ExampleHud
import dev.slne.surf.hud.example.paper.listener.ExampleHudListener
import io.papermc.paper.threadedregions.scheduler.ScheduledTask
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.TimeUnit

val plugin get() = JavaPlugin.getPlugin(PaperExampleMain::class.java)

class PaperExampleMain : JavaPlugin() {
    private var updateTask: ScheduledTask? = null

    override fun onEnable() {
        ExampleHudListener.register()
        hudExampleCommand()

        Bukkit.getOnlinePlayers().forEach(ExampleHud::apply)

        updateTask = Bukkit.getAsyncScheduler().runAtFixedRate(
            this, { ExampleHud.update() }, 500, 500,
            TimeUnit.MILLISECONDS
        )
    }

    override fun onDisable() {
        updateTask?.cancel()
        updateTask = null
    }
}
