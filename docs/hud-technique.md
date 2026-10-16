# HUD technique

Reference for developers maintaining the HUD engine. It describes how the core-shader technique draws a
server-driven HUD, what was verified on Minecraft Java 26.3, where it breaks, and why the alternative
(`end_of_frame`) was rejected. The technique follows BetterHud (https://github.com/toxicity188/BetterHud),
but the shader is derived from the vanilla source of the target version instead of being maintained by hand.
To run the engine locally (Paper dev server, dev clients, reload, RCON), see `docs/development.md`.

## How it works

1. **Slot encoded in the font ascent.** The engine assigns one shader slot per placed layout: a HUD
   placement, or a popup placement per stack index. There is one font per (slot, element y), named
   `<ns>:hud/s{slot}_{y}` (`m` replaces the minus sign for negative y). Each ascii text set gets its own font
   with a `_<textId>` suffix so characters cannot clash. Every glyph has an ascent of
   `7 - (BASE + slot * STRIDE + BIAS + localY)`, where `localY` is the element y (pixel rows use
   `y + row * size`). Constants: `BASE = 2^20`, `STRIDE = 1024`, `BIAS = 512`,
   `MAX_SLOTS = (2^24 - 2^20) / 1024 = 15360`. The HUD bossbar line is at `y = 3 + 19 * row` with row 0.
   Vanilla draws a glyph's top at `lineY + 7 - ascent`, so HUD glyphs land far below the screen at a y that
   encodes slot and local y. Text colors and shadows stay untouched.
2. **One hook in vanilla `text.vsh`.** The pack is generated at runtime by surf-hud-core, on enable and on
   `/hud reload`. `VanillaAssetFetcher` downloads and caches the needed client-jar files, `TextShaderPatcher`
   patches `text.vsh`, `PackGenerator` builds the pack with creative, and `PackWriter` writes a reproducible
   zip plus folder and a SHA-1. The plugin sends each player one bossbar whose name is the concatenated
   zero-width layout components. The patcher reads `core/text.vsh` from the target version's client
   jar, adds `#include <surf:hud.glsl>` and wraps the projected position in `surf_hud_transform(Position)`.
   Nothing else changes. If either anchor is missing, generation must fail with a message instead of
   shipping a broken pack.
3. **Generated include.** `surf:hud.glsl` holds the constants and the per-slot table (anchor as a fraction
   of the GUI size plus an offset in GUI pixels). `surf_hud_transform` only acts on GUI geometry
   (`ProjMat[3].x == -1`). It decodes the slot, reads the real GUI size from `ProjMat` and moves the glyph to
   `anchor * guiSize + offset + local`.
4. **Zero-width elements.** The bossbar centers its name, so every element must have a total advance of 0.
   The composer lays out glyphs from x = 0 and uses space glyphs (+-1 ... +-512, part of every element font)
   to move back under text for backgrounds, to align right or center, and to return to 0 at the end.
5. **Glyph sets.**
   - `ascii` reuses vanilla `minecraft:font/ascii.png`, optionally at another `height`.
   - `pixel` is a white `size` x `size` square, one glyph per image row, each row `size` px lower. Tinted
     through the component color, it draws any small image pixel by pixel, such as player heads.
   - `images` are icons (one glyph each).
   - `strip` textures are 3 px wide (left edge, middle, right edge) and are cut into edges plus
     power-of-two middle pieces, so a background or bar of any width needs at most about ten glyphs.
6. **Transparent bossbar.** The pack blanks the `pink` bossbar sprites, so only the text remains. HUD
   bossbars must use that color.

A new Minecraft version should only need the version setting changed, because the vanilla shader and font
data come from that version's jar. Breakage is limited to the two patch anchors, the bossbar line position
(`y = 3`, `+19` per row) and the glyph ascent rule.

Practical notes:

- 26.3 compiles shaders through glslang to SPIR-V and logs
  `Couldn't compile pipeline (minecraft:pipeline/gui_text)` with file and line on failure. Two bugs hit in
  the generated include: a comma inside a comment, and `layout` used as an identifier.
- Namespaced includes (`#include <surf:hud.glsl>`) work.
- Vanilla RCON accepts at most 1460 bytes per request, which a full HUD line exceeds. Test tooling must
  split the component (for example via command storage and an `nbt` component). A plugin sends the finished
  component in one bossbar packet.
- Player heads: resolve the name through the Mojang profile APIs, take the 8x8 face plus the hat layer. Like
  vanilla, ignore the overlay on legacy 64x32 skins when it has no transparent pixel; otherwise old skins
  such as Notch's become a black square.

## Test results (26.3, Windows 11, RTX 5080, 2026-10-16)

| Check | Result |
| --- | --- |
| Pack loads, shaders compile | Pass once the two include bugs above were fixed. |
| All element kinds render at their anchors | Pass. Text, icons, panels, bars, pixel images. |
| Per-player data | Pass. Two clients see their own values at the same time. |
| Live updates | Pass at 4 updates per second. Panels resize with their text. |
| Resize | Pass at 640x480 (scale 2), 1280x720 (3) and 1920x1080 (4). |
| Fixed GUI scale | Pass. With `guiScale:2` the HUD uses scale 2 like the hotbar, because the shader reads the real GUI size from `ProjMat`. |
| F1 | Hides the HUD like the rest of the vanilla GUI. |
| F3 | Both draw. Top-left and top-right elements overlap the debug text. |
| Pause menu | The HUD is blurred behind the menu like other HUD elements. |
| Title screen | No bossbar, so no HUD. |
| World text (name tags, signs) and chat | Unaffected; the hook only touches GUI geometry. |
| Vote heads from real skins | Pass for a legacy skin (after the overlay fix) and a modern skin, at both head sizes. |
| Vote lifecycle | Pass: voting, result, hidden, next round, with live countdown, drain bar and vote counts. |
| FPS | 1280x720, vsync off, 8 samples: about 1247 fps with HUD and live updates, about 1276 without the pack, i.e. about 0.02 ms per frame, within noise. |

## Known limits

- Vanilla still reserves the bossbar row (19 px) for the HUD line, so other bossbars appear one row lower.
- The HUD bossbar stays shown (with an empty name when nothing is visible) from the player's first HUD update
  until they leave, so it keeps the top row. A bossbar the server shows before the player's HUD bar (for
  example right at join) still pushes the HUD down a row.
