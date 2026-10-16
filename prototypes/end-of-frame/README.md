# end_of_frame HUD prototype

Tests whether the post-effect hooks added in Minecraft Java 26.3 can draw a server-side HUD anywhere
on screen without replacing a core shader. The pack draws an animated panel pinned to the bottom-right
corner from `assets/minecraft/post_effect/end_of_frame.json`. This folder is a throwaway prototype and
is not part of the plugin.

## Running it

The `surf-hud.minecraft-dev-runtime` plugin from `build-logic/` downloads a vanilla 26.3 client and
server into `~/.gradle/caches/surf-hud-minecraft` and runs them from `run/` (git-ignored).

```sh
# terminal 1: offline-mode dedicated server on localhost:25590, RCON on 25591, Dev and Dev2 are op
./gradlew :prototype-end-of-frame:runDevServer

# terminal 2: offline client "Dev"; the pack is copied, enabled and the client joins the server
./gradlew :prototype-end-of-frame:runDevClient

# server console commands without typing into terminal 1
./gradlew -q :prototype-end-of-frame:devRcon --command="posteffect add Dev surf:probe_outline"
```

| Property | Effect |
| --- | --- |
| `-Pminecraft.username=Dev2` | Second client with its own game directory `run/client/Dev2` |
| `-Pminecraft.joinServer=false` | Start on the title screen instead of joining the dev server |
| `-Pminecraft.enableResourcePacks=false` | Vanilla resources only (FPS baseline) |
| `-Pminecraft.serverPort=…`, `-Pminecraft.rconPort=…` | Other ports (defaults avoid 25565/25575) |

The client logs to `run/client/<username>/logs/latest.log`. After editing the pack, run
`syncDevResourcePacks` and press F3+T in game to reload.

To use the pack in a normal 26.3 install instead, copy `surf-hud-eof-test/` into
`.minecraft/resourcepacks/` and enable it. `end_of_frame` is applied as soon as the pack is active.

## Files

| File | Purpose |
| --- | --- |
| `surf-hud-eof-test/pack.mcmeta` | Pack metadata. 26.3 uses `min_format`/`max_format` (97) instead of `pack_format`. |
| `surf-hud-eof-test/assets/minecraft/post_effect/end_of_frame.json` | The always-on chain: blit `minecraft:main` into `swap`, then `surf:post/hud` reads `swap` and the `surf:hud_panel` texture and writes `minecraft:main`. `HudConfig` holds the bar rectangle, bar colour and margin. |
| `surf-hud-eof-test/assets/surf/shaders/post/hud.fsh` | Pins the panel to the bottom-right corner. Sizes are in GUI pixels times an emulated vanilla auto GUI scale computed from the framebuffer size. Fills the bar from `GameTime` (one cycle every 100 ticks). |
| `surf-hud-eof-test/assets/surf/textures/effect/hud_panel.png` | 100x34 panel; generated, do not edit by hand. |
| `surf-hud-eof-test/assets/surf/post_effect/probe_outline.json` + `shaders/post/probe_outline.fsh` | Probe for `/posteffect`: draws the `minecraft:entity_outline` target scaled into a top-left box. |
| `tools/generate_panel.py` | Regenerates the panel PNG (`python tools/generate_panel.py`, no dependencies). |
| `screenshots/` | Captures from the test run below. |

The GUI scale is vanilla `Window.calculateScale` for "Auto": start at 1 and increase while
`w / (i + 1) >= 320 && h / (i + 1) >= 240`. The result is the largest scale *i* with
`w / i >= 320 && h / i >= 240`, so 1920x1080 gives 4.

## Test results (26.3, Windows 11, RTX 5080, 2026-10-16)

| # | Check | Result |
| --- | --- | --- |
| 1 | Panel renders | Pass. No shader or post-effect messages in `latest.log`. `01-panel-1280x720-auto.jpg` |
| 2 | Pinned bottom-right while resizing | Pass at 640x480, 854x480, 1280x720, 1600x900 and 1920x1080. The scale follows auto (2, 2, 3, 3, 4) and the margin is always 4 GUI px. `02-*.jpg` |
| 3 | GUI scale auto vs fixed | Auto matches vanilla exactly. With a fixed scale the panel keeps the auto scale, because the shader cannot see the option. At 1280x720 with scale 2 the panel is 1.5x too large relative to the hotbar (300 px vs 200 px). Mismatch factor = auto / fixed. `03-gui-scale-fixed-2.jpg` |
| 4 | Hand, hotbar, chat, F1, F3 | The panel draws **over** the hand and **under** all GUI (hotbar, chat, F3; see the overlap at 640x480). **F1 does not hide it.** `04-*.jpg` |
| 5 | Menus / pause | Pause screen: the panel stays and is blurred with the world. Title screen: no panel, because post effects only run while a level renders. `05-*.jpg` |
| 6 | `/posteffect add @s surf:probe_outline` + glowing pigs | **Fail by design.** Log: `Requested post chain surf:probe_outline can not be used as a post effect because it uses targets inaccessible to post effects: [minecraft:entity_outline]`. `06-probe-rejected.jpg` |
| 7 | `/posteffect` per player | Pass. `minecraft:invert` on Dev only; Dev2 unaffected. The invert also inverts the HUD panel, because `end_of_frame` runs before player effects. `07-*.jpg` |
| 8 | FPS cost | 1280x720, vsync off, 8 samples each: with pack ≈ 1222 fps, without ≈ 1284 fps, i.e. ≈ 0.04 ms per frame for two fullscreen passes. That is within the noise of single F3 readings (±50 fps). |

What the 26.3 code says (`GameRenderer`, `LevelTargetBundle`, `PostPass`):

- `end_of_frame` is requested first, then `LocalPlayer.getActivePostEffects()` (the `/posteffect` list),
  then the spectator effect. All of them run after `renderLevel` (which includes the hand) and
  `blitEntityOutline`, and before the GUI.
- Both kinds of chain are validated against `LevelTargetBundle.MAIN_TARGETS = {minecraft:main}`, so
  `entity_outline`, `translucent`, `particles` and the other level targets cannot be read.
- Post passes get `bindDefaultUniforms`, so `Globals` (`GameTime`, `ScreenSize`, …) is available.
  `SamplerInfo` also lists texture inputs, but the shaders here use `textureSize` instead.
