package dev.slne.surf.hud.paper

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/** Installs the bundled minimal configuration on first start (when `huds/` does not exist yet). */
object DefaultFiles {
    private val FILES = listOf("texts/default.yml", "layouts/main.yml", "huds/main.yml")

    fun install(dataFolder: Path) {
        if (dataFolder.resolve("huds").exists()) return
        for (file in FILES) {
            val target = dataFolder.resolve(file)
            target.parent.createDirectories()
            DefaultFiles::class.java.getResourceAsStream("/defaults/$file")!!.use { Files.copy(it, target) }
        }
        dataFolder.resolve("assets").createDirectories()
    }
}
