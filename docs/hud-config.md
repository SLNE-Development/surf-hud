# surf-hud configuration reference

surf-hud shows HUDs, built from layouts, on a bossbar. Everything is defined in YAML. To add or change a HUD,
edit the files and run `/hud reload`. You do not need to rebuild the plugin.

The plugin generates a resource pack from your definitions. It never hosts or sends that pack. You merge it into
your main pack yourself (see "Resource pack").

## Folder layout

All paths are relative to the plugin's data folder (`plugins/surf-hud-paper/`).

```
config.yml
images/*.yml        image definitions
texts/*.yml         text (font) definitions
layouts/*.yml       layouts
huds/*.yml          HUDs
popups/*.yml        popups
assets/             PNG files. Paths in images/*.yml are relative to this folder.
cache/<version>/    downloaded vanilla assets (do not edit)
cache/heads/        cached player heads (do not edit)
build/surf-hud.zip  generated pack
build/surf-hud.sha1 SHA-1 of the zip
build/pack/         generated pack, unzipped
```

A definition file can hold any number of entries, keyed by id. Ids must be unique per type across all files.
Two layouts with the same id are an error, even in different files.

If a reload finds any error, the previous setup stays active and all errors are listed together. An error names
the file and path, for example `layouts/stats.yml > money > elements[2].width: unknown function 'mx'`.

## config.yml

```yaml
namespace: surf              # namespace of generated fonts, textures and the shader include
minecraft-version: "26.3"    # client version; vanilla assets and pack format come from it
bossbar-color: pink          # the HUD bossbar uses this color; its sprites are blanked in the pack
update-interval: 2           # ticks between HUD updates
placeholder-intervals:       # cache time in ticks for bridge placeholders
  papi: 20
  mini: 20
heads:
  await-loaded: true         # wait for heads before showing a HUD or popup
  timeout: 5s                # after this, a head that did not load uses the fallback
  cache-ttl: 1d
  fallback: heads/fallback.png
output:
  zip: build/surf-hud.zip
  folder: build/pack
```

The values above are the defaults.

With `heads.await-loaded: false`, the fallback head is drawn at once and replaced when the skin has loaded.
If `assets/heads/fallback.png` is missing, a fallback head is generated.

## images/

| type | fields | notes |
| --- | --- | --- |
| `single` | `file` | One icon. |
| `strip` | `file` | Exactly 3 px wide: left column, middle column, right column. Can be drawn at any width. Used for backgrounds and bars. |
| `pixel` | `size`, `rows` | A set of single-pixel squares, used for heads. `size` is the GUI pixels per head pixel. `rows` defaults to 8, so `size: 5` gives a 40x40 head. |

```yaml
coin:     { type: single, file: icons/coin.png }
panel:    { type: strip,  file: strips/panel.png }
head_big: { type: pixel,  size: 5 }
```

`file` is relative to the `assets/` folder. A missing PNG file is an error.

## texts/

A text definition is a font for `text` elements.

| field | default | notes |
| --- | --- | --- |
| `type` | none | Only `ascii` (vanilla `ascii.png`). |
| `height` | 8 | Text height in GUI pixels. |

```yaml
title: { type: ascii, height: 16 }
vs:    { type: ascii, height: 24 }
```

`default` is not built in. On first start, when the `huds/` folder does not exist yet, surf-hud installs
`texts/default.yml` with `default: { type: ascii }` (height 8). It also installs a minimal `layouts/main.yml`
(`welcome`) and `huds/main.yml` (`main`, `default: true`, anchored at `center_left`).

A `text` element that does not set `font` uses the text id `default`. Keep it defined. If you delete it, the
reload fails with the compile error `unknown text 'default'`. The demo files rely on it and do not define it
themselves.
Only ASCII characters are supported. Other characters are shown as `?`.

## layouts/

A layout is a group of elements. It is placed by a HUD or a popup.

| field | default | notes |
| --- | --- | --- |
| `align` | `left` | `left`, `center` or `right`, relative to the point where the layout is placed. |
| `conditions` | none | List of boolean expressions. All must be true for the layout to show. |
| `elements` | none | List of elements. |

Every element has these fields:

| field | default | notes |
| --- | --- | --- |
| `type` | none | `text`, `image`, `bar` or `head`. |
| `x`, `y` | 0 | GUI pixels from the layout origin. |
| `id` | none | Lets other elements read `[width:<id>]` (text elements only). |
| `conditions` | none | Same as for layouts. |

Fields by type:

