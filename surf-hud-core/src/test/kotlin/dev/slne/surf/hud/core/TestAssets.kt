package dev.slne.surf.hud.core

import dev.slne.surf.hud.core.pack.VanillaAssets
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

/**
 * A tiny stand-in for the vanilla client assets: a 16x16 grid of 8x8 ascii cells where every printable
 * character is 5 px wide (advance 6), except `i` (1 px, advance 2).
 */
object TestAssets {
    val TEXT_VSH = """
        #version 330

        #include <minecraft:fog.glsl>
        #include <minecraft:projection.glsl>

        in vec3 Position;

        void main() {
            gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
        }
    """.trimIndent() + "\n"

    fun asciiRows(): List<String> = (0 until 16).map { row ->
        String(CharArray(16) { column ->
            val code = row * 16 + column
            if (code in 32..126) code.toChar() else '\u0000'
        })
    }

    fun asciiImage(): BufferedImage {
        val image = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
        for (code in 33..126) {
            val width = if (code == 'i'.code) 1 else 5
            val x0 = (code % 16) * 8
            val y0 = (code / 16) * 8
            for (x in 0 until width) for (y in 0 until 7) image.setRGB(x0 + x, y0 + y, -1)
        }
        return image
    }

    fun vanilla() = VanillaAssets("26.3", 97, TEXT_VSH, asciiImage(), asciiRows(), 8)

    fun png(width: Int, height: Int, argb: Int = -1): ByteArray {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until width) for (y in 0 until height) image.setRGB(x, y, argb)
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    fun clientJar(): ByteArray {
        val defaultFont = """{"providers":[{"type":"bitmap","file":"minecraft:font/ascii.png","ascent":7,"chars":[${
            asciiRows().joinToString(",") { row -> "\"" + row.map { if (it == '\u0000') "\\u0000" else if (it == '"' || it == '\\') "\\$it" else "$it" }.joinToString("") + "\"" }
        }]}]}"""
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            fun entry(name: String, bytes: ByteArray) {
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
            entry("version.json", """{"id":"26.3","pack_version":{"resource_major":97,"data_major":90}}""".toByteArray())
            entry("assets/minecraft/shaders/core/text.vsh", TEXT_VSH.toByteArray())
            entry("assets/minecraft/font/include/default.json", defaultFont.toByteArray())
            entry("assets/minecraft/textures/font/ascii.png", ByteArrayOutputStream().also { ImageIO.write(asciiImage(), "png", it) }.toByteArray())
        }
        return out.toByteArray()
    }
}
