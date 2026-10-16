import dev.slne.surf.hud.buildlogic.hud.GenerateHudPack

plugins {
    id("surf-hud.minecraft-dev-runtime")
    kotlin("jvm")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.google.code.gson:gson:2.13.1")
}

kotlin {
    jvmToolchain(25)
}

val generateHudPack = tasks.register<GenerateHudPack>("generateHudPack") {
    group = "minecraft dev"
    description = "Generates the core-shader HUD resource pack from hud-layout.json"
    dependsOn("prepareDevClient")
    layoutFile = layout.projectDirectory.file("hud-layout.json")
    sourceDirectory = layout.projectDirectory.dir("textures")
    version = minecraftDev.version
    cacheDirectory = minecraftDev.cacheDirectory
    packDirectory = layout.buildDirectory.dir("hud-pack/surf-hud-core-test")
    metadataFile = layout.buildDirectory.file("hud-pack/hud-metadata.json")
}

minecraftDev {
    version = "26.3"
    resourcePack("surf-hud-core-test", generateHudPack.flatMap { it.packDirectory })
    clientOptions.putAll(
        mapOf(
            // Keep rendering at full speed while the window is unfocused so tests and FPS readings are comparable.
            "pauseOnLostFocus" to "false",
            "onboardAccessibility" to "false",
            "skipMultiplayerWarning" to "true",
            "joinedFirstServer" to "true",
            "tutorialStep" to "none",
        )
    )
    serverProperties.putAll(
        mapOf(
            "gamemode" to "creative",
            "spawn-protection" to "0",
            "motd" to "surf-hud core shader prototype",
        )
    )
    // Dev-only local server; see https://aka.ms/MinecraftEULA.
    acceptEula = true
}

tasks.register<JavaExec>("runHudDemo") {
    group = "minecraft dev"
    description = "Pushes live per-player HUD data to the running dev server over RCON"
    dependsOn(generateHudPack)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "dev.slne.surf.hud.prototype.coreshader.HudDemoKt"
    args(
        generateHudPack.get().metadataFile.get().asFile.absolutePath,
        minecraftDev.rconPort.get().toString(),
        minecraftDev.rconPassword.get(),
        providers.gradleProperty("hud.players").getOrElse("Dev,Dev2"),
    )
}
