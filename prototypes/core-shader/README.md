# Core-shader HUD prototype

Draws a server-driven HUD anywhere on screen by patching the vanilla `core/text.vsh` with one hook. The
HUD text travels to each player on their own bossbar. It is the fallback for the
[end_of_frame prototype](../end-of-frame/README.md), which cannot carry per-player data. The design follows
BetterHud (https://github.com/toxicity188/BetterHud), but the shader is generated from the vanilla source
of the target version instead of maintained by hand.

## How it works

1. **Slot encoded in the font ascent.** Every HUD element (`hud-layout.json` → `elements`) gets a slot
   number and its own font `surf:hud/<element>`. In that font every glyph has an ascent of
   `7 − (2^20 + slot·1024 + 512 + layerY)`. Vanilla draws a glyph's top at `lineY + 7 − ascent`, so HUD
   glyphs land far below the screen at a y that encodes slot and layer offset. Text colors and shadows stay
   untouched.
2. **One hook in vanilla `text.vsh`.** `TextShaderPatcher` (build-logic) reads `core/text.vsh` from the
   target version's client jar. It adds `#include <surf:hud.glsl>` and wraps the projected position in
   `surf_hud_transform(Position)`; nothing else changes. If either anchor is missing, the build fails with a
   message instead of shipping a broken pack.
3. **Generated include.** `surf:hud.glsl` holds the constants and the per-slot table (anchor as a fraction of
   the GUI size plus an offset in GUI pixels). `surf_hud_transform` only acts on GUI geometry
   (`ProjMat[3].x == −1`). It decodes the slot, reads the real GUI size from `ProjMat` and moves the glyph to
   `anchor·guiSize + offset + local`.
4. **Zero-width elements.** The bossbar centers its name, so every element must have a total advance of 0.
   The composer lays out glyphs from x = 0 and uses space glyphs (±1 … ±512, part of every element font) to
   move back under text for backgrounds, to align right or center, and to return to 0 at the end.
5. **Glyph sets.** `ascii` reuses vanilla `minecraft:font/ascii.png`. `images` are icons (one glyph each).
   `strip` textures are 3 px wide (left edge, middle, right edge) and are cut into edges plus power-of-two
   middle pieces, so a background or bar of any width needs at most about ten glyphs.
6. **Transparent bossbar.** The pack blanks the `pink` bossbar sprites, so only the text remains. HUD bossbars
   must use that color.

A new Minecraft version only needs `minecraftDev.version` changed. The vanilla shader and font data come from
that version's jar. Breakage is limited to the two patch anchors, the bossbar line position (`y = 3`,
`+19` per row) and the glyph ascent rule.

## Running it

```sh
./gradlew :prototype-core-shader:runDevServer                              # terminal 1
./gradlew :prototype-core-shader:runDevClient                              # terminal 2 (Dev)
./gradlew :prototype-core-shader:runDevClient -Pminecraft.username=Dev2    # optional second client
./gradlew :prototype-core-shader:runHudDemo                                # live data for Dev and Dev2
```

`generateHudPack` runs automatically. It writes the pack to `build/hud-pack/surf-hud-core-test` and the
composer metadata (glyph characters, advances, fonts) to `build/hud-pack/hud-metadata.json`.
`-Phud.players=Dev` limits the demo to one player. Starting a second demo makes the first one stop, because
two demos writing the same storage make the HUD jump between their values.

Vanilla RCON accepts at most 1460 bytes per request, which a full HUD line exceeds. The demo therefore
appends each element (170 to 380 bytes) to command storage and sets the bossbar name to an `nbt` component
that joins them. A plugin sends the finished component in one bossbar packet instead.

## Files

| File | Purpose |
| --- | --- |
| `hud-layout.json` | Glyph sets, elements, anchors, offsets and per-element layer offsets |
| `textures/` | Source icons and strips; regenerate with `python tools/generate_textures.py` |
| `src/main/kotlin/…/HudComposer.kt` | Builds zero-width element components: text, icons, strips, partial strips (bars), alignment |
| `src/main/kotlin/…/HudDemo.kt` | Simulated per-player data, pushed over RCON every 250 ms |
| `build-logic/…/hud/` | `GenerateHudPack` task, `HudPackGenerator`, `TextShaderPatcher`, `HudLayout` |

## Demo elements

| Element | Anchor | Kind |
| --- | --- | --- |
| `title` | top center | Plain colored text |
| `status` | top left | Icons as glyphs with counts |
| `timer` | top right | Text on a background (panel sized to the text) |
| `money` | bottom right | Text on a background with an icon prefix |
| `health` | bottom right, above money | Icon, progress bar (track + partial fill) and percentage |
| `player` | bottom left | Text with an icon prefix |

## Test results (26.3, Windows 11, RTX 5080, 2026-10-16)

| Check | Result |
| --- | --- |
| Pack loads, shaders compile | Pass once two bugs in the generated include were fixed (a comma inside a comment, `layout` used as a name). 26.3 compiles through glslang to SPIR-V and logs `Couldn't compile pipeline (minecraft:pipeline/gui_text)` with file and line. Namespaced includes (`#include <surf:hud.glsl>`) work. |
| All element kinds render at their anchors | Pass. `01-all-elements-dev.jpg` |
| Per-player data | Pass. Dev and Dev2 see their own money, health, stars and name at the same time. `02-per-player-data-dev2.jpg` |
| Live updates | Pass at 4 updates per second. Panels resize with their text. |
| Resize | Pinned at 640x480 (scale 2), 1280x720 (3) and 1920x1080 (4). `03-resize-640x480.jpg` |
| Fixed GUI scale | **Pass.** With `guiScale:2` the HUD uses scale 2 like the hotbar, because the shader reads the real GUI size. `04-gui-scale-fixed-2.jpg` |
| F1 | Hides the HUD like the rest of the vanilla GUI. `05-f1-hides-hud.jpg` |
| F3 | Both draw; top-left and top-right elements overlap the debug text. `06-f3-overlap-1920x1080.jpg` |
| Pause menu | The HUD is blurred behind the menu like other HUD elements. `07-pause-menu.jpg` |
| Title screen | No bossbar, so no HUD |
| World text (name tags, signs) and chat | Unaffected; the hook only touches GUI geometry. `08-world-text-unaffected.jpg` |
| FPS | 1280x720, vsync off, 8 samples: ≈ 1247 fps with HUD and live updates, ≈ 1276 without the pack, i.e. ≈ 0.02 ms per frame, within noise. |

## Known limits

- Vanilla still reserves the bossbar row (19 px) for the HUD line, so other bossbars appear one row lower.
- Element layer offsets and anchors are baked into the pack. Moving an element means regenerating the pack;
  changing content does not.
- Text uses only the ASCII glyphs of `ascii.png`. Other characters need more glyph sets.
- Another pack that also replaces `core/text.vsh` (such as BetterHud) conflicts with this one.
