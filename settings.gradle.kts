pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://reposilite.slne.dev/releases")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("dev.slne.surf.api.gradle.settings") version "+"
}

include("surf-hud-api")
include("surf-hud-core")
include("surf-hud-paper")
include("surf-hud-minestom")
include("surf-hud-paper-example")
