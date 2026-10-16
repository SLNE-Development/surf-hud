package dev.slne.surf.hud.core.pack

import dev.slne.surf.hud.core.TestAssets
import kotlin.test.*

class TextShaderPatcherTest {
    @Test fun `adds the include and wraps the position`() {
        val patched = TextShaderPatcher.patch(TestAssets.TEXT_VSH, "26.3", "surf:hud.glsl", "surf_hud_transform")
        assertTrue(patched.startsWith("#version 330\n// Patched by surf-hud"))
        assertContains(patched, "#include <minecraft:projection.glsl>\n#include <surf:hud.glsl>\n")
        assertContains(patched, "vec4(surf_hud_transform(Position), 1.0)")
    }

    @Test fun `missing anchor fails with the version`() {
        val error = assertFailsWith<PackException> {
            TextShaderPatcher.patch("#version 330\nvoid main() {}\n", "27.1", "surf:hud.glsl", "f")
        }
        assertContains(error.message!!, "27.1")
    }
}
