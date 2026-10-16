package dev.slne.surf.hud.core.pack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import kotlin.io.path.*

/**
 * Provides [VanillaAssets] for a Minecraft version. The client jar is downloaded once through Mojang's
 * piston-meta (verified by SHA-1); only the four needed files are kept in `cacheRoot/<version>/`.
 */
class VanillaAssetFetcher(private val http: HttpGet, private val cacheRoot: Path) {
    fun fetch(version: String): VanillaAssets {
        val dir = cacheRoot.resolve(version)
        if (FILES.values.any { !dir.resolve(it).exists() }) download(version, dir)
        return load(version, dir)
    }

    private fun download(version: String, dir: Path) {
        val manifest = json(http.get(MANIFEST))
        val entry = manifest.getAsJsonArray("versions").map { it.asJsonObject }.firstOrNull { it["id"].asString == version }
            ?: throw IllegalStateException("Minecraft version '$version' is not in the version manifest")
        val client = json(http.get(entry["url"].asString)).getAsJsonObject("downloads").getAsJsonObject("client")
        val jar = http.get(client["url"].asString)
        val expected = client["sha1"].asString
        val actual = MessageDigest.getInstance("SHA-1").digest(jar).joinToString("") { "%02x".format(it) }
        check(actual.equals(expected, ignoreCase = true)) { "Client jar of $version has SHA-1 $actual, expected $expected" }

        dir.createDirectories()
        ZipInputStream(ByteArrayInputStream(jar)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                FILES[entry.name]?.let { dir.resolve(it).writeBytes(zip.readBytes()) }
            }
        }
        FILES.forEach { (path, name) -> check(dir.resolve(name).exists()) { "Client jar of $version has no $path" } }
    }

    private fun load(version: String, dir: Path): VanillaAssets {
        val versionJson = json(dir.resolve("version.json").readBytes())
        val packVersion = versionJson.getAsJsonObject("pack_version")
        val packFormat = (packVersion["resource_major"] ?: packVersion["resource"]).asInt
        val font = json(dir.resolve("default.json").readBytes())
        val provider = font.getAsJsonArray("providers").map { it.asJsonObject }
            .first { it["file"]?.asString == "minecraft:font/ascii.png" }
        return VanillaAssets(
            versionId = version,
            packFormat = packFormat,
            textVsh = dir.resolve("text.vsh").readText(),
            asciiImage = ImageIO.read(dir.resolve("ascii.png").toFile()) ?: error("Cached ascii.png is not an image"),
            asciiRows = provider.getAsJsonArray("chars").map { it.asString },
            asciiHeight = provider["height"]?.asInt ?: 8,
        )
    }

    private fun json(bytes: ByteArray): JsonObject = JsonParser.parseString(bytes.decodeToString()).asJsonObject

    companion object {
        const val MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

        /** Jar entry → cache file name. */
        private val FILES = mapOf(
            "version.json" to "version.json",
            "assets/minecraft/shaders/core/text.vsh" to "text.vsh",
            "assets/minecraft/font/include/default.json" to "default.json",
            "assets/minecraft/textures/font/ascii.png" to "ascii.png",
        )
    }
}
