package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.TestAssets
import dev.slne.surf.hud.core.config.definition.definitions.ImageDefinition
import dev.slne.surf.hud.core.config.definition.definitions.TextDefinition
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.*

class GlyphSetBuilderTest {
    @TempDir
    lateinit var assetRoot: Path
    private val chars = CharAllocator()
    private val builder by lazy { GlyphSetBuilder(TestAssets.vanilla(), assetRoot, chars) }

    private fun asset(path: String, bytes: ByteArray) =
        assetRoot.resolve(path).also { it.parent.createDirectories() }.writeBytes(bytes)

    @Test
    fun `ascii advances follow vanilla measuring`() {
        val set = builder.text("default", TextDefinition())
        assertEquals(8, set.height)
        assertEquals(6, set.advance('A'))
        assertEquals(2, set.advance('i'))
        assertEquals(4, set.advance(' '))
        assertNull(set.advance('ä'))
    }

    @Test
    fun `ascii scales with height`() {
        val set = builder.text("title", TextDefinition(height = 16))
        assertEquals(11, set.advance('A'))
        assertEquals(8, set.advance(' '))
    }

    @Test
    fun `single image`() {
        asset("icons/coin.png", TestAssets.png(7, 9))
        val set = builder.image("coin", ImageDefinition("single", "icons/coin.png")) as ImageSet
        assertEquals(9, set.height)
        assertEquals(8, set.glyph.advance)
        assertEquals('', set.glyph.char)
    }

    @Test
    fun `strip is cut into edges and power of two middles`() {
        asset("strips/panel.png", TestAssets.png(3, 11))
        val set = builder.image("panel", ImageDefinition("strip", "strips/panel.png")) as StripSet
        assertEquals(11, set.height)
        assertEquals(listOf(256, 128, 64, 32, 16, 8, 4, 2, 1), set.middles.map { it.width })
        assertEquals(2, set.left.advance)
        assertEquals(
            11,
            (listOf(set.left, set.right) + set.middles).map { it.char }.distinct().size
        )
    }

    @Test
    fun `strip must be three pixels wide`() {
        asset("strips/bad.png", TestAssets.png(4, 11))
        assertFailsWith<DefinitionException> {
            builder.image(
                "bad",
                ImageDefinition("strip", "strips/bad.png")
            )
        }
    }

    @Test
    fun `pixel set`() {
        val set = builder.image("head_big", ImageDefinition("pixel", size = 5)) as PixelSet
        assertEquals(8, set.rows.size)
        assertEquals(40, set.height)
        assertEquals(6, set.advance)
    }

    @Test
    fun `missing file names the path`() {
        val error = assertFailsWith<DefinitionException> {
            builder.image(
                "x",
                ImageDefinition("single", "nope.png")
            )
        }
        assertEquals("file", error.field)
        assertContains(error.message!!, "nope.png")
    }

    @Test
    fun `unknown type`() {
        val error = assertFailsWith<DefinitionException> {
            builder.image(
                "x",
                ImageDefinition("gif", "a.png")
            )
        }
        assertEquals("type", error.field)
    }

    @Test
    fun `ascent encodes slot and local y`() {
        assertEquals(7 - ((1 shl 20) + 3 * 1024 + 512 + 5), HudEncoding.ascent(3, 5))
        assertTrue(HudEncoding.fitsLocalY(-512, 8))
        assertFalse(HudEncoding.fitsLocalY(505, 8))
    }
}
