package dev.slne.surf.hud.buildlogic.minecraft

import groovy.json.JsonSlurper
import java.nio.file.Path

/** A file hosted by Mojang, with the local path it is stored at and the checksum it must match. */
data class RemoteFile(val url: String, val target: Path, val sha1: String?, val size: Long?)

/** The host platform in the terms Mojang's launcher rules use (`windows`, `osx`, `linux`). */
data class LauncherPlatform(val name: String, val arch: String, val version: String) {
    companion object {
        fun current(): LauncherPlatform {
            val osName = System.getProperty("os.name").lowercase()
            val name = when {
                osName.contains("win") -> "windows"
                osName.contains("mac") || osName.contains("darwin") -> "osx"
                else -> "linux"
            }
            val arch = when (val raw = System.getProperty("os.arch")) {
                "x86", "i386", "i686" -> "x86"
                else -> raw
            }
            return LauncherPlatform(name, arch, System.getProperty("os.version"))
        }
    }
}

/** Reads Mojang's launcher metadata: the version manifest, version JSONs and asset indexes. */
object MojangMeta {
    const val VERSION_MANIFEST_URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
    private const val RESOURCES_URL = "https://resources.download.minecraft.net"

    @Suppress("UNCHECKED_CAST")
    fun parse(path: Path): Map<String, Any?> = JsonSlurper().parse(path.toFile()) as Map<String, Any?>

    /** Returns the manifest entry (`id`, `url`, `sha1`) for [versionId], failing if Mojang does not list it. */
    fun versionEntry(manifest: Map<String, Any?>, versionId: String): Map<*, *> =
        (manifest["versions"] as List<*>).map { it as Map<*, *> }.firstOrNull { it["id"] == versionId }
            ?: error("Minecraft version '$versionId' is not listed in Mojang's version manifest")

    /** Lists every asset object of a parsed asset index as a download into [objectsDir]. */
    fun assetObjects(assetIndex: Map<String, Any?>, objectsDir: Path): List<RemoteFile> =
        (assetIndex["objects"] as Map<*, *>).values.map { it as Map<*, *> }
            .map { it["hash"] as String to (it["size"] as Number).toLong() }
            .distinctBy { it.first }
            .map { (hash, size) ->
                val prefix = hash.substring(0, 2)
                RemoteFile("$RESOURCES_URL/$prefix/$hash", objectsDir.resolve(prefix).resolve(hash), hash, size)
            }
}

/** A parsed version JSON (for example `26.3.json`) that describes how to download and launch that version. */
class VersionMeta(private val json: Map<String, Any?>) {
    val id: String get() = json["id"] as String
    val mainClass: String get() = json["mainClass"] as String
    val javaMajorVersion: Int get() = ((json["javaVersion"] as? Map<*, *>)?.get("majorVersion") as? Number)?.toInt() ?: 21

    private val assetIndexJson: Map<*, *> get() = json["assetIndex"] as Map<*, *>
    val assetIndexId: String get() = assetIndexJson["id"] as String

    fun assetIndex(target: Path): RemoteFile = assetIndexJson.toRemoteFile(target)

    /** The `client` or `server` jar download. */
    fun download(kind: String, target: Path): RemoteFile =
        ((json["downloads"] as Map<*, *>)[kind] as? Map<*, *>)?.toRemoteFile(target)
            ?: error("Version $id has no '$kind' download")

    /** The libraries the rules allow on [platform], stored in the maven layout under [librariesDir]. */
    fun libraries(platform: LauncherPlatform, librariesDir: Path): List<RemoteFile> =
        (json["libraries"] as List<*>).map { it as Map<*, *> }
            .filter { LauncherRules.allows(it["rules"] as List<*>?, platform, emptySet()) }
            .mapNotNull { library ->
                val artifact = (library["downloads"] as? Map<*, *>)?.get("artifact") as? Map<*, *> ?: return@mapNotNull null
                artifact.toRemoteFile(librariesDir.resolve(artifact["path"] as String))
            }
            .distinctBy { it.target }

    /** JVM arguments with `${...}` placeholders: the launcher's default heap and GC flags, then the version's own flags. */
    fun jvmArguments(platform: LauncherPlatform, features: Set<String>): List<String> =
        arguments("default-user-jvm", platform, features) + arguments("jvm", platform, features)

    /** Game arguments with `${...}` placeholders. */
    fun gameArguments(platform: LauncherPlatform, features: Set<String>): List<String> =
        arguments("game", platform, features)

    private fun arguments(kind: String, platform: LauncherPlatform, features: Set<String>): List<String> {
        val entries = (json["arguments"] as? Map<*, *>)?.get(kind) as? List<*> ?: return emptyList()
        return entries.flatMap { entry ->
            when (entry) {
                is String -> listOf(entry)
                is Map<*, *> -> if (LauncherRules.allows(entry["rules"] as List<*>?, platform, features)) {
                    when (val value = entry["value"]) {
                        is String -> listOf(value)
                        is List<*> -> value.map { it as String }
                        else -> emptyList()
                    }
                } else {
                    emptyList()
                }

                else -> emptyList()
            }
        }
    }

    private fun Map<*, *>.toRemoteFile(target: Path) =
        RemoteFile(this["url"] as String, target, this["sha1"] as String?, (this["size"] as Number?)?.toLong())
}

/** Evaluates the `rules` arrays of Mojang's version JSON the way the official launcher does. */
object LauncherRules {
    /**
     * Returns whether an entry with [rules] applies. Without rules an entry always applies; otherwise the
     * last matching rule decides and nothing matching means the entry is left out.
     */
    fun allows(rules: List<*>?, platform: LauncherPlatform, features: Set<String>): Boolean {
        if (rules.isNullOrEmpty()) return true
        var allowed = false
        for (rule in rules.map { it as Map<*, *> }) {
            if (matches(rule, platform, features)) allowed = rule["action"] == "allow"
        }
        return allowed
    }

    private fun matches(rule: Map<*, *>, platform: LauncherPlatform, features: Set<String>): Boolean {
        (rule["os"] as? Map<*, *>)?.let { os ->
            (os["name"] as String?)?.let { if (it != platform.name) return false }
            (os["arch"] as String?)?.let { if (it != platform.arch) return false }
            (os["versionRange"] as? Map<*, *>)?.let { range ->
                (range["min"] as String?)?.let { if (compareVersions(platform.version, it) < 0) return false }
                (range["max"] as String?)?.let { if (compareVersions(platform.version, it) >= 0) return false }
            }
        }
        (rule["features"] as? Map<*, *>)?.let { required ->
            if (required.any { (name, value) -> (name in features) != value }) return false
        }
        return true
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val cmp = a.getOrElse(i) { 0 }.compareTo(b.getOrElse(i) { 0 })
            if (cmp != 0) return cmp
        }
        return 0
    }
}

private val PLACEHOLDER = Regex("""\$\{([A-Za-z_]+)}""")

/** Replaces `${name}` placeholders in launcher arguments, failing on any placeholder without a value. */
fun List<String>.substitutePlaceholders(values: Map<String, String>): List<String> = map { argument ->
    PLACEHOLDER.replace(argument) { match ->
        values[match.groupValues[1]] ?: error("No value for launcher placeholder ${match.value} in '$argument'")
    }
}
