package dev.slne.surf.hud.core.head

import dev.slne.surf.hud.core.placeholder.HudClock
import dev.slne.surf.hud.core.pack.HttpGet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HeadServiceTest {
    @TempDir lateinit var cache: Path
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var now = 0L
    private val httpCalls = java.util.concurrent.atomic.AtomicInteger()

    private fun skin(face: Int, hat: Int?, legacy: Boolean = false): ByteArray {
        val image = BufferedImage(64, if (legacy) 32 else 64, BufferedImage.TYPE_INT_ARGB)
        for (x in 8 until 16) for (y in 8 until 16) image.setRGB(x, y, face)
        if (hat != null) for (x in 40 until 48) for (y in 8 until 16) image.setRGB(x, y, hat)
        if (legacy) for (x in 32 until 64) for (y in 0 until 16) if (image.getRGB(x, y) == 0) image.setRGB(x, y, 0xFF000000.toInt())
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun service(skins: Map<String, ByteArray>, gate: CompletableDeferred<Unit>? = null): HeadService {
        val source = object : SkinSource {
            override suspend fun skinUrl(player: String): String? {
                gate?.await()
                return if (player in skins) "https://skin/$player" else null
            }
        }
        val http = HttpGet { url -> httpCalls.incrementAndGet(); skins.getValue(url.substringAfterLast('/')) }
        return HeadService(listOf(source), http, cache, 1.days, HudClock { now }, scope, HeadPixels.placeholder("fallback"))
    }

    @Test fun `hat is composited over the face`() = runBlocking {
        val head = service(mapOf("Notch" to skin(0xFF112233.toInt(), 0xFF445566.toInt()))).await("Notch", 5.seconds)
        assertEquals(0xFF445566.toInt(), head.argb[0][0])
    }

    @Test fun `legacy skins ignore a fully opaque overlay`() = runBlocking {
        val head = service(mapOf("Old" to skin(0xFF112233.toInt(), null, legacy = true))).await("Old", 5.seconds)
        assertEquals(0xFF112233.toInt(), head.argb[0][0])
    }

    @Test fun `loads are cached in memory and on disk`() = runBlocking<Unit> {
        val skins = mapOf("Notch" to skin(0xFF112233.toInt(), null))
        val first = service(skins)
        first.await("Notch", 5.seconds)
        assertNotNull(first.cached("notch"))
        assertTrue(first.version > 0)
        val second = service(emptyMap())
        assertNotNull(second.cached("Notch"))
    }

    @Test fun `unknown players and timeouts give the fallback`() = runBlocking {
        val service = service(emptyMap())
        assertSame(service.fallback, service.await("Nobody", 5.seconds))
        val gated = service(mapOf("Slow" to skin(0, null)), CompletableDeferred())
        assertSame(gated.fallback, gated.await("Slow", 50.milliseconds))
    }

    @Test fun `cache expires after the ttl`() = runBlocking {
        service(mapOf("Notch" to skin(0xFF112233.toInt(), null))).await("Notch", 5.seconds)
        now = 2.days.inWholeMilliseconds + System.currentTimeMillis()
        assertNull(service(emptyMap()).cached("Notch"))
    }

    @Test fun `request does not reload a cached head`() = runBlocking {
        val service = service(mapOf("Notch" to skin(0xFF112233.toInt(), null)))
        service.await("Notch", 5.seconds)
        val version = service.version
        service.request("Notch")
        service.request("Notch")
        Thread.sleep(200)
        assertEquals(1, httpCalls.get())
        assertEquals(version, service.version)
    }

    @Test fun `a malformed skin gives the fallback`() = runBlocking {
        val tiny = ByteArrayOutputStream().also { ImageIO.write(BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB), "png", it) }.toByteArray()
        val service = service(mapOf("Tiny" to tiny))
        assertSame(service.fallback, service.await("Tiny", 5.seconds))
    }
}
