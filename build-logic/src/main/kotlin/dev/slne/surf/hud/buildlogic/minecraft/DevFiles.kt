package dev.slne.surf.hud.buildlogic.minecraft

import groovy.json.JsonOutput
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
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
}
