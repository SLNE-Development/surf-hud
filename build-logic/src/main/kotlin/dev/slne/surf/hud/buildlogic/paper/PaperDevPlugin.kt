package dev.slne.surf.hud.buildlogic.paper

import dev.slne.surf.api.gradle.util.withSurfApiBukkit
import dev.slne.surf.hud.buildlogic.minecraft.DevFiles
import dev.slne.surf.hud.buildlogic.minecraft.MinecraftDevExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import xyz.jpenilla.runpaper.task.RunServer
import java.nio.file.Files

/**
 * The surf-hud dev loop for a Paper plugin project: applies `surf-hud.minecraft-dev-runtime` and turns run-paper's
 * `runServer` (added by the surf paper plugin) into the server the dev clients join.
 *
 * - `runServer` loads this project's plugin jar, the surf-hud Paper plugin ([PaperDevExtension.hudPluginProject])
 *   and the surf API server plugins, in [PaperDevExtension.runDirectory]. Before every start it writes
 *   `eula.txt`, `server.properties` (offline mode, the port and RCON from `minecraftDev`, then
 *   `minecraftDev.serverProperties`) and `ops.json` (`minecraftDev.operators`).
 * - `runDevClient` joins that server and links the pack surf-hud generates in it, so after `/hud reload`
 *   pressing F3+T in the client is enough to load a changed pack.
 *
 * The run-paper classes come from the build's own buildscript classpath (the surf gradle plugin), so this plugin
 * only touches them once `xyz.jpenilla.run-paper` is applied.
 */
class PaperDevPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("surf-hud.minecraft-dev-runtime")
        val minecraftDev = project.extensions.getByType<MinecraftDevExtension>()
        val extension = project.extensions.create<PaperDevExtension>("paperDev").apply {
            runDirectory.convention(minecraftDev.runDirectory.dir("paper"))
            hudPluginProject.convention(":surf-hud-paper")
            hudPackDirectory.convention(runDirectory.dir("plugins/surf-hud-paper/build/pack"))
            withSurfApi.convention(true)
        }
        minecraftDev.linkResourcePack("surf-hud", extension.hudPackDirectory)

        val serverProperties = project.provider {
            mapOf(
                // Offline mode so the dev clients (Dev, Dev2, ...) can join without accounts.
                "online-mode" to "false",
                "enforce-secure-profile" to "false",
                "server-port" to minecraftDev.serverPort.get().toString(),
                "enable-rcon" to "true",
                "rcon.port" to minecraftDev.rconPort.get().toString(),
                "rcon.password" to minecraftDev.rconPassword.get(),
                // devRcon and scripts send many commands; do not echo them into every op's chat.
                "broadcast-rcon-to-ops" to "false",
            ) + minecraftDev.serverProperties.get()
        }
        val operators = minecraftDev.operators
        val acceptEula = minecraftDev.acceptEula

        project.pluginManager.withPlugin("xyz.jpenilla.run-paper") {
            project.tasks.named<RunServer>("runServer") {
                group = "minecraft dev"
                runDirectory.convention(extension.runDirectory)
                if (extension.withSurfApi.get()) withSurfApiBukkit()
                val hudPlugin = extension.hudPluginProject.get()
                if (hudPlugin != project.path) {
                    pluginJars(project.project(hudPlugin).tasks.named("shadowJar"))
                }
                val serverDirectory = runDirectory
                doFirst {
                    check(acceptEula.get()) {
                        "The dev server needs the Minecraft EULA (https://aka.ms/MinecraftEULA) accepted: " +
                            "set minecraftDev.acceptEula = true"
                    }
                    val dir = serverDirectory.get().asFile.toPath()
                    Files.createDirectories(dir)
                    Files.writeString(dir.resolve("eula.txt"), "eula=true\n")
                    DevFiles.mergeKeyValueFile(dir.resolve("server.properties"), '=', serverProperties.get())
                    DevFiles.writeOps(dir.resolve("ops.json"), operators.get())
                }
            }
        }
    }
}
