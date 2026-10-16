package dev.slne.surf.hud.core.pack

import team.unnamed.creative.ResourcePack
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*

/**
 * Writes a pack as an unzipped folder and as a zip; returns the zip's SHA-1, also written next to it as `.sha1`.
 *
 * creative writes the folder, then `pack.mcmeta` is replaced by [write]'s `packMeta` (creative cannot write
 * `min_format`/`max_format`) and the zip is built from the folder, so both always agree.
 */
object PackWriter {
    /** Every entry gets this timestamp so identical content always produces identical zip bytes and SHA-1. */
    private val FIXED_TIME = LocalDateTime.of(2000, 1, 1, 0, 0)

    /** Marker file proving the output folder was written by surf-hud, so it may be deleted on the next write. */
    const val MARKER = ".surf-hud-pack"

    /**
     * Replaces [folder] with the pack, then rebuilds [zip] from it. Throws [IOException] instead of deleting an
     * existing [folder] that is a file, or that is not empty and lacks the [MARKER] of an earlier write.
     */
    fun write(pack: ResourcePack, packMeta: String, zip: Path, folder: Path): String {
        clear(folder)
        folder.createDirectories()
        MinecraftResourcePackWriter.minecraft().writeToDirectory(folder.toFile(), pack)
        folder.resolve("pack.mcmeta").writeText(packMeta)
        folder.resolve(MARKER).writeText("Written by surf-hud; this folder is deleted and rewritten on every pack write.\n")

        zip.parent?.createDirectories()
        val temp = zip.resolveSibling(zip.name + ".tmp")
        ZipOutputStream(Files.newOutputStream(temp)).use { out ->
            Files.walk(folder).use { paths ->
                paths.filter { it.isRegularFile() && folder.relativize(it).toString() != MARKER }.sorted().forEach { file ->
                    out.putNextEntry(ZipEntry(folder.relativize(file).joinToString("/")).apply { setTimeLocal(FIXED_TIME) })
                    Files.copy(file, out)
                    out.closeEntry()
                }
            }
        }
        Files.move(temp, zip, StandardCopyOption.REPLACE_EXISTING)

        val hash = MessageDigest.getInstance("SHA-1").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        zip.resolveSibling(zip.nameWithoutExtension + ".sha1").writeText(hash)
        return hash
    }

    /** Deletes [folder] if it exists and was written by surf-hud (or is empty); throws [IOException] otherwise. */
    private fun clear(folder: Path) {
        if (!folder.exists()) return
        if (!folder.isDirectory()) throw IOException("output folder $folder is a file; refusing to delete it")
        val empty = Files.list(folder).use { !it.findAny().isPresent }
        if (!empty && !folder.resolve(MARKER).exists()) {
            throw IOException(
                "output folder $folder is not empty and has no $MARKER file, so surf-hud did not write it; " +
                    "refusing to delete it (empty or move it, or choose another output.folder)"
            )
        }
        if (!folder.toFile().deleteRecursively()) throw IOException("could not delete the old output folder $folder")
    }
}
