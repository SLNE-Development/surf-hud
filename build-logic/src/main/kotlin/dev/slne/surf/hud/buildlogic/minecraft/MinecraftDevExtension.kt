package dev.slne.surf.hud.buildlogic.minecraft

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property

/** Configures the vanilla dev client and server started by `runDevClient` and `runDevServer`. */
abstract class MinecraftDevExtension {
    /** The Minecraft version to run, as listed in Mojang's version manifest (for example `26.3`). */
    abstract val version: Property<String>

    /** Launcher-style cache for jars, libraries and assets, shared across projects. */
    abstract val cacheDirectory: DirectoryProperty

    /** Holds `client/<username>/` (one game directory per dev user) and `server/`. */
    abstract val runDirectory: DirectoryProperty

    /** Offline username of the dev client; `-Pminecraft.username=Dev2` starts a second client. */
    abstract val username: Property<String>

    abstract val windowWidth: Property<Int>
    abstract val windowHeight: Property<Int>

    /** Whether the client joins the dev server on startup (`--quickPlayMultiplayer`). */
    abstract val joinDevServer: Property<Boolean>

    /** Resource pack directories copied into the client's `resourcepacks/` and enabled, keyed by pack name. */
    abstract val resourcePacks: MapProperty<String, Directory>

    /** Extra `options.txt` entries written before every client launch. */
    abstract val clientOptions: MapProperty<String, String>

    abstract val serverPort: Property<Int>
    abstract val rconPort: Property<Int>
    abstract val rconPassword: Property<String>

    /** Usernames that get operator level 4 on the dev server. */
    abstract val operators: ListProperty<String>

    /** Extra `server.properties` entries written before every server launch. */
    abstract val serverProperties: MapProperty<String, String>

    /**
     * Must be set to `true` by the build author to accept the Minecraft EULA
     * (https://aka.ms/MinecraftEULA) for the dev server; the server refuses to start otherwise.
     */
    abstract val acceptEula: Property<Boolean>

    fun resourcePack(name: String, directory: Directory) {
        resourcePacks.put(name, directory)
    }
}