| type | field | default | notes |
| --- | --- | --- | --- |
| `text` | `font` | `default` | A text id. |
| | `text` | none | MiniMessage text. May contain `[placeholder]` and `{expression \| format}`. |
| | `align` | `left` | `left`, `center` or `right`, around `x`. |
| | `shadow` | `true` | |
| `image` | `image` | none | A `single` or `strip` image. |
| | `width` | none | Expression. Required for strips. |
| | `color` | white | Hex color, for example `"#55FFFF"`. |
| `bar` | `image` | none | A `strip` image. |
| | `width` | none | Expression. |
| | `value` | none | Expression, clamped to 0..1. |
| | `direction` | `right` | `right` or `left`. `left` fills from the right. |
| | `color` | white | |
| `head` | `image` | none | A `pixel` image. |
| | `player` | none | Expression giving a player name or UUID. |
| | `brightness` | `1` | Expression, 0..1. |

Text supports color, bold and gradients. Italic, underline, strikethrough and obfuscated are ignored (one
warning per element). Bold adds 1 px per character.

Excerpt from `layouts/demo.yml`:

```yaml
demo_money:
  align: right
  elements:
    - { type: image, image: panel, width: "[width:money_text] + 17" }
    - { type: image, image: coin, x: 3, y: 2 }
    - { id: money_text, type: text, x: 13, y: 3, text: "<gold>{[money] | #,##0}</gold>" }
demo_health:
  align: right
  elements:
    - { type: image, image: heart }
    - { type: image, image: bar_bg, x: 10, y: 1, width: 60 }
    - { type: bar, image: bar_fill, x: 10, y: 2, width: 60, value: "[health] / [max_health]" }
    - { type: text, x: 74, y: 1, text: "{[health] / [max_health] * 100 | 0}%" }
```

Elements are drawn in the order they are listed.

## huds/

A HUD is a set of placed layouts.

| field | default | notes |
| --- | --- | --- |
| `default` | `false` | If true, every player gets this HUD when they join. |
| `conditions` | none | The HUD only shows while all hold. |
| `layouts` | none | List of placements. |

A placement has `layout` (the layout id), `anchor`, `x`, `y` (default 0) and optional `conditions`.

Anchors: `top_left`, `top_center`, `top_right`, `center_left`, `center`, `center_right`, `bottom_left`,
`bottom_center`, `bottom_right`. `x` and `y` move the layout away from the anchor point. Use negative values
to move left or up. For example, `x: -4` keeps a right-aligned layout 4 px from the right screen edge.

Excerpt from `huds/demo.yml`:

```yaml
demo:
  default: true
  layouts:
    - { layout: demo_title, anchor: top_center, x: 0, y: 4 }
    - { layout: demo_money, anchor: bottom_right, x: -4, y: -17 }
    - { layout: demo_health, anchor: bottom_right, x: -4, y: -30 }
```

Shown or hidden HUDs are not saved. On join a player gets the `default: true` HUDs.

## popups/

A popup is a temporary set of placed layouts. It is opened from code or with `/hud popup`.

| field | default | notes |
| --- | --- | --- |
| `anchor` | none | Anchor of the popup, same values as for HUDs. |
| `x`, `y` | 0 | Offset of the popup from its anchor. |
| `duration` | `-1` | Ticks. `-1` means it stays until it is closed. Counts from the moment it appears. |
| `unique` | `false` | If true, opening it again replaces the open one and restarts its duration. |
| `max-stack` | `1` | How many instances can be open together. The newest has index 0. The oldest closes when the stack is full. |
| `stack-offset` | `[0, 0]` | Instance number `n` is moved by `n * stack-offset`. |
| `layouts` | none | Placements, relative to the popup's anchor plus its `x`/`y`. A bare layout id is placed at 0/0. |

Popup variables (passed when the popup is opened or updated) can be used as placeholders in its layouts.

Excerpt from `popups/vote.yml`:

```yaml
vote:
  anchor: center
  unique: true
  layouts:
    - { layout: vote_title, x: -65, y: -80 }
    - { layout: vote_heads, x: -65, y: -60 }
    - { layout: vote_bar, x: -65, y: 8 }
```

A stacking example. It is illustrative: the `kill_entry` layout is not shown or shipped.


```yaml
kill_feed:
  anchor: top_right
  x: -4
  y: 30
  duration: 100
  max-stack: 5
  stack-offset: [0, 12]
  layouts: [ kill_entry ]
```

## Expressions

Expressions are used in `width`, `value`, `brightness`, `player`, `conditions` and inside `{ }` in text.

- Literals: `12`, `1.5`, `'text'`, `true`, `false`
- Placeholders: `[name]` or `[name:arg]`
- Operators: `+ - * /`, `== != < <= > >=`, `&& || !`, and parentheses. `+` joins text if either side is text.
- Functions: `min`, `max`, `round`, `floor`, `ceil`, `clamp(v, lo, hi)`
- `[width:<id>]`: the drawn width in GUI pixels of the `text` element with that id, in the same layout.
  An unknown id is an error.

In text, `{expression}` inserts the result. `{expression | pattern}` formats a number with a Java
`DecimalFormat` pattern, for example `{[money] | #,##0}` or `{[health] | 0}`.

Comparing a number with text, or using a non-boolean as a condition, gives false and logs one warning per
expression.

