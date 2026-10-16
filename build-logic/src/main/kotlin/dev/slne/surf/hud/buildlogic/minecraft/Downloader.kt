package dev.slne.surf.hud.buildlogic.minecraft

import org.gradle.api.GradleException
import org.gradle.api.logging.Logger
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Downloads [RemoteFile]s, verifies their SHA-1 and skips files that are already present with the expected size. */
class Downloader(private val logger: Logger) {
    private val http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    fun fetch(file: RemoteFile) {
        if (!isPresent(file)) download(file)
    }

    fun fetchAll(files: Collection<RemoteFile>, label: String, parallelism: Int = 16) {
        val missing = files.filterNot(::isPresent)
        if (missing.isEmpty()) return
        logger.lifecycle("Downloading ${missing.size} $label")
        val done = AtomicInteger()
        val executor = Executors.newFixedThreadPool(parallelism)
        try {
            missing.map { file ->
                executor.submit {
                    download(file)
                    val count = done.incrementAndGet()
                    if (count % 500 == 0) logger.lifecycle("  $count/${missing.size} $label")
                }
            }.forEach { future ->
                try {
                    future.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    private fun isPresent(file: RemoteFile): Boolean =
        Files.isRegularFile(file.target) && (file.size == null || Files.size(file.target) == file.size)

    private fun download(file: RemoteFile) {
        Files.createDirectories(file.target.parent)
        val temp = file.target.resolveSibling("${file.target.fileName}.part-${Thread.currentThread().threadId()}")
        var lastError: Exception? = null
        repeat(3) {
            try {
                val request = HttpRequest.newBuilder(URI(file.url)).timeout(Duration.ofMinutes(5)).GET().build()
                val response = http.send(request, HttpResponse.BodyHandlers.ofFile(temp))
                if (response.statusCode() != 200) throw GradleException("HTTP ${response.statusCode()} for ${file.url}")
                file.sha1?.let { expected ->
                    val actual = sha1(temp)
                    if (!actual.equals(expected, ignoreCase = true)) {
                        throw GradleException("SHA-1 mismatch for ${file.url}: expected $expected, got $actual")
                    }
                }
                Files.move(temp, file.target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                return
            } catch (e: Exception) {
                lastError = e
                Files.deleteIfExists(temp)
            }
        }
        throw GradleException("Could not download ${file.url}", lastError)
    }

    private fun sha1(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-1")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
