buildscript {
    repositories {
        gradlePluginPortal()
        maven("https://reposilite.slne.dev/releases")
    }
    dependencies {
        classpath("dev.slne.surf.api:surf-api-gradle-plugin:+")
    }
}

allprojects {
    group = "dev.slne.surf.hud"
    version = findProperty("version") as String
}