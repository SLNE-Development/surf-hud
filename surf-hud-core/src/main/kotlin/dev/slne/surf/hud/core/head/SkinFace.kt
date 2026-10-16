package dev.slne.surf.hud.core.head

import java.awt.image.BufferedImage

/** Cuts the 8x8 face out of a skin and composites the hat layer on top. */
object SkinFace {
    /** Face at (8, 8) and hat at (40, 8) in both 64x64 and legacy 64x32 skins (scaled for HD skins). */
    fun extract(skin: BufferedImage): HeadPixels {
        val scale = (skin.width / 64).coerceAtLeast(1)
        fun pixel(x: Int, y: Int) = skin.getRGB(x * scale, y * scale)
        // Like vanilla's "Notch transparency hack": legacy 64x32 skins often fill the overlay area with opaque
        // pixels, and vanilla ignores that layer when it has no transparent pixel at all.
        val legacy = skin.height == skin.width / 2
        val overlayOpaque = (32 until 64).all { x -> (0 until 16).all { y -> (pixel(x, y) ushr 24) >= 128 } }
        val useHat = !(legacy && overlayOpaque)
        return HeadPixels(Array(8) { row ->
            IntArray(8) { column ->
                val hat = pixel(40 + column, 8 + row)
                if (useHat && (hat ushr 24) >= 128) hat or 0xFF000000.toInt() else pixel(8 + column, 8 + row) or 0xFF000000.toInt()
            }
        })
    }
}
