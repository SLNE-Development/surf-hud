import dev.slne.surf.api.gradle.util.registerRequired
import dev.slne.surf.api.gradle.util.withSurfApiBukkit
import dev.slne.surf.hud.buildlogic.minecraft.DevFiles
import java.nio.file.Files

plugins {
    id("dev.slne.surf.api.gradle.paper-plugin")
    id("surf-hud.minecraft-dev-runtime")
}

/** The Paper dev server runs next to the vanilla dev clients (`run/client/<name>`). */
val paperRunDirectory = layout.projectDirectory.dir("run/paper")

minecraftDev {
    version = "26.3"
    // The pack surf-hud generates inside the Paper server; runDevClient copies it into the client first.
    resourcePack("surf-hud", paperRunDirectory.dir("plugins/surf-hud-paper/build/pack"))
    clientOptions.putAll(
        mapOf(
            "pauseOnLostFocus" to "false",
            "onboardAccessibility" to "false",
            "skipMultiplayerWarning" to "true",
            "joinedFirstServer" to "true",
            "tutorialStep" to "none",
        )
    )
    // Dev-only local server; see https://aka.ms/MinecraftEULA.
    acceptEula = true
}

surfPaperPluginApi {
    mainClass("dev.slne.surf.hud.example.paper.PaperExampleMain")
    generateLibraryLoader(false)
    foliaSupported(true)

    authors.add("red")

    serverDependencies {
        registerRequired("surf-hud-paper")
    }

    runServer {
        withSurfApiBukkit()
        pluginJars(project(":surf-hud-paper").tasks.named("shadowJar"))
        runDirectory.set(paperRunDirectory)
        // Offline mode so the dev clients (Dev, Dev2) can join; RCON so scripts can send console commands.
        val properties = mapOf(
            "online-mode" to "false",
            "enforce-secure-profile" to "false",
            "server-port" to minecraftDev.serverPort.get().toString(),
            "enable-rcon" to "true",
            "rcon.port" to minecraftDev.rconPort.get().toString(),
            "rcon.password" to minecraftDev.rconPassword.get(),
            "broadcast-rcon-to-ops" to "false",
            "gamemode" to "creative",
            "spawn-protection" to "0",
            "motd" to "surf-hud example",
        )
        val operators = minecraftDev.operators.get()
        val acceptEula = minecraftDev.acceptEula.get()
        doFirst {
            check(acceptEula) { "Set minecraftDev.acceptEula = true to run the dev server" }
            val dir = paperRunDirectory.asFile.toPath()
            Files.createDirectories(dir)
            Files.writeString(dir.resolve("eula.txt"), "eula=true\n")
            DevFiles.mergeKeyValueFile(dir.resolve("server.properties"), '=', properties)
            DevFiles.writeOps(dir.resolve("ops.json"), operators)
        }
    }
}

dependencies {
    compileOnly(projects.surfHudApi)
}