## Placeholders

A placeholder is written `[name]` or `[name:arg]`. Sources are checked in this order:

1. Popup variables (only inside popups)
2. `[width:<id>]`
3. Placeholders registered from code (`SurfHud.placeholders`). The example plugin registers `money`, `rank`,
   `stars` and `time`.
4. Built-in placeholders (below)
5. `[papi:<identifier>]`: PlaceholderAPI, if installed. Write the identifier without `%`, for example
   `[papi:player_name]`. Numeric results are treated as numbers.
6. `[mini:<tag>]`: MiniPlaceholders, if installed. The result is a component.

Built-in placeholders:

| name | value |
| --- | --- |
| `name` | player name |
| `display_name` | display name |
| `health`, `max_health` | health and maximum health |
| `food`, `saturation` | food level and saturation |
| `armor` | armor points |
| `level`, `exp`, `exp_percent` | experience level, points, progress |
| `air`, `max_air` | air and maximum air |
| `world` | world name |
| `x`, `y`, `z`, `yaw` | position and yaw |
| `ping` | ping in ms |
| `online`, `max_players` | players online and slot count |
| `gamemode` | game mode |
| `sneaking`, `flying` | true or false |

Caching: a registered placeholder can set an interval (in Kotlin, `register(name, interval)` takes a
`Duration`), and its value is kept per player for that long. Without
one, it is evaluated at most once per HUD update. Bridge placeholders (`papi`, `mini`) are cached for 20 ticks
by default. Change this with `placeholder-intervals` in `config.yml`; those values are in ticks, not durations.

Values are never read as MiniMessage tags, so a player name or PlaceholderAPI result cannot inject formatting.
A placeholder that is unknown at runtime shows as `?` and is logged once. A placeholder that throws an error
shows as `?` and is logged at most once per minute.

## Heads

A `head` element draws the face and hat layer of a player's skin, one tinted pixel at a time. Skins come from
the server for online players, otherwise from Mojang. They are cached in memory and in `cache/heads/` for
`heads.cache-ttl`.

With `heads.await-loaded: true`, a HUD or popup that uses heads only appears once the heads are loaded. A head
that is not loaded after `heads.timeout` uses the fallback image. If the `player` expression changes while the
head is shown, the old head stays until the new one is loaded.

## Commands

The permission for each subcommand is `surf.hud.command.<sub>`.

| command | permission | what it does |
| --- | --- | --- |
| `/hud reload` | `surf.hud.command.reload` | Reloads configs, compiles, regenerates the pack. Prints errors or the pack SHA-1. |
| `/hud generate` | `surf.hud.command.generate` | Regenerates the pack from the active setup. |
| `/hud show <player> <hud>` | `surf.hud.command.show` | Shows a HUD to a player. |
| `/hud hide <player> <hud>` | `surf.hud.command.hide` | Hides a HUD from a player. |
| `/hud popup <player> <popup> [key=value ...]` | `surf.hud.command.popup` | Opens a popup with variables, for example `/hud popup Dev vote title=Test left=Notch right=jeb_`. |
| `/hud toggle [player]` | `surf.hud.command.toggle` | Turns all HUDs on or off for a player (yourself if no player is given). |

## Resource pack

The plugin writes the pack to `build/surf-hud.zip` and unzipped to `build/pack/`. It logs the SHA-1, which is
also in `build/surf-hud.sha1`. Use the SHA-1 to check which pack version a deployment has.

To use it, merge `build/pack/` into your main resource pack and deploy that pack as usual.

- The pack replaces vanilla `assets/minecraft/shaders/core/text.vsh`. **Your main pack must not contain its own
  `core/text.vsh`.** If it does, one of the two shader changes will be lost. Merge both changes into one file by
  hand if you need both.
- The pack also blanks the sprites of the boss bar in `bossbar-color`. Other boss bars of that color will not
  be visible.
- Keep `namespace` the same when you update, or old fonts stay in the main pack.

If pack generation needs vanilla assets and `cache/<version>/` is empty, the server needs internet access once
to download them. If the shader patch cannot find its anchors in a new Minecraft version, generation fails and
names the version. The previous pack is kept.

### What needs a pack redeploy

You must redeploy the merged pack after changing:

- layout positions (`x`, `y` of an element, or the `anchor`, `x`, `y` of a placement)
- images and their files
- texts (fonts and heights)
- anything that changes the number or order of placements (slots), such as adding a layout to a HUD or popup

You do not need to redeploy after changing:

- `text` strings, placeholders, conditions and expressions, if the placements stay the same
- values in `config.yml` that do not change the pack (`update-interval`, `placeholder-intervals`, `heads`)

After redeploying, players must reload the pack (rejoin, or `F3+T`). The plugin cannot detect a client that
has an older pack than the server.

At most 15360 placements are possible in total (HUD placements plus popup placements, each popup placement once
per stack index). More is an error.
