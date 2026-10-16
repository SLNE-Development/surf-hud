package dev.slne.surf.hud.prototype.coreshader

import com.google.gson.JsonParser
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Base64
import javax.imageio.ImageIO

/** An 8x8 head face as ARGB pixels (`[row][column]`), with the hat layer composited on top. */
class PlayerHead(val name: String, val pixels: Array<IntArray>, val fromSkin: Boolean) {
    /** The same head with every color multiplied by [factor], e.g. to grey out the losing candidate. */
    fun dimmed(factor: Double) = PlayerHead(name, Array(8) { row ->
        IntArray(8) { column ->
            val argb = pixels[row][column]
            val r = (((argb shr 16) and 0xFF) * factor).toInt()
            val g = (((argb shr 8) and 0xFF) * factor).toInt()
            val b = ((argb and 0xFF) * factor).toInt()
            (argb and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
        }
    }, fromSkin)
}

/** Fetches player skins from Mojang (name → UUID → profile textures → skin PNG) and cuts out the face. */
object PlayerHeads {
    private val http = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    fun fetch(name: String): PlayerHead = runCatching { fromSkin(name, download(skinUrl(name))) }
        .getOrElse { error ->
            println("Could not fetch the skin of $name (${error.message}), using a placeholder head")
            placeholder(name)
        }

    private fun skinUrl(name: String): String {
        val profile = JsonParser.parseString(get("https://api.mojang.com/users/profiles/minecraft/$name").decodeToString()).asJsonObject
        val session = JsonParser.parseString(
            get("https://sessionserver.mojang.com/session/minecraft/profile/${profile["id"].asString}").decodeToString()
        ).asJsonObject
        val textures = session.getAsJsonArray("properties").map { it.asJsonObject }.first { it["name"].asString == "textures" }
        val decoded = JsonParser.parseString(Base64.getDecoder().decode(textures["value"].asString).decodeToString()).asJsonObject
        return decoded.getAsJsonObject("textures").getAsJsonObject("SKIN")["url"].asString
    }

    private fun download(url: String): BufferedImage =
        ImageIO.read(ByteArrayInputStream(get(url))) ?: error("Skin at $url is not an image")

    private fun get(url: String): ByteArray {
        val response = http.send(
            HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()} for $url" }
        return response.body()
    }

    /** Face at (8, 8) and hat at (40, 8) in both 64x64 and legacy 64x32 skins. */
    private fun fromSkin(name: String, skin: BufferedImage): PlayerHead {
        val scale = skin.width / 64
        fun pixel(x: Int, y: Int) = skin.getRGB(x * scale, y * scale)
        // Like vanilla's "Notch transparency hack": legacy 64x32 skins often fill the overlay area with opaque
        // pixels, and vanilla ignores that layer when it has no transparent pixel at all.
        val legacy = skin.height == skin.width / 2
        val overlayOpaque = (32 until 64).all { x -> (0 until 16).all { y -> (pixel(x, y) ushr 24) >= 128 } }
        val useHat = !(legacy && overlayOpaque)
        val pixels = Array(8) { row ->
            IntArray(8) { column ->
                val hat = pixel(40 + column, 8 + row)
                if (useHat && (hat ushr 24) >= 128) hat or 0xFF000000.toInt() else pixel(8 + column, 8 + row) or 0xFF000000.toInt()
            }
        }
        return PlayerHead(name, pixels, true)
    }

    /** A plain two-tone face so the demo still runs without network access. */
    private fun placeholder(name: String): PlayerHead {
        val hue = name.hashCode()
        val skin = 0xFFC8A07A.toInt()
        val hair = 0xFF000000.toInt() or (hue and 0x7F7F7F)
        val pixels = Array(8) { row ->
            IntArray(8) { column ->
                when {
                    row < 2 -> hair
                    row == 3 && (column == 1 || column == 6) -> 0xFF222244.toInt()
                    row == 6 && column in 2..5 -> 0xFF7A4A3A.toInt()
                    else -> skin
                }
            }
        }
        return PlayerHead(name, pixels, false)
    }
}
