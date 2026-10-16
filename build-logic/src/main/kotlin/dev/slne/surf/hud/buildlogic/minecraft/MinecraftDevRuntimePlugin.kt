package dev.slne.surf.hud.buildlogic.minecraft

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Sync
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register

/**
 * Adds a vanilla Minecraft dev environment to a project: `runDevClient` (offline client with the configured
 * resource packs enabled), `runDevServer` (offline-mode dedicated server with RCON) and `devRcon`.
 */
class MinecraftDevRuntimePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.pluginManager.apply("jvm-toolchains")
        val providers = project.providers
        val extension = project.extensions.create<MinecraftDevExtension>("minecraftDev").apply {
            version.convention("26.3")
            cacheDirectory.convention(
                project.layout.dir(providers.provider { project.gradle.gradleUserHomeDir.resolve("caches/surf-hud-minecraft") })
            )
            runDirectory.convention(project.layout.projectDirectory.dir("run"))
            username.convention(providers.gradleProperty("minecraft.username").orElse("Dev"))
            windowWidth.convention(1280)
            windowHeight.convention(720)
            joinDevServer.convention(providers.gradleProperty("minecraft.joinServer").map(String::toBoolean).orElse(true))
            // Off the default 25565/25575 so the dev server does not clash with other local servers.
            serverPort.convention(providers.gradleProperty("minecraft.serverPort").map(String::toInt).orElse(25590))
            rconPort.convention(providers.gradleProperty("minecraft.rconPort").map(String::toInt).orElse(25591))
            rconPassword.convention("surf-dev")
            operators.convention(listOf("Dev", "Dev2"))
            acceptEula.convention(false)
        }

        // Java 25 is what 26.3 ships with; versions that need another Java override the toolchain on the tasks.
        val javaLauncher = project.extensions.getByType<JavaToolchainService>().launcherFor {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        val clientGameDir = extension.runDirectory.zip(extension.username) { run, name -> run.dir("client/$name") }
        val serverDir = extension.runDirectory.map { it.dir("server") }

        val prepareClient = project.tasks.register<PrepareMinecraftClient>("prepareDevClient") {
            group = GROUP
            description = "Downloads the Minecraft client, libraries and assets"
            version.set(extension.version)
            cacheDirectory.set(extension.cacheDirectory)
        }
        val prepareServer = project.tasks.register<PrepareMinecraftServer>("prepareDevServer") {
            group = GROUP
            description = "Downloads the Minecraft dedicated server"
            version.set(extension.version)
            cacheDirectory.set(extension.cacheDirectory)
        }

        val syncPacks = project.tasks.register<Sync>("syncDevResourcePacks") {
            group = GROUP
            description = "Copies the configured resource packs into the dev client's resourcepacks folder"
            into(clientGameDir.map { it.dir("resourcepacks") })
            // getting() keeps the producing task of generated packs as a dependency.
            extension.resourcePacks.keySet().get().forEach { name ->
                from(extension.resourcePacks.getting(name)) { into(name) }
            }
            // Packs dropped into the folder by hand are kept; only the configured packs are replaced.
            preserve {
                include("**")
                extension.resourcePacks.get().keys.forEach { exclude("$it/**") }
            }
        }

        project.tasks.register<RunMinecraftClient>("runDevClient") {
            group = GROUP
            description = "Starts the vanilla Minecraft client in offline mode with the dev resource packs enabled"
            dependsOn(prepareClient, syncPacks)
            version.set(extension.version)
            cacheDirectory.set(extension.cacheDirectory)
            gameDirectory.set(clientGameDir)
            username.set(extension.username)
            windowWidth.set(extension.windowWidth)
            windowHeight.set(extension.windowHeight)
            quickPlayServer.set(
                extension.joinDevServer.zip(extension.serverPort) { join, port -> if (join) "localhost:$port" else "" }
                    .filter { it.isNotEmpty() }
            )
            // Dev clients start muted; minecraftDev.clientOptions can override this.
            options.put("soundCategory_master", "0.0")
            options.putAll(extension.clientOptions)
            // -Pminecraft.enableResourcePacks=false starts with only the vanilla pack, e.g. for an FPS baseline.
            val enablePacks = providers.gradleProperty("minecraft.enableResourcePacks").map(String::toBoolean).orElse(true)
            options.put(
                "resourcePacks",
                extension.resourcePacks.zip(enablePacks) { packs, enabled ->
                    DevFiles.resourcePackOption(if (enabled) packs.keys else emptySet())
                }
            )
            this.javaLauncher.set(javaLauncher)
        }

        project.tasks.register<RunMinecraftServer>("runDevServer") {
            group = GROUP
            description = "Starts an offline-mode vanilla dedicated server with RCON for the dev client"
            dependsOn(prepareServer)
            version.set(extension.version)
            cacheDirectory.set(extension.cacheDirectory)
            serverDirectory.set(serverDir)
            properties.putAll(
                project.provider {
                    mapOf(
                        "online-mode" to "false",
                        "enforce-secure-profile" to "false",
                        "server-port" to extension.serverPort.get().toString(),
                        "enable-rcon" to "true",
                        "rcon.port" to extension.rconPort.get().toString(),
                        "rcon.password" to extension.rconPassword.get(),
                        // devRcon and demo tools send many commands; do not echo them into every op's chat.
                        "broadcast-rcon-to-ops" to "false",
                    )
                }
            )
            properties.putAll(extension.serverProperties)
            operators.set(extension.operators)
            acceptEula.set(extension.acceptEula)
            this.javaLauncher.set(javaLauncher)
        }

        project.tasks.register<SendRconCommand>("devRcon") {
            group = GROUP
            description = "Sends a console command to the running dev server: devRcon --command=\"list\""
            port.set(extension.rconPort)
            password.set(extension.rconPassword)
        }
    }

    private companion object {
        const val GROUP = "minecraft dev"
    }
}
