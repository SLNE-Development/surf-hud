package dev.slne.surf.hud.buildlogic.minecraft

import org.gradle.api.DefaultTask
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.options.Option
import org.gradle.jvm.toolchain.JavaLauncher
import org.gradle.process.ExecOperations
import java.nio.file.Files
import javax.inject.Inject

/** Properties shared by every task that works on the cached Minecraft installation. */
abstract class MinecraftInstallationTask : DefaultTask() {
    @get:Input
    abstract val version: Property<String>

    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    /** The cached installation of [version]; a plain function, so Gradle does not treat it as a task input. */
    protected fun installation() = MinecraftInstallation(cacheDirectory.get().asFile.toPath(), version.get())
}

/** Downloads the client jar, its libraries and its assets into the cache. */
@UntrackedTask(because = "Checks the shared download cache itself and skips files that are present")
abstract class PrepareMinecraftClient : MinecraftInstallationTask() {
    @TaskAction
    fun prepare() = installation().prepareClient(Downloader(logger), LauncherPlatform.current())
}

/** Starts the vanilla client in offline mode with its own game directory, optionally joining the dev server. */
@UntrackedTask(because = "Runs the game")
abstract class RunMinecraftClient @Inject constructor(
    private val exec: ExecOperations,
) : MinecraftInstallationTask() {
    @get:Internal
    abstract val gameDirectory: DirectoryProperty

    @get:Input
    abstract val username: Property<String>

    @get:Input
    abstract val windowWidth: Property<Int>

    @get:Input
    abstract val windowHeight: Property<Int>

    /** `host:port` to join on startup; absent means the title screen. */
    @get:Input
    @get:Optional
    abstract val quickPlayServer: Property<String>

    @get:Input
    abstract val options: MapProperty<String, String>

    /** Pack directories linked into `resourcepacks/<name>` before launch; see [DevFiles.linkDirectory]. */
    @get:Internal
    abstract val linkedResourcePacks: MapProperty<String, Directory>

    @get:Nested
    abstract val javaLauncher: Property<JavaLauncher>

    @TaskAction
    fun run() {
        val installation = installation()
        val meta = installation.read()
        val platform = LauncherPlatform.current()
        val gameDir = gameDirectory.get().asFile.toPath()
        val nativesDir = gameDir.resolve("natives")
        Files.createDirectories(nativesDir)
        DevFiles.mergeKeyValueFile(gameDir.resolve("options.txt"), ':', options.get())
        linkedResourcePacks.get().forEach { (name, directory) ->
            val target = directory.asFile.toPath()
            DevFiles.linkDirectory(gameDir, gameDir.resolve("resourcepacks").resolve(name), target)
            if (!Files.isRegularFile(target.resolve("pack.mcmeta"))) {
                logger.warn(
                    "Resource pack '$name' is still empty ($target). Start the server that generates it first, " +
                        "otherwise the client drops the pack; restart the client once the pack exists."
                )
            }
        }

        val features = buildSet {
            add("has_custom_resolution")
            if (quickPlayServer.isPresent) add("is_quick_play_multiplayer")
        }
        val name = username.get()
        val placeholders = mapOf(
            "auth_player_name" to name,
            "auth_uuid" to DevFiles.offlineUuid(name).toString().replace("-", ""),
            "auth_access_token" to "0",
            "auth_xuid" to "0",
            "clientid" to "0",
            "user_type" to "legacy",
            "version_name" to meta.id,
            "version_type" to "release",
            "game_directory" to gameDir.toString(),
            "assets_root" to installation.assetsDir.toString(),
            "assets_index_name" to meta.assetIndexId,
            "natives_directory" to nativesDir.toString(),
            "launcher_name" to "surf-hud-dev",
            "launcher_version" to "1",
            "resolution_width" to windowWidth.get().toString(),
            "resolution_height" to windowHeight.get().toString(),
            "quickPlayMultiplayer" to quickPlayServer.getOrElse(""),
        )
        // The classpath is passed by javaexec itself, so the launcher's "-cp ${classpath}" pair is dropped.
        val jvmArguments = meta.jvmArguments(platform, features)
            .filter { it != "-cp" && it != "\${classpath}" }
            .substitutePlaceholders(placeholders)
        val gameArguments = meta.gameArguments(platform, features).substitutePlaceholders(placeholders)

        logger.lifecycle("Starting Minecraft ${meta.id} as $name in $gameDir")
        exec.javaexec {
            executable = javaLauncher.get().executablePath.asFile.absolutePath
            classpath(installation.clientClasspath(meta, platform).map { it.toFile() })
            mainClass.set(meta.mainClass)
            jvmArgs(jvmArguments)
            args(gameArguments)
            workingDir = gameDir.toFile()
        }
    }
}

/** Sends one console command to the running dev server over RCON, for example `--command="list"`. */
@UntrackedTask(because = "Talks to a running server")
abstract class SendRconCommand : DefaultTask() {
    @get:Input
    @get:Option(option = "command", description = "The command to run, without a leading slash")
    abstract val command: Property<String>

    @get:Input
    abstract val port: Property<Int>

    @get:Input
    abstract val password: Property<String>

    @TaskAction
    fun send() {
        RconClient("localhost", port.get(), password.get()).use { rcon ->
            val reply = rcon.command(command.get().removePrefix("/"))
            logger.quiet(reply.ifEmpty { "(no reply)" })
        }
    }
}
