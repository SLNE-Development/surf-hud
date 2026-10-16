package dev.slne.surf.hud.buildlogic.minecraft

import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider

/**
 * Configures the vanilla dev client started by `runDevClient` and the dev server it joins. The server values
 * (port, RCON, operators, properties, EULA) are applied by a server plugin such as `surf-hud.paper-dev`.
 */
abstract class MinecraftDevExtension {
    /** The Minecraft version to run, as listed in Mojang's version manifest (for example `26.3`). */
    abstract val version: Property<String>

    /** Launcher-style cache for jars, libraries and assets, shared across projects. */
    abstract val cacheDirectory: DirectoryProperty

    /** Holds `client/<username>/` (one game directory per dev user) and the dev server's directory. */
    abstract val runDirectory: DirectoryProperty

    /** Offline username of the dev client; `-Pminecraft.username=Dev2` starts a second client. */
    abstract val username: Property<String>

    abstract val windowWidth: Property<Int>
    abstract val windowHeight: Property<Int>

    /** Whether the client joins the dev server on startup (`--quickPlayMultiplayer`). */
    abstract val joinDevServer: Property<Boolean>

    /** Resource pack directories copied into the client's `resourcepacks/` and enabled, keyed by pack name. */
    abstract val resourcePacks: MapProperty<String, Directory>

    /**
     * Resource pack directories linked (not copied) into the client's `resourcepacks/` and enabled, keyed by
     * pack name. The client reads the directory itself, so F3+T loads whatever is in it at that moment.
     */
    abstract val linkedResourcePacks: MapProperty<String, Directory>

    /** Extra `options.txt` entries written before every client launch; they override the dev defaults. */
    abstract val clientOptions: MapProperty<String, String>

    abstract val serverPort: Property<Int>
    abstract val rconPort: Property<Int>
    abstract val rconPassword: Property<String>

    /** Usernames that get operator level 4 on the dev server. */
    abstract val operators: ListProperty<String>

    /** Extra `server.properties` entries written before every server launch; they override the dev defaults. */
    abstract val serverProperties: MapProperty<String, String>

    /**
     * Must be set to `true` by the build author to accept the Minecraft EULA
     * (https://aka.ms/MinecraftEULA) for the dev server; the server refuses to start otherwise.
     */
    abstract val acceptEula: Property<Boolean>

    fun resourcePack(name: String, directory: Directory) {
        resourcePacks.put(name, directory)
    }

    /** Adds a generated pack; pass a task output so the client launch builds the pack first. */
    fun resourcePack(name: String, directory: Provider<Directory>) {
        resourcePacks.put(name, directory)
    }

    /** Links [directory] into the client as pack [name]; the directory may be filled later, e.g. by a server. */
    fun linkResourcePack(name: String, directory: Provider<Directory>) {
        linkedResourcePacks.put(name, directory)
    }
}
