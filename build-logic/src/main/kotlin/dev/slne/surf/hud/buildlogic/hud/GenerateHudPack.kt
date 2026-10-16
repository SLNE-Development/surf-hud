package dev.slne.surf.hud.buildlogic.hud

import dev.slne.surf.hud.buildlogic.minecraft.MinecraftInstallation
import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

/**
 * Generates the core-shader HUD resource pack for [version] from a layout file and its source textures.
 * The vanilla shader and font data come from that version's client jar, so moving to a new Minecraft
 * version only means changing [version] (and fixing [TextShaderPatcher] if an anchor moved).
 */
@CacheableTask
abstract class GenerateHudPack : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val layoutFile: RegularFileProperty

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sourceDirectory: DirectoryProperty

    @get:Input
    abstract val version: Property<String>

    /** Cache prepared by `prepareDevClient`; only the client jar is read. */
    @get:Internal
    abstract val cacheDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val packDirectory: DirectoryProperty

    /** Glyph characters, advances, fonts and slots a server needs to compose components for this pack. */
    @get:OutputFile
    abstract val metadataFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val installation = MinecraftInstallation(cacheDirectory.get().asFile.toPath(), version.get())
        val layout = HudLayout.read(layoutFile.get().asFile.toPath())
        val generator = HudPackGenerator(layout, sourceDirectory.get().asFile.toPath(), version.get(), installation::readClientEntry)
        val metadata = generator.generate(packDirectory.get().asFile.toPath())
        val metadataPath = metadataFile.get().asFile.toPath()
        metadataPath.parent.createDirectories()
        metadataPath.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)))
    }
}
