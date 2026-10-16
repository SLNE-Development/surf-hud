plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

gradlePlugin {
    plugins {
        register("minecraftDevRuntime") {
            id = "surf-hud.minecraft-dev-runtime"
            implementationClass = "dev.slne.surf.hud.buildlogic.minecraft.MinecraftDevRuntimePlugin"
        }
    }
}
