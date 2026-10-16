package dev.slne.surf.hud.core.pack

import com.google.gson.JsonParser
import dev.slne.surf.hud.core.TestDefinitions
import dev.slne.surf.hud.core.model.HudEncoding
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.*

class PackGeneratorTest {
    @TempDir lateinit var root: Path

    private fun generate(): Pair<Path, String> {
        val model = TestDefinitions.compile(root)
        val folder = root.resolve("build/pack")
        val gen = PackGenerator(model)
        val hash = PackWriter.write(gen.build(), gen.packMeta(), root.resolve("build/surf-hud.zip"), folder)
        return folder to hash
    }

    @Test fun `writes mcmeta with the client pack format`() {
        val (folder, _) = generate()
        val pack = JsonParser.parseString(folder.resolve("pack.mcmeta").readText()).asJsonObject.getAsJsonObject("pack")
        assertEquals(97, pack["min_format"].asInt)
        assertEquals(97, pack["max_format"].asInt)
        ZipFile(root.resolve("build/surf-hud.zip").toFile()).use { zip ->
            val text = zip.getInputStream(zip.getEntry("pack.mcmeta")).readBytes().decodeToString()
            val inZip = JsonParser.parseString(text).asJsonObject.getAsJsonObject("pack")
            assertEquals(97, inZip["min_format"].asInt)
            assertEquals(97, inZip["max_format"].asInt)
        }
    }

    @Test fun `writes fonts with slot encoded ascents and spaces`() {
        val (folder, _) = generate()
        val font = JsonParser.parseString(folder.resolve("assets/surf/font/hud/s1_3_default.json").readText()).asJsonObject
        val providers = font.getAsJsonArray("providers").map { it.asJsonObject }
        val ascii = providers.single { it["type"].asString == "bitmap" }
        assertEquals("font/ascii.png", ascii["file"].asString) // creative drops the implicit minecraft: namespace
        assertEquals(HudEncoding.ascent(1, 3), ascii["ascent"].asInt)
        assertTrue(providers.any { it["type"].asString == "space" && it.getAsJsonObject("advances").has("") })
    }

    @Test fun `pixel rows are one size lower each`() {
        val (folder, _) = generate()
        val providers = JsonParser.parseString(folder.resolve("assets/surf/font/hud/s3_0.json").readText()).asJsonObject
            .getAsJsonArray("providers").map { it.asJsonObject }.filter { it["type"].asString == "bitmap" }
        assertEquals(8, providers.size)
        assertEquals(HudEncoding.ascent(3, 2), providers[1]["ascent"].asInt)
    }

    @Test fun `writes textures shader and blank bossbar`() {
        val (folder, _) = generate()
        assertTrue(folder.resolve("assets/surf/textures/font/hud/coin.png").exists())
        assertTrue(folder.resolve("assets/surf/textures/font/hud/panel/middle_256.png").exists())
        assertTrue(folder.resolve("assets/surf/textures/font/hud/head_small/pixel.png").exists())
        assertTrue(folder.resolve("assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png").exists())
        assertContains(folder.resolve("assets/minecraft/shaders/core/text.vsh").readText(), "surf_hud_transform(Position)")
        val include = folder.resolve("assets/surf/shaders/include/hud.glsl").readText()
        assertContains(include, "const int SURF_HUD_SLOT_COUNT = 6;")
        assertContains(include, "vec4(1.0, 1.0, -4.0, -17.0), // slot 1")
    }

    @Test fun `zip and sha1 match`() {
        val (_, hash) = generate()
        assertEquals(40, hash.length)
        assertEquals(hash, root.resolve("build/surf-hud.sha1").readText())
        ZipFile(root.resolve("build/surf-hud.zip").toFile()).use { assertNotNull(it.getEntry("pack.mcmeta")) }
    }
}
