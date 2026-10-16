import dev.slne.surf.api.gradle.util.registerSoft

plugins {
    id("dev.slne.surf.api.gradle.paper-plugin")
}

repositories {
    maven("https://repo.extendedclip.com/releases/")
}

surfPaperPluginApi {
    mainClass("dev.slne.surf.hud.paper.PaperMain")
    generateLibraryLoader(false)
    foliaSupported(true)

    authors.add("red")

    serverDependencies {
        registerSoft("PlaceholderAPI")
        registerSoft("MiniPlaceholders")
    }
}

dependencies {
    api(projects.surfHudCore)
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:3.2.0")
}
