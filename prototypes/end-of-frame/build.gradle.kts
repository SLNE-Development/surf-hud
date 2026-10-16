plugins {
    id("surf-hud.minecraft-dev-runtime")
}

minecraftDev {
    version = "26.3"
    resourcePack("surf-hud-eof-test", layout.projectDirectory.dir("surf-hud-eof-test"))
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
            "motd" to "surf-hud end_of_frame prototype",
        )
    )
    // Dev-only local server; see https://aka.ms/MinecraftEULA.
    acceptEula = true
}
