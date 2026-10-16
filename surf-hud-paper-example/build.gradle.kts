import dev.slne.surf.api.gradle.util.registerRequired

plugins {
    id("dev.slne.surf.api.gradle.paper-plugin")
    // Dev loop: runServer (Paper with surf-hud-paper and this plugin), runDevClient, devRcon. See docs/development.md.
    id("surf-hud.paper-dev")
}

minecraftDev {
    // Dev-only local server; see https://aka.ms/MinecraftEULA.
    acceptEula = true
    serverProperties.putAll(mapOf("gamemode" to "creative", "spawn-protection" to "0", "motd" to "surf-hud example"))
}

surfPaperPluginApi {
    mainClass("dev.slne.surf.hud.example.paper.PaperExampleMain")
    generateLibraryLoader(false)
    foliaSupported(true)

    authors.add("red")

    serverDependencies {
        registerRequired("surf-hud-paper")
    }
}

dependencies {
    compileOnly(projects.surfHudApi)
}
