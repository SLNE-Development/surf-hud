package dev.slne.surf.hud.core.head

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.hud.core.pack.HttpGet
import dev.slne.surf.hud.core.placeholder.HudClock
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.time.Duration

/**
 * Loads and caches player heads. Lookups hit memory, then `cacheDir/<player>.png` (fresh for [ttl]); loads
 * run on [scope], try each [SkinSource] in order and are not retried for a minute after a failure.
 * [version] increases with every loaded head, so renderers know when to redraw.
 */
class HeadService(
    private val sources: List<SkinSource>,
    private val http: HttpGet,
    private val cacheDir: Path,
    private val ttl: Duration,
    private val clock: HudClock,
    private val scope: CoroutineScope,
    val fallback: HeadPixels,
) {
    private val log = logger()
    private val memory = ConcurrentHashMap<String, HeadPixels>()
    private val loading = ConcurrentHashMap<String, Deferred<HeadPixels?>>()
    private val failedAt = ConcurrentHashMap<String, Long>()

    @Volatile
    var version = 0L
        private set

    fun cached(player: String): HeadPixels? {
        val key = key(player) ?: return null
        memory[key]?.let { return it }
        val file = cacheDir.resolve("$key.png")
        if (!file.exists() || file.getLastModifiedTime().toMillis() + ttl.inWholeMilliseconds <= clock.millis()) return null
        val image = runCatching { ImageIO.read(file.toFile()) }.getOrNull() ?: return null
        return toPixels(image).also { memory[key] = it }
    }

    /** Starts loading [player] unless it is loading or failed within the last minute. */
    fun request(player: String) {
        start(player)
    }

    /** The cached head, or the loaded one, or [fallback] after [timeout] or on failure. */
    suspend fun await(player: String, timeout: Duration): HeadPixels =
        cached(player) ?: withTimeoutOrNull(timeout) { start(player)?.await() } ?: fallback

    private fun start(player: String): Deferred<HeadPixels?>? {
        val key = key(player) ?: return null
        failedAt[key]?.let { if (clock.millis() - it < 60_000) return null }
        return loading.computeIfAbsent(key) {
            scope.async {
                try {
                    load(player, key).also { if (it == null) failedAt[key] = clock.millis() }
                } finally {
                    loading.remove(key)
                }
            }
        }
    }

    private suspend fun load(player: String, key: String): HeadPixels? {
        for (source in sources) {
            val url = runCatching { source.skinUrl(player) }.getOrNull() ?: continue
            val skin = runCatching { withContext(Dispatchers.IO) { ImageIO.read(ByteArrayInputStream(http.get(url))) } }
                .onFailure { log.atWarning().withCause(it).log("Could not download the skin of %s", player) }
                .getOrNull() ?: continue
            val head = SkinFace.extract(skin)
            memory[key] = head
            withContext(Dispatchers.IO) { store(key, head) }
            version++
            return head
        }
        return null
    }

    private fun store(key: String, head: HeadPixels) {
        runCatching {
            cacheDir.createDirectories()
            val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
            for (row in 0 until 8) for (column in 0 until 8) image.setRGB(column, row, head.argb[row][column])
            ImageIO.write(image, "png", cacheDir.resolve("$key.png").toFile())
        }.onFailure { log.atWarning().withCause(it).log("Could not cache head %s", key) }
    }

    private fun toPixels(image: BufferedImage) = HeadPixels(Array(8) { row -> IntArray(8) { column -> image.getRGB(column, row) } })

    /** Cache key: lower-case name or UUID; `null` for values that are neither (e.g. blank or `?`). */
    private fun key(player: String): String? =
        player.trim().lowercase().takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }
}
