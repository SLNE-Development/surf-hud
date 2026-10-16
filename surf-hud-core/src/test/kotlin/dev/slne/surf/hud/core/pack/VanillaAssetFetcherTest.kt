package dev.slne.surf.hud.core.pack

import dev.slne.surf.hud.core.TestAssets
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.*

class VanillaAssetFetcherTest {
    @TempDir lateinit var cache: Path

    private val jar = TestAssets.clientJar()
    private fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun http(jarSha1: String = sha1(jar)): Pair<HttpGet, MutableList<String>> {
        val calls = mutableListOf<String>()
        val responses = mapOf(
            VanillaAssetFetcher.MANIFEST to """{"versions":[{"id":"26.3","url":"https://meta/26.3.json"}]}""".toByteArray(),
            "https://meta/26.3.json" to """{"downloads":{"client":{"url":"https://jar/client.jar","sha1":"$jarSha1"}}}""".toByteArray(),
            "https://jar/client.jar" to jar,
        )
        return HttpGet { url -> calls += url; responses[url] ?: error("404 $url") } to calls
    }

    @Test fun `downloads extracts and caches`() {
        val (http, calls) = http()
        val assets = VanillaAssetFetcher(http, cache).fetch("26.3")
        assertEquals(97, assets.packFormat)
        assertEquals(TestAssets.TEXT_VSH, assets.textVsh)
        assertEquals(16, assets.asciiRows.size)
        assertEquals('A', assets.asciiRows[4][1])
        assertEquals(8, assets.asciiHeight)
        assertEquals(3, calls.size)

        val offline = VanillaAssetFetcher({ error("offline") }, cache).fetch("26.3")
        assertEquals(TestAssets.TEXT_VSH, offline.textVsh)
    }

    @Test fun `rejects a jar with the wrong checksum`() {
        val (http, _) = http(jarSha1 = "0".repeat(40))
        val error = assertFailsWith<IllegalStateException> { VanillaAssetFetcher(http, cache).fetch("26.3") }
        assertContains(error.message!!, "SHA-1")
    }

    @Test fun `unknown version is a clear error`() {
        val (http, _) = http()
        val error = assertFailsWith<IllegalStateException> { VanillaAssetFetcher(http, cache).fetch("1.0") }
        assertContains(error.message!!, "1.0")
    }
}
