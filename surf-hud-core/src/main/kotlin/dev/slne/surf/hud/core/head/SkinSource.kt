package dev.slne.surf.hud.core.head

import com.google.gson.JsonParser
import dev.slne.surf.hud.core.pack.HttpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/** Finds the skin texture URL of a player given by name or UUID; `null` when this source does not know them. */
interface SkinSource {
    suspend fun skinUrl(player: String): String?
}

/** Mojang API: name → UUID (skipped for UUIDs) → session profile → textures. */
class MojangSkinSource(private val http: HttpGet) : SkinSource {
    override suspend fun skinUrl(player: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val id = runCatching { UUID.fromString(player).toString().replace("-", "") }.getOrNull()
                ?: JsonParser.parseString(http.get("https://api.mojang.com/users/profiles/minecraft/$player").decodeToString())
                    .asJsonObject["id"].asString
            val session = JsonParser.parseString(http.get("https://sessionserver.mojang.com/session/minecraft/profile/$id").decodeToString()).asJsonObject
            val textures = session.getAsJsonArray("properties").map { it.asJsonObject }.first { it["name"].asString == "textures" }
            val decoded = JsonParser.parseString(Base64.getDecoder().decode(textures["value"].asString).decodeToString()).asJsonObject
            decoded.getAsJsonObject("textures").getAsJsonObject("SKIN")["url"].asString
        }.getOrNull()
    }
}
