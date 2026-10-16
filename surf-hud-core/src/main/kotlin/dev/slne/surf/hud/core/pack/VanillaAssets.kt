package dev.slne.surf.hud.core.pack

import java.awt.image.BufferedImage

/**
 * The vanilla client files the pack is generated from: the `core/text.vsh` to patch, the `ascii.png` font
 * texture with its character grid and default height, and the resource pack format of [versionId].
 */
class VanillaAssets(
    val versionId: String,
    val packFormat: Int,
    val textVsh: String,
    val asciiImage: BufferedImage,
    val asciiRows: List<String>,
    val asciiHeight: Int,
)
