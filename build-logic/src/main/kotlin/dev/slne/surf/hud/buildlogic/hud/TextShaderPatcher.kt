package dev.slne.surf.hud.buildlogic.hud

import org.gradle.api.GradleException

/**
 * Patches vanilla `core/text.vsh` with the two hooks the HUD needs, so the rest of the shader stays
 * whatever the target Minecraft version ships:
 *
 * 1. an `#include` of the generated HUD include right after the projection include, and
 * 2. the vertex position passed through the HUD transform before projection.
 *
 * Each anchor must match exactly once. When Mojang rewrites the shader so an anchor disappears, the
 * build fails with the anchor's name instead of producing a pack that silently does nothing.
 */
object TextShaderPatcher {
    private val includeAnchor = Regex("""#include\s*<minecraft:projection\.glsl>[ \t]*\r?\n""")
    private val positionAnchor = Regex(
        """gl_Position\s*=\s*ProjMat\s*\*\s*ModelViewMat\s*\*\s*vec4\(\s*Position\s*,\s*1\.0\s*\)\s*;"""
    )

    fun patch(vanilla: String, versionId: String, include: String, transformFunction: String): String {
        requireSingleMatch(includeAnchor, vanilla, "projection include", versionId)
        requireSingleMatch(positionAnchor, vanilla, "gl_Position assignment", versionId)
        val patched = vanilla
            .replace(includeAnchor) { it.value + "#include <$include>\n" }
            .replace(positionAnchor) { "gl_Position = ProjMat * ModelViewMat * vec4($transformFunction(Position), 1.0);" }
        val header = "// Patched by surf-hud from vanilla $versionId core/text.vsh; regenerate instead of editing.\n"
        // The #version directive must stay the first line.
        val firstLineEnd = patched.indexOf('\n') + 1
        return patched.substring(0, firstLineEnd) + header + patched.substring(firstLineEnd)
    }

    private fun requireSingleMatch(anchor: Regex, source: String, name: String, versionId: String) {
        val count = anchor.findAll(source).count()
        if (count != 1) {
            throw GradleException(
                "Cannot patch core/text.vsh of Minecraft $versionId: expected the $name anchor once, found it $count times. " +
                    "Compare the new vanilla shader with TextShaderPatcher and adjust the anchor."
            )
        }
    }
}
