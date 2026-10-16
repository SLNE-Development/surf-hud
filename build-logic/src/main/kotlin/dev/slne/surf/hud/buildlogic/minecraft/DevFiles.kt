package dev.slne.surf.hud.buildlogic.minecraft

import groovy.json.JsonOutput
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.UUID

/** Helpers for the files a dev client or server reads at startup. */
object DevFiles {
    /** The UUID an offline-mode server assigns to [name]; the dev client sends the same one. */
    fun offlineUuid(name: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(StandardCharsets.UTF_8))

    /**
     * Sets [values] in a `key<separator>value` file (`options.txt` uses `:`, `server.properties` uses `=`).
     * Keys not in [values] and their order are kept, so settings changed in game survive the next launch.
     */
    fun mergeKeyValueFile(file: Path, separator: Char, values: Map<String, String>) {
        val lines = if (Files.isRegularFile(file)) Files.readAllLines(file).toMutableList() else mutableListOf()
        val remaining = values.toMutableMap()
        for (i in lines.indices) {
            val line = lines[i]
            if (line.startsWith("#")) continue
            val key = line.substringBefore(separator, missingDelimiterValue = "")
            remaining.remove(key)?.let { lines[i] = "$key$separator$it" }
        }
        remaining.forEach { (key, value) -> lines += "$key$separator$value" }
        Files.createDirectories(file.parent)
        Files.write(file, lines)
    }

    /** Writes `ops.json` that grants operator level 4 to every name in [names] by its offline UUID. */
    fun writeOps(file: Path, names: List<String>) {
        val ops = names.map { name ->
            mapOf("uuid" to offlineUuid(name).toString(), "name" to name, "level" to 4, "bypassesPlayerLimit" to false)
        }
        Files.createDirectories(file.parent)
        Files.writeString(file, JsonOutput.prettyPrint(JsonOutput.toJson(ops)))
    }

    /** Formats resource pack directory names as the `resourcePacks` value of `options.txt`. */
    fun resourcePackOption(packNames: Collection<String>): String =
        JsonOutput.toJson(listOf("vanilla") + packNames.map { "file/$it" })

    /**
     * Makes [link] point at the directory [target] (created if missing), replacing an older link there.
     * On Windows this is a directory junction, which needs no admin rights and which Java and the game see as
     * a plain directory. Elsewhere it is a symbolic link; the game only follows symbolic links whose target is
     * listed in `allowed_symlinks.txt` of [gameDir], so the target is added there.
     * A real directory at [link] is never deleted; that fails with a message instead.
     */
    fun linkDirectory(gameDir: Path, link: Path, target: Path) {
        val absoluteTarget = target.toAbsolutePath().normalize()
        Files.createDirectories(absoluteTarget)
        Files.createDirectories(link.parent)
        if (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
            check(isLink(link)) {
                "$link is a real directory, not a link. Delete it once so it can be linked to $absoluteTarget."
            }
            if (Files.exists(link) && link.toRealPath() == absoluteTarget.toRealPath()) return
            // Removes only the link (junction or symbolic link), never the directory it points at.
            Files.delete(link)
        }
        if (System.getProperty("os.name").startsWith("Windows")) {
            val process = ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), absoluteTarget.toString())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            check(process.waitFor() == 0) { "mklink /J $link $absoluteTarget failed: $output" }
        } else {
            Files.createSymbolicLink(link, absoluteTarget)
            val allowList = gameDir.resolve("allowed_symlinks.txt")
            val entry = "[prefix]$absoluteTarget"
            val lines = if (Files.isRegularFile(allowList)) Files.readAllLines(allowList) else emptyList()
            if (entry !in lines) Files.write(allowList, lines + entry)
        }
    }

    /** Whether [path] is a symbolic link or a Windows junction (which Java reports as "other", not as a link). */
    private fun isLink(path: Path): Boolean {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        return attributes.isSymbolicLink || attributes.isOther
    }
}
