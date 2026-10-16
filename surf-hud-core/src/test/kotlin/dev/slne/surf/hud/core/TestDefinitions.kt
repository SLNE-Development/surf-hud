package dev.slne.surf.hud.core

import dev.slne.surf.hud.core.config.DefinitionLoader
import dev.slne.surf.hud.core.model.CompileResult
import dev.slne.surf.hud.core.model.Compiler
import dev.slne.surf.hud.core.model.HudModel
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

/** A small but complete definition set (money panel, health bar, head popup) used by many tests. */
object TestDefinitions {
    fun standardFiles(): Map<String, String> = mapOf(
        "texts/default.yml" to "default: { type: ascii }",
        "images/basic.yml" to """
            coin: { type: single, file: icons/coin.png }
            panel: { type: strip, file: strips/panel.png }
            head_small: { type: pixel, size: 2 }
        """,
        "layouts/stats.yml" to """
            money:
              align: right
              elements:
                - { type: image, image: panel, width: "[width:money_text] + 14" }
                - { type: image, image: coin, x: 3, y: 2 }
                - { id: money_text, type: text, x: 12, y: 3, text: "<gold>[money]</gold>" }
            health:
              align: right
              conditions: [ "[health] < [max_health]" ]
              elements:
                - { type: bar, image: panel, width: 60, value: "[health] / [max_health]" }
            head:
              elements:
                - { type: head, image: head_small, player: "[target]" }
        """,
        "huds/main.yml" to """
            main:
              default: true
              layouts:
                - { layout: money, anchor: bottom_right, x: -4, y: -17 }
                - { layout: health, anchor: bottom_right, x: -4, y: -30 }
            extra:
              layouts:
                - { layout: money, anchor: top_left, x: 4, y: 4 }
        """,
        "popups/feed.yml" to """
            feed:
              anchor: top_right
              x: -4
              y: 30
              duration: 100
              max-stack: 2
              stack-offset: [0, 12]
              layouts: [ head ]
            vote:
              anchor: center
              unique: true
              layouts:
                - head
        """,
    )

    fun write(root: Path, files: Map<String, String>) {
        files.forEach { (path, text) -> root.resolve(path).also { it.parent.createDirectories() }.writeText(text.trimIndent()) }
        root.resolve("assets/icons/coin.png").also { it.parent.createDirectories() }.writeBytes(TestAssets.png(7, 9))
        root.resolve("assets/strips/panel.png").also { it.parent.createDirectories() }.writeBytes(TestAssets.png(3, 11))
    }

    fun load(root: Path, files: Map<String, String> = standardFiles()) =
        DefinitionLoader(ConfigurateTestReader).also { write(root, files) }.load(root)

    fun compileResult(root: Path, files: Map<String, String> = standardFiles()) =
        Compiler(TestAssets.vanilla(), root.resolve("assets")).compile(load(root, files))

    fun compile(root: Path, files: Map<String, String> = standardFiles()): HudModel =
        when (val result = compileResult(root, files)) {
            is CompileResult.Success -> result.model
            is CompileResult.Failure -> error("Test definitions do not compile: ${result.errors}")
        }
}
