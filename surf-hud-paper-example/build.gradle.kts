import dev.slne.surf.api.gradle.util.registerRequired

plugins {
    id("dev.slne.surf.api.gradle.paper-plugin")
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
