plugins {
    id("dev.slne.surf.api.gradle.paper-plugin")
}

surfPaperPluginApi {
    mainClass("dev.slne.surf.hud.paper.PaperMain")
    generateLibraryLoader(false)
    foliaSupported(true)

    authors.add("red")
}

dependencies {
    api(projects.surfHudCore)
}
