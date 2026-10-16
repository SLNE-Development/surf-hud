package dev.slne.surf.hud.core

import net.kyori.adventure.key.Key
import org.junit.jupiter.api.io.TempDir
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.font.Font
import team.unnamed.creative.font.FontProvider
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class CreativeSmokeTest {
    @Test
    fun `creative writes a font and an unknown file`(@TempDir dir: Path) {
        val pack = ResourcePack.resourcePack()
        pack.packMeta(46, "x")
        pack.unknownFile("surf/extra.txt", Writable.stringUtf8("hello"))
        pack.font(Font.font(Key.key("surf", "hud/test"), listOf(FontProvider.space(mapOf("" to 1)))))
        MinecraftResourcePackWriter.minecraft().writeToDirectory(dir.toFile(), pack)

        assertTrue(dir.resolve("pack.mcmeta").exists())
        assertTrue(dir.resolve("surf/extra.txt").exists())
        assertTrue(dir.resolve("assets/surf/font/hud/test.json").readText().contains("space"))
    }
}
