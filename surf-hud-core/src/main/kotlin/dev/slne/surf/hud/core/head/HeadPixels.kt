package dev.slne.surf.hud.core.head

/** An 8x8 head face as ARGB pixels, indexed `[row][column]`, hat layer already composited. */
class HeadPixels(val argb: Array<IntArray>) {
    init {
        require(argb.size == 8 && argb.all { it.size == 8 }) { "Head must be 8x8" }
    }

    /** The same head with every color multiplied by [factor] (0..1). */
    fun dimmed(factor: Double): HeadPixels {
        if (factor >= 1.0) return this
        val f = factor.coerceIn(0.0, 1.0)
        return HeadPixels(Array(8) { row ->
            IntArray(8) { column ->
                val pixel = argb[row][column]
                val r = (((pixel shr 16) and 0xFF) * f).toInt()
                val g = (((pixel shr 8) and 0xFF) * f).toInt()
                val b = ((pixel and 0xFF) * f).toInt()
                (pixel and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        })
    }

    companion object {
        /** A plain two-tone face, colored by [seed]; used when no fallback image exists. */
        fun placeholder(seed: String): HeadPixels {
            val skin = 0xFFC8A07A.toInt()
            val hair = 0xFF000000.toInt() or (seed.hashCode() and 0x7F7F7F)
            return HeadPixels(Array(8) { row ->
                IntArray(8) { column ->
                    when {
                        row < 2 -> hair
                        row == 3 && (column == 1 || column == 6) -> 0xFF222244.toInt()
                        row == 6 && column in 2..5 -> 0xFF7A4A3A.toInt()
                        else -> skin
                    }
                }
            })
        }
    }
}
