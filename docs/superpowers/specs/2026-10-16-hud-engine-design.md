# surf-hud engine: config-driven HUDs, layouts, popups and placeholders

Date: 2026-10-16
Status: approved in conversation, pending written review

## Goal

Turn the core-shader prototype (`prototypes/core-shader`) into the real surf-hud plugin, structured like
BetterHud (https://github.com/toxicity188/BetterHud): several HUDs, reusable layouts, popups and placeholders,
all defined in YAML through `surfConfigApi`. A server admin adds or changes a HUD by editing YAML and running
`/hud reload`; no Kotlin and no plugin rebuild. The plugin generates the resource pack itself (BetterHud style)
and writes it as a zip plus an unzipped folder, which is then merged into the server's main pack by hand. The
plugin never hosts or sends the pack.

The rendering technique stays the one proven by the prototype: the element slot is encoded in the font ascent,
one hook in vanilla `core/text.vsh` moves HUD glyphs to their anchor, the HUD travels on a bossbar with a
blanked color, and every placed layout composes to a component with a total advance of 0.

## Scope

In v1:

- HUDs (sets of placed layouts, default HUDs, show/hide per player)
- Layouts with `text`, `image`, `bar` and `head` elements, conditions and expressions
- Texts: vanilla `ascii.png` at any height
- Images: `single` icons, `strip` (backgrounds and bars of any width), `pixel` sets (heads)
- Popups: temporary, stacking or unique, with a duration, live variable updates
- Placeholders: built-ins, Kotlin-registered, popup variables, PlaceholderAPI bridge, MiniPlaceholders bridge
- Pack generation at runtime with team.unnamed creative
- Paper/Folia platform; core stays platform-neutral
- Rewrite of `surf-hud-paper-example` as a demo

Out of scope (later or never):

- TTF / unicode fonts (later)
- Image sequences and animations (later)
- Compass (later)
- Pack hosting or sending (never; the pack is merged into the main pack)
- Minestom platform (later; core must not block it)
- HUDs or layouts defined in Kotlin (never; YAML is the only source of definitions)

## Modules

```
surf-hud-api            public API only
surf-hud-core           platform-neutral engine
  config/               Configurate models and the loader for each folder and config.yml
  model/                validated, immutable compiled model (ids resolved, expressions parsed, slots assigned)
  expression/           parser and evaluator
  placeholder/          registry, typed values, per-placeholder interval cache, popup variables
  pack/                 creative-based generator, vanilla asset fetcher, TextShaderPatcher, hud.glsl generator
  render/               composer (port of the prototype's HudElementBuilder to Adventure components), width measuring
  runtime/              HudPlayerImpl, popup manager, head cache, tick and diff, bossbar transport
  platform/             HudPlatform: per-player scheduling, skin lookup, logger, data folder
surf-hud-paper          Paper/Folia platform, built-in placeholders, PlaceholderAPI and MiniPlaceholders bridges,
                        /hud command, join/quit listeners, bundled minimal default configs
surf-hud-paper-example  demo plugin
```

The old API (`Hud`, `HudElement`, `HudBuilder`, `HudTheme`, `HudGlyph`), core's `HudImpl`, `layout/` package and
`ExampleConfig` are removed. The prototypes and the build-logic pack generator were removed after the engine landed; their findings are in docs/hud-technique.md.

## Data flow

On enable and on `/hud reload`:

1. Load every `.yml` file in each definition folder with `surfConfigApi` (one config manager per file) plus
   `config.yml`.
2. Compile into the immutable model: resolve references, parse expressions, assign shader slots. Collect all
   errors. If there is any error, keep the previous model (or run with no HUDs on first start) and report the
   errors.
3. Generate the pack asynchronously into the output zip and folder and write its SHA-1 next to the zip.
4. Swap the new model in atomically and rebuild every online player's HUD state from it.

Each tick (every `update-interval` ticks, per player, on the player's entity scheduler):

1. Collect visible placed layouts: HUD placements whose HUD is shown and whose conditions hold, plus open popup
   instances.
2. Evaluate the placeholders they need, using the per-placeholder interval cache.
3. Recompose only the placed layouts whose evaluated inputs changed.
4. Concatenate all placed-layout components in slot order and send the bossbar name only if it changed.

`HudPlayer.refresh()` and `PopupHandle.update(...)` schedule an immediate update on the player's scheduler.

## Directory layout

```
plugins/surf-hud-paper/
  config.yml
  images/*.yml
  texts/*.yml
  layouts/*.yml
  huds/*.yml
  popups/*.yml
  assets/               png sources referenced by images (paths in image files are relative to it)
  cache/<version>/      extracted vanilla assets
  cache/heads/          head cache
  build/surf-hud.zip    generated pack
  build/surf-hud.sha1
  build/pack/           generated pack, unzipped
```

Each definition file may contain any number of entries keyed by id. Ids are unique per type across all files;
a duplicate id is a compile error naming both files.

## Configuration model

### config.yml

```yaml
namespace: surf              # namespace of generated fonts, textures and the shader include
minecraft-version: "26.3"    # client version: vanilla assets and pack format come from its jar
bossbar-color: pink          # its sprites are blanked; the HUD bossbar uses this color
update-interval: 2           # ticks between HUD updates
placeholder-intervals:       # cache ticks for bridge placeholders
  papi: 20
  mini: 20
heads:
  await-loaded: true         # wait for heads before showing (see Heads)
  timeout: 5s
  cache-ttl: 1d
  fallback: heads/fallback.png
output:
  zip: build/surf-hud.zip
  folder: build/pack
```

`output.folder` is deleted and rewritten on every pack write, so the output paths are validated at compile
time: both must resolve strictly inside the data folder (not the data folder itself) and must not be or lie in
`config.yml`, `assets`, `cache` or a definition folder (`images`, `texts`, `layouts`, `huds`, `popups`); the
zip must not lie inside the folder. Errors read `config.yml > output.folder: …`. The pack writer also puts a
`.surf-hud-pack` marker into the folder and refuses to delete an existing non-empty folder without it.

### images/

```yaml
coin:     { type: single, file: icons/coin.png }
panel:    { type: strip,  file: strips/panel.png }   # exactly 3 px wide: left, middle, right column
head_big: { type: pixel,  size: 5 }                  # rows default 8 -> 40x40 heads
```

### texts/

```yaml
default: { type: ascii }              # height defaults to 8
title:   { type: ascii, height: 16 }
```

### layouts/

A layout has `align` (`left`, `center`, `right`; relative to its placement point, default `left`), optional
`conditions` (list of boolean expressions, all must hold) and `elements`. Every element has `x`, `y` (GUI
pixels relative to the layout origin, default 0), an optional `id` and optional `conditions`.

| type | fields |
| --- | --- |
| `text` | `font` (text id, default `default`), `text` (MiniMessage with `[placeholder]` and `{expression \| format}`), `align` (`left`, `center`, `right` around `x`), `shadow` (default true) |
| `image` | `image` (single or strip), `width` (expression; strips only, required for strips), `color` (hex, default white) |
| `bar` | `image` (strip), `width` (expression), `value` (expression, clamped to 0..1), `direction` (`right` or `left`, default `right`), `color` |
| `head` | `image` (pixel set), `player` (expression giving a name or UUID), `brightness` (expression 0..1, default 1) |

Example:

```yaml
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
    - { type: image, image: bar_bg, width: 60, y: 1 }
    - { type: bar, image: bar_fill, width: 60, y: 2, value: "[health] / [max_health]" }
    - { type: text, x: 64, y: 1, text: "{[health] / [max_health] * 100 | 0}%" }
```

### huds/

```yaml
main:
  default: true              # shown to every player on join
  conditions: []
  layouts:
    - { layout: money,  anchor: bottom_right, x: -4, y: -17 }
    - { layout: health, anchor: bottom_right, x: -4, y: -30, conditions: [] }
```

Anchors: `top_left`, `top_center`, `top_right`, `center_left`, `center`, `center_right`, `bottom_left`,
`bottom_center`, `bottom_right`.

### popups/

```yaml
vote:
  anchor: center
  duration: -1               # ticks; -1 = until closed from code
  unique: true               # triggering again replaces the open instance
  layouts:
    - { layout: vote_title, y: -80 }
    - { layout: vote_heads, y: -60 }
    - vote_bar               # bare name = placement at 0/0
kill_feed:
  anchor: top_right
  x: -4
  y: 30
  duration: 100
  max-stack: 5               # newest at index 0, oldest dropped when full
  stack-offset: [0, 12]      # index n is offset by n * stack-offset
  layouts: [ kill_entry ]
```

Defaults: `x`/`y` 0, `duration` -1, `unique` false, `max-stack` 1, `stack-offset` [0, 0]. Popup layout
placements are relative to the popup's anchor point plus its `x`/`y`.

## Expressions

Values are numbers (double), strings or booleans. Grammar: literals (`12`, `1.5`, `'text'`, `true`), placeholders
`[name]` and `[name:arg]`, `+ - * /` (`+` concatenates if either side is a string), `== != < <= > >=`,
`&& || !`, parentheses, and the functions `min`, `max`, `round`, `floor`, `ceil`, `clamp(v, lo, hi)`.
`[width:<id>]` is the rendered width in GUI pixels of the `text` element with that id in the same layout; only
text elements can be referenced, so there are no cycles. A reference to an unknown element id is a compile
error.

Inside `text`, `{expression}` inserts the result; `{expression | pattern}` formats a number with a
`java.text.DecimalFormat` pattern (`{[money] | #,##0}`).

Comparing a number with a string, or using a non-boolean as a condition, evaluates to false and logs one
warning per expression.

## Placeholders

Sources, in lookup order:

1. Popup variables of the popup instance being rendered (popups only)
2. Element widths (`[width:id]`)
3. Kotlin-registered placeholders (`SurfHud.placeholders`)
4. Built-ins (registered by the platform through the same registry): `name`, `display_name`, `health`,
   `max_health`, `food`, `saturation`, `armor`, `level`, `exp`, `exp_percent`, `air`, `max_air`, `world`,
   `x`, `y`, `z`, `yaw`, `ping`, `online`, `max_players`, `gamemode`, `sneaking`, `flying`
5. `[papi:<identifier>]` through PlaceholderAPI, if installed (resolved to a string; numeric strings become
   numbers)
6. `[mini:<tag>]` through MiniPlaceholders, if installed (resolved to a component)

A registered placeholder may declare an `interval`; its value is cached per player for that long. Without one
it is evaluated at most once per HUD update. Bridge placeholders use a default interval of 20 ticks,
configurable in `config.yml` (`placeholder-intervals: { papi: 20, mini: 20 }`).

Values are `Number`, `String`, `Boolean`, `Component` or `null` (empty string). A placeholder unknown at
runtime renders as `?` and logs once. A resolver that throws yields `?` and is logged at most once per minute
per placeholder.

Placeholder values are inserted into MiniMessage text as unparsed text, so values (player names, PAPI output)
can never inject tags. Component values (MiniPlaceholders, registered `Component`s) are inserted as components.

## Shader slots and pack

### Slots

One slot per HUD layout placement and one per popup layout placement per stack index. Slots are assigned in a
stable order (huds, then popups, each sorted by id, placements in file order). The maximum is
`(2^24 - 2^20) / 1024 = 15360` slots; more is a compile error. Encoding constants are those of the prototype
(`BASE = 2^20`, `STRIDE = 1024`, `BIAS = 512`, bossbar line y `3 + 19 * row`, row 0).

### Fonts

One font per (slot, element y): `<ns>:hud/s<slot>_<y>`, with a negative y written as `m<-y>` (for example
`hud/s3_m4`). Text elements get their own font per text id, `<ns>:hud/s<slot>_<y>_<textId>`. It contains the providers of every glyph set used at
that y in that slot (with ascent from the slot encoding; pixel sets add `row * size` per row) plus the space
provider (±1 … ±512). Characters are allocated per glyph set in the private use area and only need to be
unique within our fonts, so they cannot clash with fonts of the main pack.

### Pack contents

Built with team.unnamed creative and written both as zip and as folder:

- `pack.mcmeta` with the pack format from the client jar's `version.json`
- textures: `single` images, strip pieces (left, right, middle widths 1 … 256), one white square per pixel set;
  `ascii` sets reference vanilla `minecraft:font/ascii.png` and do not copy it
- the fonts above
- `assets/minecraft/shaders/core/text.vsh`, vanilla patched by `TextShaderPatcher` (moved from build-logic into
  core), and `assets/<ns>/shaders/include/hud.glsl` with the generated slot table
- blanked `assets/minecraft/textures/gui/sprites/boss_bar/<color>_background.png` and `_progress.png`

The pack overrides vanilla `text.vsh`; the main pack it is merged into must not ship its own. Changes to
layout positions, images, fonts or slot assignment need the merged pack redeployed; changes to text,
placeholders, conditions and expressions that keep the slot structure do not. A client using an older pack
than the server's model cannot be detected; the logged SHA-1 helps to check deployments.

### Vanilla assets

Piston-meta version manifest → version JSON → client jar, verified by SHA-1. Only
`assets/minecraft/shaders/core/text.vsh`, `assets/minecraft/textures/font/ascii.png`,
`assets/minecraft/font/include/default.json` and `version.json` are extracted to `cache/<version>/`. With a
complete cache no network is used. Without a cache and without network, generation fails with a clear message.
If `TextShaderPatcher` cannot find its anchors, generation fails and names the version.

## Rendering

The composer is a port of the prototype's `HudElementBuilder` that emits Adventure components. Each placed
layout is composed into one component whose total advance is 0: glyphs are laid out from x = 0, space glyphs
move the cursor, the layout's `align` shifts it relative to its placement point, and the cursor returns to 0 at
the end. Runs split only where font, color or shadow change.

Text elements: placeholders become unparsed-text tags, MiniMessage parses the string, the component is flattened
into runs of (color, bold, shadow), measured with the generated advances (bold adds 1 px per character), then
emitted in the element's font. Italic, underline, strikethrough and obfuscated are ignored with one warning per
element. Characters outside the ascii set become `?` with one warning per character.

Images draw their glyph; strips compose edges plus power-of-two middles to the requested width; bars draw the
first `value * width` px of the strip and leave the cursor at the bar's end (`direction: left` fills from the
right); heads draw one tinted pixel glyph per opaque pixel, multiplied by `brightness`.

## Heads

The face plus hat layer (legacy 64x32 skins ignore an overlay without a transparent pixel, as in the prototype)
is composited to 8x8 ARGB. Skin source: the platform's profile textures for players on this server, otherwise
Mojang name → UUID → profile. Results are cached in memory and in `cache/heads/` with `heads.cache-ttl`.

With `heads.await-loaded: true` (default):

- `HudPlayer.show(...)` and `popup(...)` are `suspend` and resolve every head the shown layouts reference
  before the HUD or popup appears; a popup's duration starts when it appears.
- If a head's `player` expression changes while shown, the element keeps the previous head until the new one is
  cached.
- After `heads.timeout` a head that could not be loaded uses the fallback image.

With `false`, the fallback head is drawn immediately and replaced when the skin is loaded.

## Transport

One bossbar per player in `bossbar-color`, progress 0. It is shown on the player's first HUD update and stays
shown until quit or shutdown, with an empty name while nothing is visible (also while the player's HUD is
disabled), so it keeps the top row the shader expects. Its name is the concatenation of all placed-layout components in slot order. Each placed layout keeps
its last evaluated inputs and component; the bossbar name is sent only when the concatenation changed. On
rejoin the state is rebuilt and the bossbar sent again.

## Popups

- `popup(...)` opens an instance with its variables; `PopupHandle.update(...)` merges new variables and
  updates immediately; `close()` removes it.
- `unique: true`: triggering again replaces the open instance's variables, restarts its duration and returns
  the same handle.
- Otherwise instances stack: the newest gets index 0, the others move down one index; when `max-stack` is
  exceeded the oldest closes. When an instance closes, the instances behind it move up. A changed index means a
  changed slot and a recompose.
- `duration` counts server ticks from the moment the popup appears.

## API (surf-hud-api)

```kotlin
interface SurfHud {
    val placeholders: PlaceholderRegistry
    val huds: Set<String>
    val popups: Set<String>
    fun player(uuid: UUID): HudPlayer
    suspend fun popup(id: String, viewers: Collection<UUID>, variables: Map<String, Any?> = emptyMap()): List<PopupHandle>
    suspend fun reload(): ReloadResult

    companion object : SurfHud by service
}

interface HudPlayer {
    val uuid: UUID
    var enabled: Boolean
    val shownHuds: Set<String>
    suspend fun show(hud: String)
    fun hide(hud: String)
    suspend fun popup(id: String, variables: Map<String, Any?> = emptyMap()): PopupHandle
    fun popups(id: String? = null): List<PopupHandle>
    fun refresh()
}

interface PopupHandle {
    val id: String
    val isOpen: Boolean
    fun update(variables: Map<String, Any?>)
    fun update(vararg variables: Pair<String, Any?>)
    fun close()
}

interface PlaceholderRegistry {
    fun register(name: String, interval: Duration? = null, resolver: (viewer: UUID, arg: String?) -> Any?)
    fun unregister(name: String)
}

data class ReloadResult(val errors: List<String>, val packHash: String?) { val success get() = errors.isEmpty() }

val Identified.hudPlayer: HudPlayer
```

`surf-hud-paper` adds `PlaceholderRegistry.player(name, interval) { player: Player, arg -> ... }`. Unknown HUD
or popup ids throw `IllegalArgumentException`. HUDs shown or hidden at runtime are not persisted; on join a
player gets the `default: true` HUDs. `enabled = false` hides everything without changing `shownHuds`.

## Commands (surf-hud-paper)

Permission `surf.hud.command.<sub>` (`/hud` itself needs none):

- `/hud reload`: configs, compile, pack, swap; prints errors or the pack SHA-1
- `/hud generate`: regenerate the pack from the active model
- `/hud show <player> <hud>`, `/hud hide <player> <hud>`
- `/hud popup <player> <popup> [key=value ...]`
- `/hud toggle [player]`

## Error handling

- Compile errors carry file and path, e.g. `layouts/stats.yml > money > elements[2].width: unknown function 'mx'`.
  All errors of a load are reported together; the previous model stays active.
- Pack generation failures (download, IO, shader anchors) keep the previous pack and are logged.
- Missing asset files referenced by images are compile errors.
- Placeholder failures: see Placeholders. Head failures: fallback after timeout.

## Example plugin (surf-hud-paper-example)

Registers `money` and `rank` placeholders with simulated values, ships the prototype's demo widgets (title,
status, timer, money, health, player) as YAML plus its textures, and runs the vote loop (30 s voting with live
counts, 5 s result, 5 s hidden) as the `vote` popup with two Mojang heads. On first start it copies its YAML and
textures into `plugins/surf-hud-paper/` where missing and calls `SurfHud.reload()`. `surf-hud-paper` itself bundles
only a minimal `main` HUD; the fallback head is generated when `assets/heads/fallback.png` is missing.

## Testing

Unit tests (JUnit) in core:

- expression parser and evaluator, including type mismatches and formatting
- compiler: reference resolution, duplicate ids, slot assignment order, collected errors with paths
- composer: total advance 0, alignment, strip and bar pieces, width measurement, MiniMessage injection safety
- placeholder interval cache and popup stacking, uniqueness and expiry with a fake clock
- pack generation into a temp folder from a small fixture asset set: fonts, slot table, shader patch, mcmeta

End to end: the example plugin on a Paper dev server with the dev client, checked with screenshots as in the
prototype README: all element kinds, two players with different data, vote popup with heads, reload after a
layout edit.