- Moving an element or changing images or fonts changes the generated pack: run `/hud reload`, redeploy the
  merged pack, and clients must download it again. Text, placeholder and condition changes do not.
- Text uses only the ASCII glyphs of `ascii.png`. Other characters need more glyph sets.
- Another pack that also replaces `core/text.vsh` (such as BetterHud) conflicts with this one.
- F3 overlaps top-left and top-right elements.

## Title scale

The vote title can use a 1.5x font (height 12) or a 2x font (height 16). Measured as stroke widths in
captures (one font pixel should be one uniform run):

| Font | GUI scale 2 | GUI scale 3 |
| --- | --- | --- |
| 1.5x (height 12) | 3 px exactly | 4 or 5 px (uneven) |
| 2x (height 16) | 4 px exactly | 6 px |

1.5x is uneven only at odd GUI scales, where 1.5 * scale is not a whole number of screen pixels. 2x is
crisp at every scale.

## Rejected: end_of_frame

26.3 adds an always-on `minecraft:post_effect/end_of_frame` post chain. It can draw a panel pinned to the
screen without replacing a core shader, and the cost is small (about 0.04 ms per frame for two passes).
It was rejected because the chain comes from the resource pack, so it is identical for every player and
cannot carry per-player data. `/posteffect` is per player, but those chains can only read
`minecraft:main` (`entity_outline` and other level targets are rejected), and the shader cannot see the
GUI scale option, so a fixed GUI scale mismatches the hotbar. F1 also does not hide the panel.
