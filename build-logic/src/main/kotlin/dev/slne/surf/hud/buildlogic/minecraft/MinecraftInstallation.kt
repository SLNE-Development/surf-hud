package dev.slne.surf.hud.buildlogic.minecraft

import java.nio.file.Files
import java.nio.file.Path

/**
 * The launcher-style cache of one Minecraft version: `versions/<id>/`, `libraries/` and `assets/`
 * below [cacheDir], shared by every project and run directory.
 */
class MinecraftInstallation(private val cacheDir: Path, val versionId: String) {
    private val versionDir: Path = cacheDir.resolve("versions").resolve(versionId)
    private val versionJson: Path = versionDir.resolve("$versionId.json")
    val librariesDir: Path = cacheDir.resolve("libraries")
    val assetsDir: Path = cacheDir.resolve("assets")
    val clientJar: Path = versionDir.resolve("client.jar")

    /** Reads the version JSON, fetching the manifest and the JSON first if this version was never resolved. */
    fun resolve(downloader: Downloader): VersionMeta {
        if (!Files.isRegularFile(versionJson)) {
            val manifest = cacheDir.resolve("version_manifest_v2.json")
            Files.deleteIfExists(manifest)
            downloader.fetch(RemoteFile(MojangMeta.VERSION_MANIFEST_URL, manifest, null, null))
            val entry = MojangMeta.versionEntry(MojangMeta.parse(manifest), versionId)
            downloader.fetch(RemoteFile(entry["url"] as String, versionJson, entry["sha1"] as String?, null))
        }
        return read()
    }

    /** Reads the version JSON that [resolve] stored; fails if the version was never resolved. */
    fun read(): VersionMeta {
        check(Files.isRegularFile(versionJson)) { "Minecraft $versionId is not prepared yet ($versionJson is missing)" }
        return VersionMeta(MojangMeta.parse(versionJson))
    }

    /** Downloads everything the client needs: the jar, the libraries for this platform, the asset index and objects. */
    fun prepareClient(downloader: Downloader, platform: LauncherPlatform) {
        val meta = resolve(downloader)
        downloader.fetch(meta.download("client", clientJar))
        downloader.fetchAll(meta.libraries(platform, librariesDir), "libraries")
        val indexFile = assetsDir.resolve("indexes").resolve("${meta.assetIndexId}.json")
        downloader.fetch(meta.assetIndex(indexFile))
        downloader.fetchAll(MojangMeta.assetObjects(MojangMeta.parse(indexFile), assetsDir.resolve("objects")), "assets")
    }

    /** The client classpath: every allowed library followed by the client jar. */
    fun clientClasspath(meta: VersionMeta, platform: LauncherPlatform): List<Path> =
        // listOf: Path is itself an Iterable<Path>, so "+ clientJar" would add the jar's path segments.
        meta.libraries(platform, librariesDir).map { it.target } + listOf(clientJar)
}
