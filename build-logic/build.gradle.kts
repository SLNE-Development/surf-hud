plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    maven("https://reposilite.slne.dev/releases")
}

dependencies {
    // compileOnly: at runtime both come from the root buildscript classpath (surf-api-gradle-plugin pulls in
    // run-task), so surf-hud.paper-dev sees the same RunServer class the surf paper plugin registers.
    compileOnly("dev.slne.surf.api:surf-api-gradle-plugin:2.3.0") { isTransitive = false }
    compileOnly("xyz.jpenilla:run-task:3.1.0") { isTransitive = false }
}

gradlePlugin {
    plugins {
        register("minecraftDevRuntime") {
            id = "surf-hud.minecraft-dev-runtime"
            implementationClass = "dev.slne.surf.hud.buildlogic.minecraft.MinecraftDevRuntimePlugin"
        }
        register("paperDev") {
            id = "surf-hud.paper-dev"
            implementationClass = "dev.slne.surf.hud.buildlogic.paper.PaperDevPlugin"
        }
    }
}
