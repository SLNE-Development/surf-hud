package dev.slne.surf.hud.core.pack

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Blocking HTTP GET returning the body; throws on any non-200 status. */
fun interface HttpGet {
    fun get(url: String): ByteArray
}

class JdkHttpGet : HttpGet {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun get(url: String): ByteArray {
        val response = client.send(
            HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(60)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()} for $url" }
        return response.body()
    }
}
