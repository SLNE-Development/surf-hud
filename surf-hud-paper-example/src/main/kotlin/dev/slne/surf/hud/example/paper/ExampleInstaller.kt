package dev.slne.surf.hud.example.paper

import org.bukkit.Bukkit
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/** Copies the demo definitions and textures into surf-hud's folder; existing files are left alone. */
object ExampleInstaller {
    private val FILES = listOf(
        "texts/demo.yml", "images/demo.yml", "layouts/demo.yml", "layouts/vote.yml", "huds/demo.yml", "popups/vote.yml",
        "assets/icons/heart.png", "assets/icons/coin.png", "assets/icons/clock.png", "assets/icons/star.png",
        "assets/icons/player.png", "assets/strips/panel.png", "assets/strips/bar_bg.png", "assets/strips/bar_fill.png",
        "assets/strips/bar_tint.png",
    )

    fun install(): Path {
        val target = Bukkit.getPluginManager().getPlugin("surf-hud-paper")!!.dataPath
        for (file in FILES) {
            val path = target.resolve(file)
            if (path.exists()) continue
            path.parent.createDirectories()
            ExampleInstaller::class.java.getResourceAsStream("/surf-hud/$file")!!.use { Files.copy(it, path) }
        }
        return target
    }
}
