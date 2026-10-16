# surf-hud Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the old line/pill HUD with a BetterHud-style, YAML-driven HUD engine (HUDs, layouts, popups, placeholders) that generates its own core-shader resource pack at runtime.

**Architecture:** `surf-hud-core` loads YAML definitions through `surfConfigApi`, compiles them into an immutable model (glyph sets, shader slots, fonts), generates the pack with team.unnamed creative, and renders each player's visible layouts into one zero-width bossbar component per update. `surf-hud-paper` provides the Folia-safe platform, built-in placeholders, PlaceholderAPI/MiniPlaceholders bridges and the `/hud` command. `surf-hud-paper-example` is the demo.

**Tech Stack:** Kotlin 2.4, surf-api 3.47 (Configurate fork, Adventure 5.2, kotlinx-coroutines 1.11, CommandAPI, flogger), team.unnamed creative 1.7.3, JUnit 5 via kotlin-test-junit5, mockk available.

**Spec:** `docs/superpowers/specs/2026-10-16-hud-engine-design.md`. Read it before starting any task.

## Global Constraints

- Commit messages: Conventional Commits in English, e.g. `feat(core): add expression parser`, ending with `Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>`.
- Encoding constants (must match the prototype): `BASE = 2^20`, `STRIDE = 1024`, `BIAS = 512`, `MAX_SLOTS = (2^24 - 2^20) / 1024 = 15360`, bossbar line y `3 + 19 * row` with row 0, ascent `7 - (BASE + slot * STRIDE + BIAS + localY)`.
- Space glyphs: ±1, ±2, ±4 … ±512, chars `U+F800` + 2·power (positive) and `U+F800` + 2·power + 1 (negative).
- Non-ascii glyph chars are allocated from `U+E000` to `U+F7FF`; more glyphs is a compile error.
- Ids (images, texts, layouts, huds, popups) must match `[a-z0-9_-]+`.
- Defaults from the spec: `namespace: surf`, `minecraft-version: "26.3"`, `bossbar-color: pink`, `update-interval: 2`, `placeholder-intervals: {papi: 20, mini: 20}`, `heads: {await-loaded: true, timeout: 5s, cache-ttl: 1d, fallback: heads/fallback.png}`, `output: {zip: build/surf-hud.zip, folder: build/pack}`.
- Placeholder values are inserted into MiniMessage as unparsed text; only `Component` values are inserted as components.
- The core module must not depend on Paper/Bukkit.
- Doc comments: KDoc on every public class and on non-obvious functions, describing what the code does (not history), matching the prototype's comment density.
- Build/test commands run from the repo root with `./gradlew` (Git Bash).

## Review Focus

1. A player name or placeholder value containing `<red>` or `<click:...>` must show literally and never be parsed as MiniMessage. Test: Task 9 `placeholder values are not parsed as MiniMessage`.
2. PlaceholderAPI expansions often return `§`-colored legacy text, and those colors should show up as colors, not as literal `§`. Test: Task 8 `legacy section text becomes a component`.
3. Non-ASCII text (umlauts, emoji in a display name) must render as `?` without throwing. Test: Task 9 `characters outside the font become question marks`.
4. A width or bar expression that evaluates to a negative number, `Missing` or a division by zero must draw nothing for that element and must not throw. Test: Task 10 `invalid widths draw nothing`.
5. A reload that removes a HUD or popup players are currently using must drop or close it without errors. A player leaving while `show()`/`popup()` waits on heads must not crash. Tests: Task 12 `reload drops removed huds and popups` and `show on an offline player does not wait`.

---

## File map

```
surf-hud-api/src/main/kotlin/dev/slne/surf/hud/api/
  SurfHud.kt  HudPlayer.kt  PopupHandle.kt  PlaceholderRegistry.kt  ReloadResult.kt
surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/
  HudEngine.kt
  expression/  Value.kt PlaceholderRef.kt Expr.kt ExpressionParser.kt TextTemplate.kt
  config/      HudSettings.kt Definitions.kt PlacementDefinitionSerializer.kt DefinitionReader.kt
               DefinitionLoader.kt Durations.kt
  pack/        HttpGet.kt VanillaAssets.kt VanillaAssetFetcher.kt TextShaderPatcher.kt ShaderInclude.kt
               PackGenerator.kt PackWriter.kt
  model/       HudEncoding.kt Glyphs.kt GlyphSetBuilder.kt Specs.kt HudModel.kt Compiler.kt ErrorCollector.kt
  placeholder/ HudClock.kt PlaceholderRegistryImpl.kt PlaceholderCache.kt RenderScope.kt Warnings.kt LegacyText.kt
  render/      TextRenderer.kt GlyphWriter.kt LayoutRenderer.kt
  head/        HeadPixels.kt SkinFace.kt SkinSource.kt HeadService.kt
  runtime/     PlayerHud.kt PopupInstance.kt
  platform/    HudPlatform.kt
surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/
  TestAssets.kt TestDefinitions.kt ConfigurateTestReader.kt + one test file per unit
surf-hud-paper/src/main/kotlin/dev/slne/surf/hud/paper/
  PaperMain.kt PaperSurfHud.kt PaperHudPlatform.kt PaperSkinSource.kt PaperPlaceholders.kt
  BuiltinPlaceholders.kt DefaultFiles.kt bridge/PapiBridge.kt bridge/MiniPlaceholdersBridge.kt
  command/HudCommand.kt listener/HudConnectionListener.kt
surf-hud-paper/src/main/resources/defaults/  texts/default.yml layouts/main.yml huds/main.yml
surf-hud-paper-example/src/main/kotlin/dev/slne/surf/hud/example/paper/
  PaperExampleMain.kt ExampleInstaller.kt ExamplePlaceholders.kt VoteLoop.kt
surf-hud-paper-example/src/main/resources/surf-hud/  huds/ layouts/ popups/ images/ texts/ assets/
```

---

### Task 1: New API, removal of the old system, dependencies

**Files:**
- Delete: `surf-hud-api/src/main/kotlin/dev/slne/surf/hud/api/{Hud.kt,HudBuilder.kt,HudElement.kt}`, `surf-hud-api/src/main/kotlin/dev/slne/surf/hud/api/theme/`
- Delete: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/{AbstractSurfHud.kt,HudImpl.kt}`, `.../core/layout/`, `.../core/config/ExampleConfig.kt` (the user's staged reference file; check `git status` first and keep a copy of its content in the commit message body if it differs from HEAD)
- Delete: `surf-hud-paper-example/src/main/kotlin/dev/slne/surf/hud/example/paper/{hud,listener,command}/`
- Rewrite: `surf-hud-api/src/main/kotlin/dev/slne/surf/hud/api/SurfHud.kt`
- Create: `surf-hud-api/src/main/kotlin/dev/slne/surf/hud/api/{HudPlayer.kt,PopupHandle.kt,PlaceholderRegistry.kt,ReloadResult.kt}`
- Modify: `surf-hud-core/build.gradle.kts`
- Stub: `surf-hud-paper/src/main/kotlin/dev/slne/surf/hud/paper/{PaperMain.kt,PaperSurfHud.kt,PaperHudPlatform.kt,listener/HudConnectionListener.kt}`, `surf-hud-paper-example/.../PaperExampleMain.kt` (minimal so the build compiles; replaced in Tasks 13–16)
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/CreativeSmokeTest.kt`

**Interfaces:**
- Produces (API, used by everything later):

```kotlin
interface SurfHud { val placeholders: PlaceholderRegistry; val huds: Set<String>; val popups: Set<String>
  fun player(uuid: UUID): HudPlayer
  suspend fun popup(id: String, viewers: Collection<UUID>, variables: Map<String, Any?> = emptyMap()): List<PopupHandle>
  suspend fun reload(): ReloadResult }
interface HudPlayer { val uuid: UUID; var enabled: Boolean; val shownHuds: Set<String>
  suspend fun show(hud: String); fun hide(hud: String)
  suspend fun popup(id: String, variables: Map<String, Any?> = emptyMap()): PopupHandle
  fun popups(id: String? = null): List<PopupHandle>; fun refresh() }
interface PopupHandle { val id: String; val isOpen: Boolean
  fun update(variables: Map<String, Any?>); fun update(vararg variables: Pair<String, Any?>); fun close() }
fun interface PlaceholderResolver { fun resolve(viewer: UUID, argument: String?): Any? }
interface PlaceholderRegistry { fun register(name: String, interval: Duration? = null, resolver: PlaceholderResolver)
  fun unregister(name: String); fun isRegistered(name: String): Boolean }
data class ReloadResult(val errors: List<String>, val packHash: String?) { val success: Boolean }
val Identified.hudPlayer: HudPlayer
```

- [ ] **Step 1: Delete the old API, core and example code**

```bash
git rm -r -q surf-hud-api/src/main/kotlin/dev/slne/surf/hud/api/Hud.kt \
  surf-hud-api/src/main/kotlin/dev/slne/surf/hud/api/HudBuilder.kt \
  surf-hud-api/src/main/kotlin/dev/slne/surf/hud/api/HudElement.kt \
  surf-hud-api/src/main/kotlin/dev/slne/surf/hud/api/theme \
  surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/AbstractSurfHud.kt \
  surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/HudImpl.kt \
  surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/layout \
  surf-hud-paper-example/src/main/kotlin/dev/slne/surf/hud/example/paper/hud \
  surf-hud-paper-example/src/main/kotlin/dev/slne/surf/hud/example/paper/listener \
  surf-hud-paper-example/src/main/kotlin/dev/slne/surf/hud/example/paper/command
git rm -q -f surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/config/ExampleConfig.kt
```

- [ ] **Step 2: Write the API**

`surf-hud-api/src/main/kotlin/dev/slne/surf/hud/api/SurfHud.kt`:

```kotlin
package dev.slne.surf.hud.api

import dev.slne.surf.api.core.util.requiredService
import net.kyori.adventure.identity.Identified
import java.util.*

private val service = requiredService<SurfHud>()

/**
 * Entry point of surf-hud. HUDs, layouts and popups are defined in YAML in the plugin folder; code shows and
 * hides them, opens popups and supplies placeholder values.
 */
interface SurfHud {
    /** Placeholders that HUD texts and expressions can use as `[name]` or `[name:argument]`. */
    val placeholders: PlaceholderRegistry

    /** Ids of the HUDs in the active configuration. */
    val huds: Set<String>

    /** Ids of the popups in the active configuration. */
    val popups: Set<String>

    /** The HUD state of a player; created on first access. */
    fun player(uuid: UUID): HudPlayer

    /** Opens popup [id] for every viewer with the same [variables]; see [HudPlayer.popup]. */
    suspend fun popup(
        id: String,
        viewers: Collection<UUID>,
        variables: Map<String, Any?> = emptyMap(),
    ): List<PopupHandle>

    /**
     * Reloads every definition, regenerates the resource pack and swaps the new configuration in. On any error
     * the previous configuration stays active and the result lists the errors.
     */
    suspend fun reload(): ReloadResult

    companion object : SurfHud by service {
        val instance: SurfHud get() = service
    }
}

/** The HUD state of this player or audience. */
val Identified.hudPlayer: HudPlayer
    get() = SurfHud.player(identity().uuid())
```

`HudPlayer.kt`:

```kotlin
package dev.slne.surf.hud.api

import java.util.*

/** What one player sees: the HUDs shown to them and their open popups. */
interface HudPlayer {
    val uuid: UUID

    /** Master switch; `false` hides everything without changing [shownHuds]. */
    var enabled: Boolean

    /** Ids of the HUDs currently shown to this player. */
    val shownHuds: Set<String>

    /**
     * Shows HUD [hud]. With `heads.await-loaded` this suspends until every head the HUD draws is loaded
     * (or timed out). Throws [IllegalArgumentException] for an unknown id.
     */
    suspend fun show(hud: String)

    /** Hides HUD [hud]. Throws [IllegalArgumentException] for an unknown id. */
    fun hide(hud: String)

    /**
     * Opens popup [id] with [variables], which its layouts read as placeholders. With `heads.await-loaded` this
     * suspends until its heads are loaded; the popup's duration starts when it appears. A `unique` popup that is
     * already open is updated and restarted instead, and its existing handle is returned.
     */
    suspend fun popup(id: String, variables: Map<String, Any?> = emptyMap()): PopupHandle

    /** Open popups, optionally only those with [id]. */
    fun popups(id: String? = null): List<PopupHandle>

    /** Re-renders this player's HUD on the next tick instead of waiting for the update interval. */
    fun refresh()
}
```

`PopupHandle.kt`:

```kotlin
package dev.slne.surf.hud.api

/** One open popup instance. */
interface PopupHandle {
    val id: String

    /** `false` once the popup closed, expired or was pushed out of its stack. */
    val isOpen: Boolean

    /** Merges [variables] into the popup's variables and re-renders it on the next tick. No-op when closed. */
    fun update(variables: Map<String, Any?>)

    fun update(vararg variables: Pair<String, Any?>) = update(variables.toMap())

    fun close()
}
```

`PlaceholderRegistry.kt`:

```kotlin
package dev.slne.surf.hud.api

import java.util.*
import kotlin.time.Duration

/**
 * Supplies the value of a placeholder for a viewer. [argument] is the part after the colon in
 * `[name:argument]`, or `null`. Return a `Number`, `String`, `Boolean`, Adventure `Component` or `null`.
 * Called on the viewer's thread.
 */
fun interface PlaceholderResolver {
    fun resolve(viewer: UUID, argument: String?): Any?
}

/** Registry of placeholders usable as `[name]` in HUD definitions. Names are case-insensitive. */
interface PlaceholderRegistry {
    /**
     * Registers [resolver] under [name], replacing an existing registration. With an [interval] the value is
     * cached per player for that long; without one it is resolved at most once per HUD update.
     */
    fun register(name: String, interval: Duration? = null, resolver: PlaceholderResolver)

    fun unregister(name: String)

    fun isRegistered(name: String): Boolean
}
```

`ReloadResult.kt`:

```kotlin
package dev.slne.surf.hud.api

/** Outcome of [SurfHud.reload]: the errors (empty on success) and the SHA-1 of the generated pack. */
data class ReloadResult(val errors: List<String>, val packHash: String?) {
    val success: Boolean get() = errors.isEmpty()
}
```

- [ ] **Step 3: Add creative to core**

`surf-hud-core/build.gradle.kts`:

```kotlin
import dev.slne.surf.api.gradle.util.slneReleases

plugins {
    id("dev.slne.surf.api.gradle.core")
}

dependencies {
    api(projects.surfHudApi)
    // Adventure and Gson come from surf-api / the server; creative must not bring its own copies.
    implementation("team.unnamed:creative-api:1.7.3") {
        exclude(group = "net.kyori")
        exclude(group = "com.google.code.gson")
    }
    implementation("team.unnamed:creative-serializer-minecraft:1.7.3") {
        exclude(group = "net.kyori")
        exclude(group = "com.google.code.gson")
    }
}

publishing {
    repositories {
        slneReleases()
    }
}
```

- [ ] **Step 4: Stub the Paper modules so the build compiles**

`surf-hud-paper/src/main/kotlin/dev/slne/surf/hud/paper/PaperMain.kt`:

```kotlin
package dev.slne.surf.hud.paper

import org.bukkit.plugin.java.JavaPlugin

val plugin get() = JavaPlugin.getPlugin(PaperMain::class.java)

class PaperMain : JavaPlugin()
```

Delete `PaperSurfHud.kt`, `PaperHudPlatform.kt` and `listener/HudConnectionListener.kt` (`git rm`). They are recreated in Task 13.

`surf-hud-paper-example/src/main/kotlin/dev/slne/surf/hud/example/paper/PaperExampleMain.kt`:

```kotlin
package dev.slne.surf.hud.example.paper

import org.bukkit.plugin.java.JavaPlugin

val plugin get() = JavaPlugin.getPlugin(PaperExampleMain::class.java)

class PaperExampleMain : JavaPlugin()
```

- [ ] **Step 5: Write a smoke test that proves creative works with Adventure 5**

`surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/CreativeSmokeTest.kt`:

```kotlin
package dev.slne.surf.hud.core

import net.kyori.adventure.key.Key
import org.junit.jupiter.api.io.TempDir
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.font.Font
import team.unnamed.creative.font.FontProvider
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertTrue

class CreativeSmokeTest {
    @Test
    fun `creative writes a font and an unknown file`(@TempDir dir: Path) {
        val pack = ResourcePack.resourcePack()
        pack.unknownFile("pack.mcmeta", Writable.stringUtf8("""{"pack":{"description":"x","pack_format":46}}"""))
        pack.font(Font.font(Key.key("surf", "hud/test"), listOf(FontProvider.space(mapOf("" to 1)))))
        MinecraftResourcePackWriter.minecraft().writeToDirectory(dir.toFile(), pack)

        assertTrue(dir.resolve("pack.mcmeta").exists())
        assertTrue(dir.resolve("assets/surf/font/hud/test.json").readText().contains("space"))
    }
}
```

- [ ] **Step 6: Build and run the smoke test**

Run: `./gradlew build -x test && ./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.CreativeSmokeTest"`
Expected: BUILD SUCCESSFUL, 1 test passed. If the test fails with `NoSuchMethodError`/`NoClassDefFoundError` in `net.kyori`, creative 1.7.3 is not binary compatible with Adventure 5. In that case stop and report it; the fallback (writing the pack files with Gson directly) is a design change the user must approve.

- [ ] **Step 7: Commit**

```bash
git add -A surf-hud-api surf-hud-core surf-hud-paper surf-hud-paper-example
git commit -m "refactor!: replace line HUD API with config-driven HUD API" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: Expressions and text templates

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/expression/{PlaceholderRef.kt,Value.kt,Expr.kt,ExpressionParser.kt,TextTemplate.kt}`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/expression/{ExpressionTest.kt,TextTemplateTest.kt}`

**Interfaces:**
- Produces:
  - `data class PlaceholderRef(val name: String, val argument: String? = null)`
  - `sealed interface Value { Num(Double), Str(String), Bool(Boolean), Comp(Component), Missing; fun number(): Double?; fun text(): String; companion fun of(Any?): Value }`
  - `fun interface Scope { fun resolve(ref: PlaceholderRef): Value; fun warn(expression: Expr, message: String) {} }`
  - `sealed interface Expr { fun evaluate(scope: Scope): Value; fun placeholders(): Set<PlaceholderRef> }`, plus `fun Expr.isTrue(scope: Scope): Boolean` and `fun Expr.number(scope: Scope): Double?`
  - `object ExpressionParser { fun parse(source: String): Expr }` throws `ExpressionException(message, position)`
  - `class TextTemplate(val segments: List<Segment>) { fun placeholders(): Set<PlaceholderRef>; companion fun parse(source: String): TextTemplate }` with `Segment.Literal(miniMessage)`, `Segment.Placeholder(ref)`, `Segment.Expression(expression, format: String?)`

- [ ] **Step 1: Write the failing tests**

`ExpressionTest.kt`:

```kotlin
package dev.slne.surf.hud.core.expression

import kotlin.test.*

class ExpressionTest {
    private val values = mapOf(
        PlaceholderRef("health") to Value.Num(15.0),
        PlaceholderRef("max_health") to Value.Num(20.0),
        PlaceholderRef("name") to Value.Str("Notch"),
        PlaceholderRef("papi", "balance") to Value.Str("1500"),
        PlaceholderRef("flying") to Value.Bool(true),
    )
    private val warnings = mutableListOf<String>()
    private val scope = object : Scope {
        override fun resolve(ref: PlaceholderRef) = values[ref] ?: Value.Missing
        override fun warn(expression: Expr, message: String) { warnings += message }
    }

    private fun eval(source: String) = ExpressionParser.parse(source).evaluate(scope)

    @Test fun `arithmetic respects precedence`() = assertEquals(Value.Num(14.0), eval("2 + 3 * 4"))
    @Test fun `parentheses group`() = assertEquals(Value.Num(20.0), eval("(2 + 3) * 4"))
    @Test fun `placeholders resolve`() = assertEquals(Value.Num(0.75), eval("[health] / [max_health]"))
    @Test fun `placeholder arguments parse`() = assertEquals(Value.Num(1501.0), eval("[papi:balance] + 1"))
    @Test fun `numeric strings compare as numbers`() = assertEquals(Value.Bool(true), eval("[papi:balance] > 999"))
    @Test fun `plus concatenates text`() = assertEquals(Value.Str("Hi Notch"), eval("'Hi ' + [name]"))
    @Test fun `comparison and logic`() = assertEquals(Value.Bool(true), eval("[health] < [max_health] && ![flying] || true"))
    @Test fun `functions`() {
        assertEquals(Value.Num(3.0), eval("min(5, 3, 4)"))
        assertEquals(Value.Num(5.0), eval("max(5, 3)"))
        assertEquals(Value.Num(2.0), eval("round(1.6)"))
        assertEquals(Value.Num(1.0), eval("floor(1.6)"))
        assertEquals(Value.Num(2.0), eval("ceil(1.2)"))
        assertEquals(Value.Num(10.0), eval("clamp(42, 0, 10)"))
    }
    @Test fun `division by zero is zero`() = assertEquals(Value.Num(0.0), eval("5 / 0"))
    @Test fun `missing placeholders propagate`() {
        assertEquals(Value.Missing, eval("[unknown] + 1"))
        assertEquals(Value.Bool(false), eval("[unknown] > 1"))
    }
    @Test fun `type mismatch in comparison is false and warns`() {
        assertEquals(Value.Bool(false), eval("[name] > 3"))
        assertEquals(1, warnings.size)
    }
    @Test fun `non boolean condition is false`() {
        assertFalse(ExpressionParser.parse("[health]").isTrue(scope))
    }
    @Test fun `collects placeholders`() {
        assertEquals(
            setOf(PlaceholderRef("health"), PlaceholderRef("papi", "balance")),
            ExpressionParser.parse("max([health], [papi:balance])").placeholders(),
        )
    }
    @Test fun `unknown function is an error`() {
        val error = assertFailsWith<ExpressionException> { ExpressionParser.parse("mx(1, 2)") }
        assertContains(error.message!!, "unknown function 'mx'")
    }
    @Test fun `wrong arity is an error`() {
        assertFailsWith<ExpressionException> { ExpressionParser.parse("clamp(1, 2)") }
    }
    @Test fun `trailing input is an error`() {
        assertFailsWith<ExpressionException> { ExpressionParser.parse("1 2") }
    }
    @Test fun `unclosed placeholder is an error`() {
        assertFailsWith<ExpressionException> { ExpressionParser.parse("[health") }
    }
}
```

`TextTemplateTest.kt`:

```kotlin
package dev.slne.surf.hud.core.expression

import dev.slne.surf.hud.core.expression.TextTemplate.Segment
import kotlin.test.*

class TextTemplateTest {
    @Test fun `splits literals placeholders and expressions`() {
        val template = TextTemplate.parse("<gold>[money]</gold> {[health] / 2 | 0.0}%")
        val segments = template.segments
        assertEquals(Segment.Literal("<gold>"), segments[0])
        assertEquals(Segment.Placeholder(PlaceholderRef("money")), segments[1])
        assertEquals(Segment.Literal("</gold> "), segments[2])
        val expression = segments[3] as Segment.Expression
        assertEquals("0.0", expression.format)
        assertEquals(Segment.Literal("%"), segments[4])
    }
    @Test fun `double bar is an operator not a format`() {
        val expression = TextTemplate.parse("{true || false}").segments.single() as Segment.Expression
        assertNull(expression.format)
    }
    @Test fun `escaped brackets stay literal`() {
        assertEquals(listOf(Segment.Literal("[x] {y}")), TextTemplate.parse("\\[x] \\{y}").segments)
    }
    @Test fun `bracket that is not a placeholder stays literal`() {
        assertEquals(listOf(Segment.Literal("a [b c")), TextTemplate.parse("a [b c").segments)
    }
    @Test fun `unclosed expression is an error`() {
        assertFailsWith<ExpressionException> { TextTemplate.parse("{1 + 2") }
    }
    @Test fun `invalid format is an error`() {
        assertFailsWith<ExpressionException> { TextTemplate.parse("{1 | 0.0.0'}") }
    }
    @Test fun `collects placeholders from expressions too`() {
        assertEquals(
            setOf(PlaceholderRef("a"), PlaceholderRef("b")),
            TextTemplate.parse("[a] {[b] + 1}").placeholders(),
        )
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.expression.*"`
Expected: FAIL, compilation errors (`Unresolved reference: ExpressionParser`).

- [ ] **Step 3: Implement**

`PlaceholderRef.kt`:

```kotlin
package dev.slne.surf.hud.core.expression

/** A placeholder reference `[name]` or `[name:argument]`; [name] is stored lower case. */
data class PlaceholderRef(val name: String, val argument: String? = null) {
    override fun toString() = if (argument == null) "[$name]" else "[$name:$argument]"

    companion object {
        /** Characters allowed in a placeholder name. */
        val NAME = Regex("[A-Za-z0-9_.-]+")
    }
}
```

`Value.kt`:

```kotlin
package dev.slne.surf.hud.core.expression

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

/** A value in a HUD expression or text. [Missing] stands for an unknown placeholder and renders as `?`. */
sealed interface Value {
    data class Num(val value: Double) : Value
    data class Str(val value: String) : Value
    data class Bool(val value: Boolean) : Value
    data class Comp(val value: Component) : Value
    data object Missing : Value

    /** The numeric view: numbers, and strings that parse as numbers (e.g. PlaceholderAPI output). */
    fun number(): Double? = when (this) {
        is Num -> value
        is Str -> value.trim().toDoubleOrNull()
        else -> null
    }

    /** True for values that `+` concatenates instead of adding. */
    fun isTextual(): Boolean = when (this) {
        is Str -> number() == null
        is Comp, is Bool -> true
        else -> false
    }

    fun text(): String = when (this) {
        is Num -> formatNumber(value)
        is Str -> value
        is Bool -> value.toString()
        is Comp -> PlainTextComponentSerializer.plainText().serialize(value)
        Missing -> "?"
    }

    companion object {
        private val DEFAULT_FORMAT: ThreadLocal<DecimalFormat> =
            ThreadLocal.withInitial { DecimalFormat("0.##", DecimalFormatSymbols(Locale.US)) }

        fun formatNumber(value: Double): String = DEFAULT_FORMAT.get().format(value)

        /** Converts a placeholder or variable value; `null` becomes an empty string. */
        fun of(value: Any?): Value = when (value) {
            null -> Str("")
            is Value -> value
            is Number -> Num(value.toDouble())
            is Boolean -> Bool(value)
            is Component -> Comp(value)
            else -> Str(value.toString())
        }
    }
}
```

`Expr.kt`:

```kotlin
package dev.slne.surf.hud.core.expression

/** Resolves placeholders while an expression is evaluated and receives evaluation warnings. */
fun interface Scope {
    fun resolve(ref: PlaceholderRef): Value
    fun warn(expression: Expr, message: String) {}
}

/** A parsed expression. Evaluation never throws; type errors yield [Value.Missing] or `false`. */
sealed interface Expr {
    fun evaluate(scope: Scope): Value
    fun placeholders(): Set<PlaceholderRef>

    data class Literal(val value: Value) : Expr {
        override fun evaluate(scope: Scope) = value
        override fun placeholders() = emptySet<PlaceholderRef>()
    }

    data class Placeholder(val ref: PlaceholderRef) : Expr {
        override fun evaluate(scope: Scope) = scope.resolve(ref)
        override fun placeholders() = setOf(ref)
    }

    data class Not(val operand: Expr) : Expr {
        override fun evaluate(scope: Scope): Value = when (val value = operand.evaluate(scope)) {
            is Value.Bool -> Value.Bool(!value.value)
            Value.Missing -> Value.Bool(false)
            else -> Value.Bool(false).also { scope.warn(this, "'!' needs a boolean") }
        }
        override fun placeholders() = operand.placeholders()
    }

    data class Negate(val operand: Expr) : Expr {
        override fun evaluate(scope: Scope): Value =
            operand.evaluate(scope).number()?.let { Value.Num(-it) } ?: Value.Missing
        override fun placeholders() = operand.placeholders()
    }

    data class Binary(val op: BinaryOp, val left: Expr, val right: Expr) : Expr {
        override fun evaluate(scope: Scope): Value = when (op) {
            BinaryOp.AND -> Value.Bool(left.isTrue(scope) && right.isTrue(scope))
            BinaryOp.OR -> Value.Bool(left.isTrue(scope) || right.isTrue(scope))
            else -> op.apply(this, left.evaluate(scope), right.evaluate(scope), scope)
        }
        override fun placeholders() = left.placeholders() + right.placeholders()
    }

    data class Call(val function: ExprFunction, val arguments: List<Expr>) : Expr {
        override fun evaluate(scope: Scope): Value {
            val numbers = arguments.map { it.evaluate(scope).number() ?: return Value.Missing }
            return Value.Num(function.apply(numbers))
        }
        override fun placeholders() = arguments.flatMapTo(HashSet()) { it.placeholders() }
    }
}

enum class BinaryOp(val symbol: String) {
    PLUS("+"), MINUS("-"), TIMES("*"), DIV("/"),
    EQ("=="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">="),
    AND("&&"), OR("||");

    fun apply(expression: Expr, left: Value, right: Value, scope: Scope): Value = when (this) {
        PLUS, MINUS, TIMES, DIV -> arithmetic(expression, left, right, scope)
        EQ, NE -> equality(left, right)
        LT, LE, GT, GE -> comparison(expression, left, right, scope)
        AND, OR -> error("handled by Expr.Binary")
    }

    private fun arithmetic(expression: Expr, left: Value, right: Value, scope: Scope): Value {
        if (left == Value.Missing || right == Value.Missing) return Value.Missing
        if (this == PLUS && (left.isTextual() || right.isTextual())) return Value.Str(left.text() + right.text())
        val a = left.number()
        val b = right.number()
        if (a == null || b == null) {
            scope.warn(expression, "'$symbol' needs numbers")
            return Value.Missing
        }
        return Value.Num(
            when (this) {
                PLUS -> a + b
                MINUS -> a - b
                TIMES -> a * b
                else -> if (b == 0.0) 0.0 else a / b
            }
        )
    }

    private fun equality(left: Value, right: Value): Value {
        if (left == Value.Missing || right == Value.Missing) return Value.Bool(false)
        val a = left.number()
        val b = right.number()
        val equal = if (a != null && b != null) a == b else left.text() == right.text()
        return Value.Bool(if (this == EQ) equal else !equal)
    }

    private fun comparison(expression: Expr, left: Value, right: Value, scope: Scope): Value {
        if (left == Value.Missing || right == Value.Missing) return Value.Bool(false)
        val a = left.number()
        val b = right.number()
        if (a == null || b == null) {
            scope.warn(expression, "'$symbol' needs numbers")
            return Value.Bool(false)
        }
        return Value.Bool(
            when (this) {
                LT -> a < b
                LE -> a <= b
                GT -> a > b
                else -> a >= b
            }
        )
    }
}

/** Functions callable in expressions; [arity] is the allowed argument count. */
enum class ExprFunction(val arity: IntRange, val apply: (List<Double>) -> Double) {
    MIN(1..Int.MAX_VALUE, { it.min() }),
    MAX(1..Int.MAX_VALUE, { it.max() }),
    ROUND(1..1, { Math.round(it[0]).toDouble() }),
    FLOOR(1..1, { kotlin.math.floor(it[0]) }),
    CEIL(1..1, { kotlin.math.ceil(it[0]) }),
    CLAMP(3..3, { it[0].coerceIn(it[1], maxOf(it[1], it[2])) });

    companion object {
        fun byName(name: String): ExprFunction? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** `true` only for a boolean `true`; anything else (including [Value.Missing]) is `false`. */
fun Expr.isTrue(scope: Scope): Boolean = when (val value = evaluate(scope)) {
    is Value.Bool -> value.value
    Value.Missing -> false
    else -> false.also { scope.warn(this, "condition is not a boolean") }
}

/** The numeric result, or `null` when the result is not a number. */
fun Expr.number(scope: Scope): Double? = evaluate(scope).number()
```

`ExpressionParser.kt`:

```kotlin
package dev.slne.surf.hud.core.expression

/** Thrown for invalid expression or template syntax; [position] is the character index in the source. */
class ExpressionException(message: String, val position: Int) : IllegalArgumentException("$message at position $position")

/**
 * Recursive-descent parser for HUD expressions:
 * `||` < `&&` < `== !=` < `< <= > >=` < `+ -` < `* /` < unary `! -` < literals, `[placeholders]`, calls, `( )`.
 */
object ExpressionParser {
    fun parse(source: String): Expr {
        val parser = Parser(source)
        val expression = parser.or()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw ExpressionException("unexpected '${source[parser.pos]}'", parser.pos)
        return expression
    }

    private class Parser(val src: String) {
        var pos = 0

        fun atEnd() = pos >= src.length
        fun skipWhitespace() { while (!atEnd() && src[pos].isWhitespace()) pos++ }

        private fun accept(token: String): Boolean {
            skipWhitespace()
            if (!src.startsWith(token, pos)) return false
            // "|" and "&" alone are not operators; "<" must not swallow "<=".
            pos += token.length
            return true
        }

        fun or(): Expr {
            var left = and()
            while (accept("||")) left = Expr.Binary(BinaryOp.OR, left, and())
            return left
        }

        private fun and(): Expr {
            var left = equality()
            while (accept("&&")) left = Expr.Binary(BinaryOp.AND, left, equality())
            return left
        }

        private fun equality(): Expr {
            var left = comparison()
            while (true) {
                left = when {
                    accept("==") -> Expr.Binary(BinaryOp.EQ, left, comparison())
                    accept("!=") -> Expr.Binary(BinaryOp.NE, left, comparison())
                    else -> return left
                }
            }
        }

        private fun comparison(): Expr {
            var left = additive()
            while (true) {
                left = when {
                    accept("<=") -> Expr.Binary(BinaryOp.LE, left, additive())
                    accept(">=") -> Expr.Binary(BinaryOp.GE, left, additive())
                    accept("<") -> Expr.Binary(BinaryOp.LT, left, additive())
                    accept(">") -> Expr.Binary(BinaryOp.GT, left, additive())
                    else -> return left
                }
            }
        }

        private fun additive(): Expr {
            var left = multiplicative()
            while (true) {
                left = when {
                    accept("+") -> Expr.Binary(BinaryOp.PLUS, left, multiplicative())
                    accept("-") -> Expr.Binary(BinaryOp.MINUS, left, multiplicative())
                    else -> return left
                }
            }
        }

        private fun multiplicative(): Expr {
            var left = unary()
            while (true) {
                left = when {
                    accept("*") -> Expr.Binary(BinaryOp.TIMES, left, unary())
                    accept("/") -> Expr.Binary(BinaryOp.DIV, left, unary())
                    else -> return left
                }
            }
        }

        private fun unary(): Expr = when {
            peekNot() -> { pos++; Expr.Not(unary()) }
            accept("-") -> Expr.Negate(unary())
            else -> primary()
        }

        private fun peekNot(): Boolean {
            skipWhitespace()
            return !atEnd() && src[pos] == '!' && !src.startsWith("!=", pos)
        }

        private fun primary(): Expr {
            skipWhitespace()
            if (atEnd()) throw ExpressionException("unexpected end of expression", pos)
            val c = src[pos]
            return when {
                c == '(' -> {
                    pos++
                    val inner = or()
                    if (!accept(")")) throw ExpressionException("missing ')'", pos)
                    inner
                }
                c == '[' -> placeholder()
                c == '\'' || c == '"' -> string(c)
                c.isDigit() || c == '.' -> number()
                c.isLetter() -> identifier()
                else -> throw ExpressionException("unexpected '$c'", pos)
            }
        }

        private fun placeholder(): Expr {
            val start = pos
            val end = src.indexOf(']', pos)
            if (end < 0) throw ExpressionException("unclosed placeholder", start)
            val body = src.substring(pos + 1, end)
            val name = body.substringBefore(':')
            if (!PlaceholderRef.NAME.matches(name)) throw ExpressionException("invalid placeholder name '$name'", start)
            val argument = if (':' in body) body.substringAfter(':') else null
            pos = end + 1
            return Expr.Placeholder(PlaceholderRef(name.lowercase(), argument))
        }

        private fun string(quote: Char): Expr {
            val start = pos
            val end = src.indexOf(quote, pos + 1)
            if (end < 0) throw ExpressionException("unclosed string", start)
            pos = end + 1
            return Expr.Literal(Value.Str(src.substring(start + 1, end)))
        }

        private fun number(): Expr {
            val start = pos
            while (!atEnd() && (src[pos].isDigit() || src[pos] == '.')) pos++
            val value = src.substring(start, pos).toDoubleOrNull()
                ?: throw ExpressionException("invalid number '${src.substring(start, pos)}'", start)
            return Expr.Literal(Value.Num(value))
        }

        private fun identifier(): Expr {
            val start = pos
            while (!atEnd() && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
            val name = src.substring(start, pos)
            when (name) {
                "true" -> return Expr.Literal(Value.Bool(true))
                "false" -> return Expr.Literal(Value.Bool(false))
            }
            val function = ExprFunction.byName(name) ?: throw ExpressionException("unknown function '$name'", start)
            if (!accept("(")) throw ExpressionException("expected '(' after '$name'", pos)
            val arguments = mutableListOf<Expr>()
            if (!accept(")")) {
                do arguments += or() while (accept(","))
                if (!accept(")")) throw ExpressionException("missing ')' after arguments of '$name'", pos)
            }
            if (arguments.size !in function.arity) {
                throw ExpressionException("'$name' takes ${describe(function.arity)} arguments, got ${arguments.size}", start)
            }
            return Expr.Call(function, arguments)
        }

        private fun describe(range: IntRange) =
            if (range.last == Int.MAX_VALUE) "at least ${range.first}" else if (range.first == range.last) "${range.first}" else "$range"
    }
}
```

`TextTemplate.kt`:

```kotlin
package dev.slne.surf.hud.core.expression

import java.text.DecimalFormat

/**
 * A HUD text: MiniMessage with `[placeholder]` and `{expression}` or `{expression | DecimalFormat pattern}`.
 * `\[` and `\{` insert the bracket literally. Literal segments are MiniMessage source.
 */
class TextTemplate(val segments: List<Segment>) {
    sealed interface Segment {
        data class Literal(val miniMessage: String) : Segment
        data class Placeholder(val ref: PlaceholderRef) : Segment
        data class Expression(val expression: Expr, val format: String?) : Segment
    }

    fun placeholders(): Set<PlaceholderRef> = segments.flatMapTo(HashSet()) {
        when (it) {
            is Segment.Literal -> emptySet()
            is Segment.Placeholder -> setOf(it.ref)
            is Segment.Expression -> it.expression.placeholders()
        }
    }

    companion object {
        private val PLACEHOLDER = Regex("""\[([A-Za-z0-9_.-]+)(?::([^\]]*))?]""")

        fun parse(source: String): TextTemplate {
            val segments = mutableListOf<Segment>()
            val literal = StringBuilder()
            fun flush() {
                if (literal.isNotEmpty()) segments += Segment.Literal(literal.toString())
                literal.clear()
            }
            var i = 0
            while (i < source.length) {
                val c = source[i]
                when {
                    c == '\\' && i + 1 < source.length && source[i + 1] in "[{" -> {
                        literal.append(source[i + 1]); i += 2
                    }
                    c == '[' -> {
                        val match = PLACEHOLDER.matchAt(source, i)
                        if (match == null) {
                            literal.append(c); i++
                        } else {
                            flush()
                            segments += Segment.Placeholder(PlaceholderRef(match.groupValues[1].lowercase(), match.groups[2]?.value))
                            i = match.range.last + 1
                        }
                    }
                    c == '{' -> {
                        flush()
                        val end = closingBrace(source, i)
                        segments += expression(source.substring(i + 1, end), i + 1)
                        i = end + 1
                    }
                    else -> { literal.append(c); i++ }
                }
            }
            flush()
            return TextTemplate(segments)
        }

        private fun closingBrace(source: String, open: Int): Int {
            var quote: Char? = null
            for (i in open + 1 until source.length) {
                val c = source[i]
                when {
                    quote != null -> if (c == quote) quote = null
                    c == '\'' || c == '"' -> quote = c
                    c == '}' -> return i
                }
            }
            throw ExpressionException("unclosed '{'", open)
        }

        /** Splits off the format at the last single `|` outside quotes (`||` is the or operator). */
        private fun expression(body: String, offset: Int): Segment.Expression {
            var separator = -1
            var quote: Char? = null
            var i = 0
            while (i < body.length) {
                val c = body[i]
                when {
                    quote != null -> if (c == quote) quote = null
                    c == '\'' || c == '"' -> quote = c
                    c == '|' && i + 1 < body.length && body[i + 1] == '|' -> i++
                    c == '|' -> separator = i
                }
                i++
            }
            val source = if (separator < 0) body else body.substring(0, separator)
            val format = if (separator < 0) null else body.substring(separator + 1).trim()
            if (format != null) {
                try {
                    DecimalFormat(format)
                } catch (e: IllegalArgumentException) {
                    throw ExpressionException("invalid number format '$format'", offset + separator)
                }
            }
            val expression = try {
                ExpressionParser.parse(source)
            } catch (e: ExpressionException) {
                throw ExpressionException(e.message!!.substringBeforeLast(" at position"), offset + e.position)
            }
            return Segment.Expression(expression, format)
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.expression.*"`
Expected: PASS (all tests in `ExpressionTest` and `TextTemplateTest`).

- [ ] **Step 5: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): add HUD expressions and text templates" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: Configuration classes and the definition loader

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/config/{HudSettings.kt,Definitions.kt,PlacementDefinitionSerializer.kt,DefinitionReader.kt,DefinitionLoader.kt,Durations.kt}`
- Create (test support, reused by later tasks): `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/ConfigurateTestReader.kt`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/config/{DefinitionLoaderTest.kt,DurationsTest.kt}`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `HudSettings(namespace, minecraftVersion, bossbarColor, updateInterval, placeholderIntervals: PlaceholderIntervals, heads: HeadSettings, output: OutputSettings)`; `PlaceholderIntervals(papi: Int, mini: Int) { fun millis(): Map<String, Long> }`; `HeadSettings(awaitLoaded, timeout: String, cacheTtl: String, fallback: String)`; `OutputSettings(zip: String, folder: String)`
  - `ImageDefinition(type, file, size, rows)`, `TextDefinition(type, height)`, `ElementDefinition(type, id, x, y, conditions, font, text, align, shadow, image, width, color, value, direction, player, brightness)`, `LayoutDefinition(align, conditions, elements)`, `PlacementDefinition(layout, anchor, x, y, conditions)`, `HudDefinition(default, conditions, layouts)`, `PopupDefinition(anchor, x, y, duration, unique, maxStack, stackOffset, layouts)`. All fields nullable with `null` defaults except `PlacementDefinition.layout`.
  - `interface DefinitionReader { fun <T : Any> read(type: Class<T>, folder: Path, fileName: String): T }`, `class SurfConfigDefinitionReader : DefinitionReader`
  - `data class Sourced<T>(val id: String, val file: String, val value: T)`
  - `data class DefinitionSet(settings, images, texts, layouts, huds, popups: Map<String, Sourced<…>>, errors: List<String>)`
  - `class DefinitionLoader(reader) { fun load(root: Path): DefinitionSet }`
  - `object Durations { fun parse(text: String): Duration }` (`500ms`, `5s`, `2m`, `1h`, `1d`; throws `IllegalArgumentException`)
  - Test: `object ConfigurateTestReader : DefinitionReader`

- [ ] **Step 1: Write the failing tests**

`ConfigurateTestReader.kt` (test sources, not a test class):

```kotlin
package dev.slne.surf.hud.core

import dev.slne.surf.hud.core.config.DefinitionReader
import dev.slne.surf.hud.core.config.PlacementDefinition
import dev.slne.surf.hud.core.config.PlacementDefinitionSerializer
import org.spongepowered.configurate.kotlin.objectMapperFactory
import org.spongepowered.configurate.yaml.YamlConfigurationLoader
import java.nio.file.Path

/** Reads definition files with plain Configurate, since surfConfigApi needs a running surf-api service. */
object ConfigurateTestReader : DefinitionReader {
    override fun <T : Any> read(type: Class<T>, folder: Path, fileName: String): T {
        val loader = YamlConfigurationLoader.builder()
            .path(folder.resolve(fileName))
            .defaultOptions { options ->
                options.serializers { serializers ->
                    serializers.registerAnnotatedObjects(objectMapperFactory())
                    serializers.register(PlacementDefinition::class.java, PlacementDefinitionSerializer)
                }
            }
            .build()
        return loader.load().get(type) ?: error("$fileName did not deserialize")
    }
}
```

`DefinitionLoaderTest.kt`:

```kotlin
package dev.slne.surf.hud.core.config

import dev.slne.surf.hud.core.ConfigurateTestReader
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.*

class DefinitionLoaderTest {
    @TempDir lateinit var root: Path

    private fun file(path: String, text: String) {
        root.resolve(path).also { it.parent.createDirectories() }.writeText(text.trimIndent())
    }

    @Test fun `loads every folder with several entries per file`() {
        file("images/a.yml", """
            coin: { type: single, file: icons/coin.png }
            panel: { type: strip, file: strips/panel.png }
        """)
        file("layouts/sub/b.yml", """
            money:
              align: right
              elements:
                - { type: text, x: 3, text: "[money]" }
        """)
        file("huds/main.yml", """
            main:
              default: true
              layouts:
                - { layout: money, anchor: bottom_right, x: -4, y: -17 }
        """)
        file("popups/vote.yml", """
            vote:
              anchor: center
              max-stack: 3
              stack-offset: [0, 12]
              layouts:
                - money
                - { layout: money, y: 10 }
        """)
        val set = DefinitionLoader(ConfigurateTestReader).load(root)

        assertEquals(emptyList(), set.errors)
        assertEquals(setOf("coin", "panel"), set.images.keys)
        assertEquals("images/a.yml", set.images.getValue("coin").file)
        assertEquals("layouts/sub/b.yml", set.layouts.getValue("money").file)
        assertEquals("[money]", set.layouts.getValue("money").value.elements!!.single().text)
        assertEquals(PlacementDefinition("money", "bottom_right", -4, -17), set.huds.getValue("main").value.layouts!!.single())
        val vote = set.popups.getValue("vote").value
        assertEquals(3, vote.maxStack)
        assertEquals(listOf(0, 12), vote.stackOffset)
        assertEquals(listOf(PlacementDefinition("money"), PlacementDefinition("money", y = 10)), vote.layouts)
    }

    @Test fun `numbers are read into expression strings`() {
        file("layouts/a.yml", """
            bar:
              elements:
                - { type: image, image: panel, width: 60 }
        """)
        assertEquals("60", DefinitionLoader(ConfigurateTestReader).load(root).layouts.getValue("bar").value.elements!!.single().width)
    }

    @Test fun `duplicate ids are reported with both files`() {
        file("texts/a.yml", "default: { type: ascii }")
        file("texts/b.yml", "default: { type: ascii, height: 16 }")
        val error = DefinitionLoader(ConfigurateTestReader).load(root).errors.single()
        assertContains(error, "texts/b.yml > default")
        assertContains(error, "texts/a.yml")
    }

    @Test fun `unreadable files are reported and the rest still loads`() {
        file("texts/broken.yml", "default: [ unclosed")
        file("texts/ok.yml", "title: { type: ascii }")
        val set = DefinitionLoader(ConfigurateTestReader).load(root)
        assertEquals(setOf("title"), set.texts.keys)
        assertContains(set.errors.single(), "texts/broken.yml")
    }

    @Test fun `missing config yml gives defaults`() {
        val settings = DefinitionLoader(ConfigurateTestReader).load(root).settings
        assertEquals("surf", settings.namespace)
        assertEquals(2, settings.updateInterval)
        assertEquals(mapOf("papi" to 1000L, "mini" to 1000L), settings.placeholderIntervals.millis())
    }

    @Test fun `config yml keys are dashed`() {
        file("config.yml", """
            minecraft-version: "26.4"
            update-interval: 5
            heads: { await-loaded: false, cache-ttl: 2h }
        """)
        val settings = DefinitionLoader(ConfigurateTestReader).load(root).settings
        assertEquals("26.4", settings.minecraftVersion)
        assertEquals(5, settings.updateInterval)
        assertFalse(settings.heads.awaitLoaded)
        assertEquals("2h", settings.heads.cacheTtl)
        assertEquals("5s", settings.heads.timeout)
    }
}
```

`DurationsTest.kt`:

```kotlin
package dev.slne.surf.hud.core.config

import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class DurationsTest {
    @Test fun parses() {
        assertEquals(500.milliseconds, Durations.parse("500ms"))
        assertEquals(5.seconds, Durations.parse("5s"))
        assertEquals(1.days, Durations.parse(" 1d "))
    }
    @Test fun rejects() {
        assertFailsWith<IllegalArgumentException> { Durations.parse("5 parsecs") }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.config.*"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`HudSettings.kt`:

```kotlin
package dev.slne.surf.hud.core.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

/** `config.yml`: global settings of the HUD engine and its generated pack. */
@ConfigSerializable
data class HudSettings(
    /** Namespace of generated fonts, textures and the shader include; must not clash with the main pack. */
    val namespace: String = "surf",
    /** Client version whose jar provides the vanilla shader, ascii font and pack format. */
    @Setting("minecraft-version") val minecraftVersion: String = "26.3",
    /** Bossbar color whose sprites are blanked; the HUD bossbar uses it. */
    @Setting("bossbar-color") val bossbarColor: String = "pink",
    /** Ticks between HUD updates. */
    @Setting("update-interval") val updateInterval: Int = 2,
    @Setting("placeholder-intervals") val placeholderIntervals: PlaceholderIntervals = PlaceholderIntervals(),
    val heads: HeadSettings = HeadSettings(),
    val output: OutputSettings = OutputSettings(),
)

/** Cache time in ticks of the PlaceholderAPI (`papi`) and MiniPlaceholders (`mini`) bridges. */
@ConfigSerializable
data class PlaceholderIntervals(val papi: Int = 20, val mini: Int = 20) {
    fun millis(): Map<String, Long> = mapOf("papi" to papi * 50L, "mini" to mini * 50L)
}

@ConfigSerializable
data class HeadSettings(
    /** Wait for heads to load before showing a HUD or popup. */
    @Setting("await-loaded") val awaitLoaded: Boolean = true,
    val timeout: String = "5s",
    @Setting("cache-ttl") val cacheTtl: String = "1d",
    /** Fallback head image, relative to `assets/`. A generated face is used when the file is missing. */
    val fallback: String = "heads/fallback.png",
)

/** Output paths of the generated pack, relative to the plugin folder. */
@ConfigSerializable
data class OutputSettings(val zip: String = "build/surf-hud.zip", val folder: String = "build/pack")
```

`Definitions.kt`:

```kotlin
package dev.slne.surf.hud.core.config

import org.spongepowered.configurate.objectmapping.ConfigSerializable
import org.spongepowered.configurate.objectmapping.meta.Setting

// Raw YAML shapes. Every field is optional so files are written back without added defaults;
// the compiler applies defaults and reports missing or invalid values with their path.

/** `images/`: `single` icon, `strip` (3 px wide: left, middle, right column) or `pixel` set. */
@ConfigSerializable
data class ImageDefinition(
    val type: String? = null,
    val file: String? = null,
    val size: Int? = null,
    val rows: Int? = null,
)

/** `texts/`: an `ascii` font at [height] (default 8). */
@ConfigSerializable
data class TextDefinition(val type: String? = null, val height: Int? = null)

/** One layout element; which fields apply depends on [type] (`text`, `image`, `bar`, `head`). */
@ConfigSerializable
data class ElementDefinition(
    val type: String? = null,
    val id: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val conditions: List<String>? = null,
    val font: String? = null,
    val text: String? = null,
    val align: String? = null,
    val shadow: Boolean? = null,
    val image: String? = null,
    val width: String? = null,
    val color: String? = null,
    val value: String? = null,
    val direction: String? = null,
    val player: String? = null,
    val brightness: String? = null,
)

/** `layouts/`: a reusable group of elements. */
@ConfigSerializable
data class LayoutDefinition(
    val align: String? = null,
    val conditions: List<String>? = null,
    val elements: List<ElementDefinition>? = null,
)

/** A layout placed in a HUD or popup. In YAML either a map or just the layout id. */
data class PlacementDefinition(
    val layout: String,
    val anchor: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val conditions: List<String>? = null,
)

/** `huds/`: what a player sees. */
@ConfigSerializable
data class HudDefinition(
    val default: Boolean? = null,
    val conditions: List<String>? = null,
    val layouts: List<PlacementDefinition>? = null,
)

/** `popups/`: temporary layouts, optionally stacking. */
@ConfigSerializable
data class PopupDefinition(
    val anchor: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val duration: Int? = null,
    val unique: Boolean? = null,
    @Setting("max-stack") val maxStack: Int? = null,
    @Setting("stack-offset") val stackOffset: List<Int>? = null,
    val layouts: List<PlacementDefinition>? = null,
)

// One wrapper per folder: the entries are the top-level keys of the file.

@ConfigSerializable
data class ImageFile(@Setting(nodeFromParent = true) val entries: Map<String, ImageDefinition> = emptyMap())

@ConfigSerializable
data class TextFile(@Setting(nodeFromParent = true) val entries: Map<String, TextDefinition> = emptyMap())

@ConfigSerializable
data class LayoutFile(@Setting(nodeFromParent = true) val entries: Map<String, LayoutDefinition> = emptyMap())

@ConfigSerializable
data class HudFile(@Setting(nodeFromParent = true) val entries: Map<String, HudDefinition> = emptyMap())

@ConfigSerializable
data class PopupFile(@Setting(nodeFromParent = true) val entries: Map<String, PopupDefinition> = emptyMap())
```

`PlacementDefinitionSerializer.kt`:

```kotlin
package dev.slne.surf.hud.core.config

import org.spongepowered.configurate.ConfigurationNode
import org.spongepowered.configurate.serialize.SerializationException
import org.spongepowered.configurate.serialize.TypeSerializer
import java.lang.reflect.Type

/** Reads a placement from either a bare layout id (`- vote_bar`) or a map with `layout`, `anchor`, `x`, `y`, `conditions`. */
object PlacementDefinitionSerializer : TypeSerializer<PlacementDefinition> {
    override fun deserialize(type: Type, node: ConfigurationNode): PlacementDefinition {
        if (!node.isMap) {
            val layout = node.string ?: throw SerializationException(node, type, "expected a layout id or a placement map")
            return PlacementDefinition(layout)
        }
        val layout = node.node("layout").string ?: throw SerializationException(node, type, "placement needs 'layout'")
        return PlacementDefinition(
            layout = layout,
            anchor = node.node("anchor").string,
            x = node.node("x").takeUnless { it.virtual() }?.int,
            y = node.node("y").takeUnless { it.virtual() }?.int,
            conditions = node.node("conditions").takeUnless { it.virtual() }?.getList(String::class.java),
        )
    }

    override fun serialize(type: Type, obj: PlacementDefinition?, node: ConfigurationNode) {
        if (obj == null) {
            node.raw(null)
            return
        }
        if (obj.anchor == null && obj.x == null && obj.y == null && obj.conditions == null) {
            node.set(obj.layout)
            return
        }
        node.node("layout").set(obj.layout)
        obj.anchor?.let { node.node("anchor").set(it) }
        obj.x?.let { node.node("x").set(it) }
        obj.y?.let { node.node("y").set(it) }
        obj.conditions?.let { node.node("conditions").setList(String::class.java, it) }
    }
}
```

`DefinitionReader.kt`:

```kotlin
package dev.slne.surf.hud.core.config

import dev.slne.surf.api.core.config.serializer.surfSpongeConfigSerializers
import dev.slne.surf.api.core.config.surfConfigApi
import java.nio.file.Path

/** Reads one YAML file into [type]; a missing file yields the type's defaults. */
interface DefinitionReader {
    fun <T : Any> read(type: Class<T>, folder: Path, fileName: String): T
}

/** Production reader backed by surfConfigApi. Note that surfConfigApi writes each file back after loading. */
class SurfConfigDefinitionReader : DefinitionReader {
    init {
        surfSpongeConfigSerializers.registerClassSerializer(PlacementDefinition::class.java, PlacementDefinitionSerializer)
    }

    override fun <T : Any> read(type: Class<T>, folder: Path, fileName: String): T =
        surfConfigApi.createSpongeYmlConfigManager(
            configClass = type,
            configFolder = folder,
            configFileName = fileName,
        ).config
}
```

`DefinitionLoader.kt`:

```kotlin
package dev.slne.surf.hud.core.config

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

/** A definition with its id and the file (relative to the plugin folder) it came from. */
data class Sourced<T>(val id: String, val file: String, val value: T)

/** Everything read from the plugin folder, plus read errors (unreadable files, duplicate ids). */
data class DefinitionSet(
    val settings: HudSettings,
    val images: Map<String, Sourced<ImageDefinition>>,
    val texts: Map<String, Sourced<TextDefinition>>,
    val layouts: Map<String, Sourced<LayoutDefinition>>,
    val huds: Map<String, Sourced<HudDefinition>>,
    val popups: Map<String, Sourced<PopupDefinition>>,
    val errors: List<String>,
)

/** Reads `config.yml` and every `.yml` file below `images/`, `texts/`, `layouts/`, `huds/` and `popups/`. */
class DefinitionLoader(private val reader: DefinitionReader) {
    fun load(root: Path): DefinitionSet {
        val errors = mutableListOf<String>()
        val settings = runCatching { reader.read(HudSettings::class.java, root, "config.yml") }
            .getOrElse {
                errors += "config.yml: ${it.message}"
                HudSettings()
            }
        return DefinitionSet(
            settings = settings,
            images = folder(root, "images", ImageFile::class.java, ImageFile::entries, errors),
            texts = folder(root, "texts", TextFile::class.java, TextFile::entries, errors),
            layouts = folder(root, "layouts", LayoutFile::class.java, LayoutFile::entries, errors),
            huds = folder(root, "huds", HudFile::class.java, HudFile::entries, errors),
            popups = folder(root, "popups", PopupFile::class.java, PopupFile::entries, errors),
            errors = errors,
        )
    }

    private fun <F : Any, D> folder(
        root: Path,
        name: String,
        type: Class<F>,
        entries: (F) -> Map<String, D>,
        errors: MutableList<String>,
    ): Map<String, Sourced<D>> {
        val dir = root.resolve(name)
        if (!dir.exists()) return emptyMap()
        val files = Files.walk(dir).use { stream ->
            stream.asSequence().filter { it.isRegularFile() && it.name.endsWith(".yml") }.sorted().toList()
        }
        val result = LinkedHashMap<String, Sourced<D>>()
        for (file in files) {
            val relative = root.relativize(file).invariantSeparatorsPathString
            val content = try {
                entries(reader.read(type, file.parent, file.name))
            } catch (e: Exception) {
                errors += "$relative: ${e.cause?.message ?: e.message}"
                continue
            }
            for ((id, definition) in content) {
                val existing = result[id]
                if (existing != null) {
                    errors += "$relative > $id: duplicate id, also defined in ${existing.file}"
                } else {
                    result[id] = Sourced(id, relative, definition)
                }
            }
        }
        return result
    }
}
```

`Durations.kt`:

```kotlin
package dev.slne.surf.hud.core.config

import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** Parses config durations such as `500ms`, `5s`, `2m`, `1h`, `1d`. */
object Durations {
    private val PATTERN = Regex("""(\d+)\s*(ms|s|m|h|d)""")

    fun parse(text: String): Duration {
        val match = PATTERN.matchEntire(text.trim())
            ?: throw IllegalArgumentException("invalid duration '$text' (expected e.g. 500ms, 5s, 2m, 1h, 1d)")
        val amount = match.groupValues[1].toLong()
        return when (match.groupValues[2]) {
            "ms" -> amount.milliseconds
            "s" -> amount.seconds
            "m" -> amount.minutes
            "h" -> amount.hours
            else -> amount.days
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.config.*"`
Expected: PASS. If `nodeFromParent` maps are not populated (empty `entries`), the Configurate fork does not apply `nodeFromParent` to maps. In that case change each `*File` class to a custom `TypeSerializer` that reads `node.childrenMap()` into `entries`, register it in both readers, and re-run.

- [ ] **Step 5: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): load HUD definitions from YAML folders" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: Vanilla asset fetcher

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/pack/{HttpGet.kt,VanillaAssets.kt,VanillaAssetFetcher.kt}`
- Create (test support): `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/TestAssets.kt`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/pack/VanillaAssetFetcherTest.kt`

**Interfaces:**
- Produces:
  - `fun interface HttpGet { fun get(url: String): ByteArray }`, `class JdkHttpGet : HttpGet`
  - `class VanillaAssets(val versionId: String, val packFormat: Int, val textVsh: String, val asciiImage: BufferedImage, val asciiRows: List<String>, val asciiHeight: Int)`
  - `class VanillaAssetFetcher(http: HttpGet, cacheRoot: Path) { fun fetch(version: String): VanillaAssets }`, cache under `cacheRoot/<version>/`
  - Test: `object TestAssets { val TEXT_VSH: String; fun asciiImage(): BufferedImage; fun asciiRows(): List<String>; fun vanilla(): VanillaAssets; fun clientJar(): ByteArray; fun png(width, height, argb): ByteArray }`

- [ ] **Step 1: Write the test support and the failing test**

`TestAssets.kt`:

```kotlin
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
```

`VanillaAssetFetcherTest.kt`:

```kotlin
package dev.slne.surf.hud.core.pack

import dev.slne.surf.hud.core.TestAssets
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.test.*

class VanillaAssetFetcherTest {
    @TempDir lateinit var cache: Path

    private val jar = TestAssets.clientJar()
    private fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun http(jarSha1: String = sha1(jar)): Pair<HttpGet, MutableList<String>> {
        val calls = mutableListOf<String>()
        val responses = mapOf(
            VanillaAssetFetcher.MANIFEST to """{"versions":[{"id":"26.3","url":"https://meta/26.3.json"}]}""".toByteArray(),
            "https://meta/26.3.json" to """{"downloads":{"client":{"url":"https://jar/client.jar","sha1":"$jarSha1"}}}""".toByteArray(),
            "https://jar/client.jar" to jar,
        )
        return HttpGet { url -> calls += url; responses[url] ?: error("404 $url") } to calls
    }

    @Test fun `downloads extracts and caches`() {
        val (http, calls) = http()
        val assets = VanillaAssetFetcher(http, cache).fetch("26.3")
        assertEquals(97, assets.packFormat)
        assertEquals(TestAssets.TEXT_VSH, assets.textVsh)
        assertEquals(16, assets.asciiRows.size)
        assertEquals('A', assets.asciiRows[4][1])
        assertEquals(8, assets.asciiHeight)
        assertEquals(3, calls.size)

        val offline = VanillaAssetFetcher({ error("offline") }, cache).fetch("26.3")
        assertEquals(TestAssets.TEXT_VSH, offline.textVsh)
    }

    @Test fun `rejects a jar with the wrong checksum`() {
        val (http, _) = http(jarSha1 = "0".repeat(40))
        val error = assertFailsWith<IllegalStateException> { VanillaAssetFetcher(http, cache).fetch("26.3") }
        assertContains(error.message!!, "SHA-1")
    }

    @Test fun `unknown version is a clear error`() {
        val (http, _) = http()
        val error = assertFailsWith<IllegalStateException> { VanillaAssetFetcher(http, cache).fetch("1.0") }
        assertContains(error.message!!, "1.0")
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.pack.VanillaAssetFetcherTest"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`HttpGet.kt`:

```kotlin
package dev.slne.surf.hud.core.pack

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Blocking HTTP GET returning the body; throws on any non-200 status. */
fun interface HttpGet {
    fun get(url: String): ByteArray
}

class JdkHttpGet : HttpGet {
    private val client = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    override fun get(url: String): ByteArray {
        val response = client.send(
            HttpRequest.newBuilder(URI(url)).timeout(Duration.ofSeconds(60)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        check(response.statusCode() == 200) { "HTTP ${response.statusCode()} for $url" }
        return response.body()
    }
}
```

`VanillaAssets.kt`:

```kotlin
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
```

`VanillaAssetFetcher.kt`:

```kotlin
package dev.slne.surf.hud.core.pack

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import kotlin.io.path.*

/**
 * Provides [VanillaAssets] for a Minecraft version. The client jar is downloaded once through Mojang's
 * piston-meta (verified by SHA-1); only the four needed files are kept in `cacheRoot/<version>/`.
 */
class VanillaAssetFetcher(private val http: HttpGet, private val cacheRoot: Path) {
    fun fetch(version: String): VanillaAssets {
        val dir = cacheRoot.resolve(version)
        if (FILES.values.any { !dir.resolve(it).exists() }) download(version, dir)
        return load(version, dir)
    }

    private fun download(version: String, dir: Path) {
        val manifest = json(http.get(MANIFEST))
        val entry = manifest.getAsJsonArray("versions").map { it.asJsonObject }.firstOrNull { it["id"].asString == version }
            ?: throw IllegalStateException("Minecraft version '$version' is not in the version manifest")
        val client = json(http.get(entry["url"].asString)).getAsJsonObject("downloads").getAsJsonObject("client")
        val jar = http.get(client["url"].asString)
        val expected = client["sha1"].asString
        val actual = MessageDigest.getInstance("SHA-1").digest(jar).joinToString("") { "%02x".format(it) }
        check(actual.equals(expected, ignoreCase = true)) { "Client jar of $version has SHA-1 $actual, expected $expected" }

        dir.createDirectories()
        ZipInputStream(ByteArrayInputStream(jar)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                FILES[entry.name]?.let { dir.resolve(it).writeBytes(zip.readBytes()) }
            }
        }
        FILES.forEach { (path, name) -> check(dir.resolve(name).exists()) { "Client jar of $version has no $path" } }
    }

    private fun load(version: String, dir: Path): VanillaAssets {
        val versionJson = json(dir.resolve("version.json").readBytes())
        val packVersion = versionJson.getAsJsonObject("pack_version")
        val packFormat = (packVersion["resource_major"] ?: packVersion["resource"]).asInt
        val font = json(dir.resolve("default.json").readBytes())
        val provider = font.getAsJsonArray("providers").map { it.asJsonObject }
            .first { it["file"]?.asString == "minecraft:font/ascii.png" }
        return VanillaAssets(
            versionId = version,
            packFormat = packFormat,
            textVsh = dir.resolve("text.vsh").readText(),
            asciiImage = ImageIO.read(dir.resolve("ascii.png").toFile()) ?: error("Cached ascii.png is not an image"),
            asciiRows = provider.getAsJsonArray("chars").map { it.asString },
            asciiHeight = provider["height"]?.asInt ?: 8,
        )
    }

    private fun json(bytes: ByteArray): JsonObject = JsonParser.parseString(bytes.decodeToString()).asJsonObject

    companion object {
        const val MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"

        /** Jar entry → cache file name. */
        private val FILES = mapOf(
            "version.json" to "version.json",
            "assets/minecraft/shaders/core/text.vsh" to "text.vsh",
            "assets/minecraft/font/include/default.json" to "default.json",
            "assets/minecraft/textures/font/ascii.png" to "ascii.png",
        )
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.pack.VanillaAssetFetcherTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): fetch and cache vanilla client assets" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Glyph sets

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/model/{HudEncoding.kt,Glyphs.kt,GlyphSetBuilder.kt}`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/model/{GlyphSetBuilderTest.kt,SpaceGlyphsTest.kt}`

**Interfaces:**
- Consumes: `VanillaAssets`, `ImageDefinition`, `TextDefinition` (Tasks 3, 4).
- Produces:
  - `object HudEncoding { BASE, STRIDE, BIAS, MAX_SLOTS; fun bossbarLineY(row: Int): Int; fun ascent(slot: Int, localY: Int): Int; fun fitsLocalY(y: Int, height: Int): Boolean }`
  - `data class Glyph(val name: String, val char: Char, val width: Int, val height: Int, val advance: Int, val texture: BufferedImage)`
  - `sealed interface GlyphSet { val id: String; val height: Int }` with `AsciiSet(id, height, advances: Map<Char, Int>, rows: List<String>) { fun advance(char: Char): Int? }`, `ImageSet(id, glyph: Glyph)`, `StripSet(id, left: Glyph, right: Glyph, middles: List<Glyph> /* widest first */)`, `PixelSet(id, size: Int, rows: List<Char>, texture: BufferedImage) { val advance: Int }`
  - `class CharAllocator { fun next(): Char }` (U+E000..U+F7FF), `object SpaceGlyphs { val advancesByChar: Map<String, Int>; fun encode(pixels: Int): String }`
  - `class DefinitionException(message: String, val field: String? = null)`
  - `class GlyphSetBuilder(assets, assetRoot: Path, chars: CharAllocator) { fun text(id, TextDefinition): AsciiSet; fun image(id, ImageDefinition): GlyphSet }`

- [ ] **Step 1: Write the failing tests**

`SpaceGlyphsTest.kt`:

```kotlin
package dev.slne.surf.hud.core.model

import kotlin.test.*

class SpaceGlyphsTest {
    private fun sum(encoded: String) = encoded.sumOf { SpaceGlyphs.advancesByChar.getValue(it.toString()) }

    @Test fun `encodes any offset exactly`() {
        for (pixels in listOf(0, 1, -1, 7, -300, 1023, 1500, -2047)) assertEquals(pixels, sum(SpaceGlyphs.encode(pixels)))
    }
    @Test fun `uses the documented code points`() {
        assertEquals("", SpaceGlyphs.encode(1))
        assertEquals("", SpaceGlyphs.encode(-1))
        assertEquals("", SpaceGlyphs.encode(512))
    }
}
```

`GlyphSetBuilderTest.kt`:

```kotlin
package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.TestAssets
import dev.slne.surf.hud.core.config.ImageDefinition
import dev.slne.surf.hud.core.config.TextDefinition
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeBytes
import kotlin.test.*

class GlyphSetBuilderTest {
    @TempDir lateinit var assetRoot: Path
    private val chars = CharAllocator()
    private val builder by lazy { GlyphSetBuilder(TestAssets.vanilla(), assetRoot, chars) }

    private fun asset(path: String, bytes: ByteArray) =
        assetRoot.resolve(path).also { it.parent.createDirectories() }.writeBytes(bytes)

    @Test fun `ascii advances follow vanilla measuring`() {
        val set = builder.text("default", TextDefinition())
        assertEquals(8, set.height)
        assertEquals(6, set.advance('A'))
        assertEquals(2, set.advance('i'))
        assertEquals(4, set.advance(' '))
        assertNull(set.advance('ä'))
    }

    @Test fun `ascii scales with height`() {
        val set = builder.text("title", TextDefinition(height = 16))
        assertEquals(11, set.advance('A'))
        assertEquals(8, set.advance(' '))
    }

    @Test fun `single image`() {
        asset("icons/coin.png", TestAssets.png(7, 9))
        val set = builder.image("coin", ImageDefinition("single", "icons/coin.png")) as ImageSet
        assertEquals(9, set.height)
        assertEquals(8, set.glyph.advance)
        assertEquals('', set.glyph.char)
    }

    @Test fun `strip is cut into edges and power of two middles`() {
        asset("strips/panel.png", TestAssets.png(3, 11))
        val set = builder.image("panel", ImageDefinition("strip", "strips/panel.png")) as StripSet
        assertEquals(11, set.height)
        assertEquals(listOf(256, 128, 64, 32, 16, 8, 4, 2, 1), set.middles.map { it.width })
        assertEquals(2, set.left.advance)
        assertEquals(11, (listOf(set.left, set.right) + set.middles).map { it.char }.distinct().size)
    }

    @Test fun `strip must be three pixels wide`() {
        asset("strips/bad.png", TestAssets.png(4, 11))
        assertFailsWith<DefinitionException> { builder.image("bad", ImageDefinition("strip", "strips/bad.png")) }
    }

    @Test fun `pixel set`() {
        val set = builder.image("head_big", ImageDefinition("pixel", size = 5)) as PixelSet
        assertEquals(8, set.rows.size)
        assertEquals(40, set.height)
        assertEquals(6, set.advance)
    }

    @Test fun `missing file names the path`() {
        val error = assertFailsWith<DefinitionException> { builder.image("x", ImageDefinition("single", "nope.png")) }
        assertEquals("file", error.field)
        assertContains(error.message!!, "nope.png")
    }

    @Test fun `unknown type`() {
        val error = assertFailsWith<DefinitionException> { builder.image("x", ImageDefinition("gif", "a.png")) }
        assertEquals("type", error.field)
    }

    @Test fun `ascent encodes slot and local y`() {
        assertEquals(7 - ((1 shl 20) + 3 * 1024 + 512 + 5), HudEncoding.ascent(3, 5))
        assertTrue(HudEncoding.fitsLocalY(-512, 8))
        assertFalse(HudEncoding.fitsLocalY(505, 8))
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.model.*"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`HudEncoding.kt`:

```kotlin
package dev.slne.surf.hud.core.model

/**
 * How a HUD glyph tells the shader which slot it belongs to: the font ascent pushes the glyph far below the
 * screen, to `lineY + BASE + slot * STRIDE + BIAS + localY`. The shader recovers slot and localY from the
 * vertex y and moves the glyph to the slot's anchor. Every value stays below 2^24, so it is exact in a float.
 */
object HudEncoding {
    const val BASE = 1 shl 20
    const val STRIDE = 1024

    /** Local y range of a slot is [-BIAS, STRIDE - BIAS), glyph height and shadow included. */
    const val BIAS = 512
    const val MAX_SLOTS = ((1 shl 24) - BASE) / STRIDE

    /** Vanilla draws the first bossbar name at y = 3 and each further bossbar 19 GUI pixels lower. */
    fun bossbarLineY(row: Int) = 3 + 19 * row

    /** Bitmap glyphs are drawn with their top at `lineY + 7 - ascent`. */
    fun ascent(slot: Int, localY: Int) = 7 - (BASE + slot * STRIDE + BIAS + localY)

    /** Whether a glyph of [height] drawn at local [y] stays inside its slot's range (1 px shadow included). */
    fun fitsLocalY(y: Int, height: Int) = y >= -BIAS && y + height + 1 <= STRIDE - BIAS
}
```

`Glyphs.kt`:

```kotlin
package dev.slne.surf.hud.core.model

import java.awt.image.BufferedImage
import kotlin.math.abs

/** Thrown while compiling a definition; [field] names the offending key, if any. */
class DefinitionException(message: String, val field: String? = null) : RuntimeException(message)

/** One bitmap glyph of a generated font; [advance] includes vanilla's 1 px gap after bitmap glyphs. */
data class Glyph(
    val name: String,
    val char: Char,
    val width: Int,
    val height: Int,
    val advance: Int,
    val texture: BufferedImage,
)

/** A set of glyphs defined by one `images/` or `texts/` entry. */
sealed interface GlyphSet {
    val id: String
    val height: Int
}

/** Vanilla `ascii.png` drawn [height] px tall; [advances] are per character (bold adds 1, except spaces). */
class AsciiSet(override val id: String, override val height: Int, val advances: Map<Char, Int>, val rows: List<String>) : GlyphSet {
    fun advance(char: Char): Int? = advances[char]
}

class ImageSet(override val id: String, val glyph: Glyph) : GlyphSet {
    override val height get() = glyph.height
}

/** Edge glyphs plus power-of-two middle pieces ([middles] widest first) that compose any width. */
class StripSet(override val id: String, val left: Glyph, val right: Glyph, val middles: List<Glyph>) : GlyphSet {
    override val height get() = left.height
}

/** A white [size] px square once per row; row r is drawn r * size px lower. Tinted per glyph to draw images. */
class PixelSet(override val id: String, val size: Int, val rows: List<Char>, val texture: BufferedImage) : GlyphSet {
    override val height get() = size * rows.size
    val advance get() = size + 1
}

/** Hands out private-use characters for non-ascii glyphs; they only need to be unique within surf-hud fonts. */
class CharAllocator {
    private var next = FIRST

    fun next(): Char {
        if (next > LAST) throw DefinitionException("too many glyphs (max ${LAST - FIRST + 1})")
        return (next++).toChar()
    }

    private companion object {
        const val FIRST = 0xE000
        const val LAST = 0xF7FF
    }
}

/** Space glyphs of ±1, ±2, ±4 … ±512 px, part of every surf-hud font; used to move the cursor. */
object SpaceGlyphs {
    private val chars: Map<Int, Char> = (0..9).flatMap { power ->
        val amount = 1 shl power
        listOf(amount to (0xF800 + 2 * power).toChar(), -amount to (0xF800 + 2 * power + 1).toChar())
    }.toMap()

    val advancesByChar: Map<String, Int> = chars.entries.associate { (amount, char) -> char.toString() to amount }

    /** Encodes an offset as a sum of space glyphs, largest first. */
    fun encode(pixels: Int): String {
        if (pixels == 0) return ""
        val sign = if (pixels < 0) -1 else 1
        var remaining = abs(pixels)
        val text = StringBuilder()
        for (power in 9 downTo 0) {
            val amount = 1 shl power
            val char = chars.getValue(sign * amount)
            while (remaining >= amount) {
                text.append(char)
                remaining -= amount
            }
        }
        return text.toString()
    }
}
```

`GlyphSetBuilder.kt`:

```kotlin
package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.config.ImageDefinition
import dev.slne.surf.hud.core.config.TextDefinition
import dev.slne.surf.hud.core.pack.VanillaAssets
import java.awt.image.BufferedImage
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.io.path.exists

/** Turns `texts/` and `images/` definitions into glyph sets, reading image files below [assetRoot]. */
class GlyphSetBuilder(private val assets: VanillaAssets, private val assetRoot: Path, private val chars: CharAllocator) {
    fun text(id: String, definition: TextDefinition): AsciiSet {
        val type = definition.type ?: "ascii"
        if (type != "ascii") throw DefinitionException("unknown text type '$type' (expected ascii)", "type")
        val height = definition.height ?: assets.asciiHeight
        if (height !in 1..256) throw DefinitionException("must be between 1 and 256, was $height", "height")
        val image = assets.asciiImage
        val cellWidth = image.width / assets.asciiRows.first().length
        val cellHeight = image.height / assets.asciiRows.size
        val scale = height.toDouble() / cellHeight
        val advances = HashMap<Char, Int>()
        assets.asciiRows.forEachIndexed { row, line ->
            line.forEachIndexed { column, char ->
                if (char != '\u0000' && char != ' ') {
                    val width = opaqueWidth(image, column * cellWidth, row * cellHeight, cellWidth, cellHeight)
                    advances[char] = (0.5 + width * scale).toInt() + 1
                }
            }
        }
        // Vanilla's space is 4 px at height 8; scale it with the font.
        advances[' '] = (4 * scale + 0.5).toInt()
        return AsciiSet(id, height, advances, assets.asciiRows)
    }

    fun image(id: String, definition: ImageDefinition): GlyphSet = when (val type = definition.type) {
        "single" -> {
            val image = read(definition.file)
            ImageSet(id, Glyph(id, chars.next(), image.width, image.height, opaqueWidth(image) + 1, image))
        }
        "strip" -> strip(id, read(definition.file), definition.file!!)
        "pixel" -> pixel(id, definition)
        null -> throw DefinitionException("missing", "type")
        else -> throw DefinitionException("unknown image type '$type' (expected single, strip or pixel)", "type")
    }

    private fun strip(id: String, source: BufferedImage, file: String): StripSet {
        if (source.width != 3) throw DefinitionException("strip texture $file must be 3 px wide (left, middle, right column)", "file")
        fun column(x: Int, width: Int, name: String): Glyph {
            val image = BufferedImage(width, source.height, BufferedImage.TYPE_INT_ARGB)
            for (px in 0 until width) for (py in 0 until source.height) image.setRGB(px, py, source.getRGB(x, py))
            return Glyph(name, chars.next(), width, source.height, width + 1, image)
        }
        val left = column(0, 1, "left")
        val right = column(2, 1, "right")
        val middles = MIDDLE_WIDTHS.map { column(1, it, "middle_$it") }
        return StripSet(id, left, right, middles)
    }

    private fun pixel(id: String, definition: ImageDefinition): PixelSet {
        val size = definition.size ?: throw DefinitionException("missing", "size")
        if (size !in 1..64) throw DefinitionException("must be between 1 and 64, was $size", "size")
        val rows = definition.rows ?: 8
        if (rows !in 1..64) throw DefinitionException("must be between 1 and 64, was $rows", "rows")
        val texture = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until size) for (y in 0 until size) texture.setRGB(x, y, -1)
        return PixelSet(id, size, List(rows) { chars.next() }, texture)
    }

    private fun read(file: String?): BufferedImage {
        if (file == null) throw DefinitionException("missing", "file")
        val path = assetRoot.resolve(file).normalize()
        if (!path.startsWith(assetRoot.normalize()) || !path.exists()) {
            throw DefinitionException("file '$file' not found in assets/", "file")
        }
        return ImageIO.read(path.toFile()) ?: throw DefinitionException("file '$file' is not a readable image", "file")
    }

    companion object {
        private val MIDDLE_WIDTHS = listOf(256, 128, 64, 32, 16, 8, 4, 2, 1)

        /** Width up to the rightmost column with any visible pixel, as vanilla measures bitmap glyphs. */
        fun opaqueWidth(image: BufferedImage, x0: Int = 0, y0: Int = 0, width: Int = image.width, height: Int = image.height): Int {
            for (x in width - 1 downTo 0) {
                for (y in 0 until height) {
                    if ((image.getRGB(x0 + x, y0 + y) ushr 24) != 0) return x + 1
                }
            }
            return 0
        }
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.model.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): build glyph sets from text and image definitions" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: Compiler and HUD model

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/model/{Specs.kt,HudModel.kt,ErrorCollector.kt,Compiler.kt}`
- Create (test support): `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/TestDefinitions.kt`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/model/CompilerTest.kt`

**Interfaces:**
- Consumes: Tasks 2–5.
- Produces:
  - `enum class Anchor(x: Double, y: Double)` (9 values, `parse(name): Anchor?`), `enum class HAlign { LEFT, CENTER, RIGHT; parse(name): HAlign? }`, `enum class FillDirection { RIGHT, LEFT; parse }`
  - `sealed interface ElementSpec { val index: Int; val id: String?; val x: Int; val y: Int; val conditions: List<Expr>; val glyphSet: GlyphSet }`: `TextSpec(…, glyphSet: AsciiSet, template: TextTemplate, align: HAlign, shadow: Boolean)`, `ImageSpec(…, glyphSet: ImageSet, color: TextColor)`, `StripSpec(…, glyphSet: StripSet, width: Expr, color: TextColor)`, `BarSpec(…, glyphSet: StripSet, width: Expr, value: Expr, direction: FillDirection, color: TextColor)`, `HeadSpec(…, glyphSet: PixelSet, player: Expr, brightness: Expr)`
  - `LayoutSpec(id, align: HAlign, conditions: List<Expr>, elements: List<ElementSpec>) { val placeholders: Set<PlaceholderRef> }` (all refs except `width`)
  - `Placement(slot: Int, layout: LayoutSpec, conditions: List<Expr>)`, `HudSpec(id, default: Boolean, conditions: List<Expr>, placements: List<Placement>)`, `PopupSpec(id, durationTicks: Int, unique: Boolean, stacks: List<List<Placement>>) { val maxStack: Int }`
  - `FontSpec(key: Key, slot: Int, y: Int, glyphSets: Set<GlyphSet>)`, `SlotSpec(index, anchor, offsetX, offsetY)`
  - `class HudModel(settings, assets, bossbarColor: BossBar.Color, glyphSets: Map<String, GlyphSet>, layouts, huds: Map<String, HudSpec>, popups: Map<String, PopupSpec>, slots: List<SlotSpec>, fonts: Map<Key, FontSpec>) { fun fontKey(slot: Int, element: ElementSpec): Key; fun slotFont(slot: Int): Key }`
  - `sealed interface CompileResult { Success(model); Failure(errors) }`, `class Compiler(assets: VanillaAssets, assetRoot: Path) { fun compile(definitions: DefinitionSet): CompileResult }`
  - Test: `object TestDefinitions { fun write(root: Path, files: Map<String, String>); fun standardFiles(): Map<String, String>; fun compile(root: Path, files: Map<String, String> = standardFiles()): HudModel }`

- [ ] **Step 1: Write the test support and the failing tests**

`TestDefinitions.kt`:

```kotlin
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
```

`CompilerTest.kt`:

```kotlin
package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.TestDefinitions
import dev.slne.surf.hud.core.expression.PlaceholderRef
import net.kyori.adventure.key.Key
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.*

class CompilerTest {
    @TempDir lateinit var root: Path

    private fun errors(vararg changes: Pair<String, String>): List<String> =
        (TestDefinitions.compileResult(root, TestDefinitions.standardFiles() + changes) as CompileResult.Failure).errors

    @Test fun `assigns slots in stable order`() {
        val model = TestDefinitions.compile(root)
        // huds sorted by id: extra (1 placement), main (2); popups sorted: feed (2 stack slots), vote (1)
        assertEquals(listOf(0), model.huds.getValue("extra").placements.map { it.slot })
        assertEquals(listOf(1, 2), model.huds.getValue("main").placements.map { it.slot })
        assertEquals(listOf(listOf(3), listOf(4)), model.popups.getValue("feed").stacks.map { s -> s.map { it.slot } })
        assertEquals(listOf(listOf(5)), model.popups.getValue("vote").stacks.map { s -> s.map { it.slot } })
        assertEquals(6, model.slots.size)
    }

    @Test fun `popup slots include position and stack offset`() {
        val model = TestDefinitions.compile(root)
        val second = model.slots[4]
        assertEquals(Anchor.TOP_RIGHT, second.anchor)
        assertEquals(-4, second.offsetX)
        assertEquals(42, second.offsetY)
        assertEquals(100, model.popups.getValue("feed").durationTicks)
        assertEquals(-1, model.popups.getValue("vote").durationTicks)
    }

    @Test fun `fonts per slot and y, ascii separated`() {
        val model = TestDefinitions.compile(root)
        val money = model.huds.getValue("main").placements[0]
        val text = money.layout.elements[2]
        assertEquals(Key.key("surf", "hud/s1_3_default"), model.fontKey(1, text))
        assertEquals(Key.key("surf", "hud/s1_0"), model.fontKey(1, money.layout.elements[0]))
        assertEquals(Key.key("surf", "hud/s1_2"), model.fontKey(1, money.layout.elements[1]))
        assertTrue(model.fonts.getValue(Key.key("surf", "hud/s1_0")).glyphSets.single() is StripSet)
    }

    @Test fun `layout placeholders exclude widths`() {
        val model = TestDefinitions.compile(root)
        assertEquals(setOf(PlaceholderRef("money")), model.layouts.getValue("money").placeholders)
    }

    @Test fun `reports errors with file and path`() {
        val errors = errors("layouts/stats.yml" to """
            money:
              elements:
                - { type: image, image: panel, width: "mx(1, 2)" }
                - { type: text, font: nope, text: "x" }
                - { type: image, image: missing }
            health: { elements: [ { type: bar, image: panel, width: 60, value: "1" } ] }
            head: { elements: [ { type: head, image: head_small, player: "[target]" } ] }
        """)
        assertContains(errors, "layouts/stats.yml > money > elements[0].width: unknown function 'mx' at position 0")
        assertContains(errors, "layouts/stats.yml > money > elements[1].font: unknown text 'nope'")
        assertContains(errors, "layouts/stats.yml > money > elements[2].image: unknown image 'missing'")
    }

    @Test fun `width references must name a text element of the layout`() {
        val errors = errors("layouts/stats.yml" to """
            money: { elements: [ { type: image, image: panel, width: "[width:nope]" } ] }
            health: { elements: [ { type: bar, image: panel, width: 60, value: "1" } ] }
            head: { elements: [ { type: head, image: head_small, player: "[target]" } ] }
        """)
        assertTrue(errors.any { it.startsWith("layouts/stats.yml > money > elements[0].width") && "unknown text element 'nope'" in it }, errors.toString())
    }

    @Test fun `unknown layout and anchor in huds`() {
        val errors = errors("huds/main.yml" to """
            main:
              layouts:
                - { layout: nope, anchor: bottom_right }
                - { layout: money, anchor: sideways }
        """)
        assertContains(errors, "huds/main.yml > main > layouts[0].layout: unknown layout 'nope'")
        assertContains(errors.joinToString(), "huds/main.yml > main > layouts[1].anchor: unknown anchor 'sideways'")
    }

    @Test fun `element y must stay inside the slot`() {
        val errors = errors("layouts/stats.yml" to """
            money: { elements: [ { type: text, y: 600, text: "x" } ] }
            health: { elements: [ { type: bar, image: panel, width: 60, value: "1" } ] }
            head: { elements: [ { type: head, image: head_small, player: "[target]" } ] }
        """)
        assertTrue(errors.any { it.startsWith("layouts/stats.yml > money > elements[0].y") }, errors.toString())
    }

    @Test fun `invalid ids are rejected`() {
        val errors = errors("texts/default.yml" to "Default: { type: ascii }")
        assertTrue(errors.any { "Default" in it && "[a-z0-9_-]" in it })
    }

    @Test fun `invalid bossbar color is reported`() {
        val errors = errors("config.yml" to "bossbar-color: magenta")
        assertTrue(errors.any { it.startsWith("config.yml > bossbar-color") })
    }

    @Test fun `popup placements cannot set their own anchor`() {
        val errors = errors("popups/feed.yml" to """
            feed: { anchor: center, layouts: [ { layout: head, anchor: top_left } ] }
        """)
        assertContains(errors.single(), "anchor is set on the popup")
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.model.CompilerTest"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`Specs.kt`:

```kotlin
package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.expression.Expr
import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.TextTemplate
import net.kyori.adventure.text.format.TextColor

/** A screen anchor as a fraction of the GUI width and height. */
enum class Anchor(val x: Double, val y: Double) {
    TOP_LEFT(0.0, 0.0), TOP_CENTER(0.5, 0.0), TOP_RIGHT(1.0, 0.0),
    CENTER_LEFT(0.0, 0.5), CENTER(0.5, 0.5), CENTER_RIGHT(1.0, 0.5),
    BOTTOM_LEFT(0.0, 1.0), BOTTOM_CENTER(0.5, 1.0), BOTTOM_RIGHT(1.0, 1.0);

    companion object {
        fun parse(name: String): Anchor? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

enum class HAlign {
    LEFT, CENTER, RIGHT;

    companion object {
        fun parse(name: String): HAlign? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** Side a bar fills from: `RIGHT` grows to the right (fills from the left edge), `LEFT` the opposite. */
enum class FillDirection {
    RIGHT, LEFT;

    companion object {
        fun parse(name: String): FillDirection? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** A compiled layout element; [index] is its position in the layout's element list. */
sealed interface ElementSpec {
    val index: Int
    val id: String?
    val x: Int
    val y: Int
    val conditions: List<Expr>
    val glyphSet: GlyphSet
}

data class TextSpec(
    override val index: Int, override val id: String?, override val x: Int, override val y: Int,
    override val conditions: List<Expr>, override val glyphSet: AsciiSet,
    val template: TextTemplate, val align: HAlign, val shadow: Boolean,
) : ElementSpec

data class ImageSpec(
    override val index: Int, override val id: String?, override val x: Int, override val y: Int,
    override val conditions: List<Expr>, override val glyphSet: ImageSet, val color: TextColor,
) : ElementSpec

data class StripSpec(
    override val index: Int, override val id: String?, override val x: Int, override val y: Int,
    override val conditions: List<Expr>, override val glyphSet: StripSet, val width: Expr, val color: TextColor,
) : ElementSpec

data class BarSpec(
    override val index: Int, override val id: String?, override val x: Int, override val y: Int,
    override val conditions: List<Expr>, override val glyphSet: StripSet,
    val width: Expr, val value: Expr, val direction: FillDirection, val color: TextColor,
) : ElementSpec

data class HeadSpec(
    override val index: Int, override val id: String?, override val x: Int, override val y: Int,
    override val conditions: List<Expr>, override val glyphSet: PixelSet, val player: Expr, val brightness: Expr,
) : ElementSpec

data class LayoutSpec(val id: String, val align: HAlign, val conditions: List<Expr>, val elements: List<ElementSpec>) {
    /** Every placeholder the layout reads, except `[width:…]`; a change in any of them re-renders the layout. */
    val placeholders: Set<PlaceholderRef> = buildSet {
        conditions.forEach { addAll(it.placeholders()) }
        elements.forEach { element ->
            element.conditions.forEach { addAll(it.placeholders()) }
            when (element) {
                is TextSpec -> addAll(element.template.placeholders())
                is ImageSpec -> {}
                is StripSpec -> addAll(element.width.placeholders())
                is BarSpec -> { addAll(element.width.placeholders()); addAll(element.value.placeholders()) }
                is HeadSpec -> { addAll(element.player.placeholders()); addAll(element.brightness.placeholders()) }
            }
        }
    }.filterTo(HashSet()) { it.name != "width" }
}

/** A layout placed in its own shader [slot]. */
data class Placement(val slot: Int, val layout: LayoutSpec, val conditions: List<Expr>)

data class HudSpec(val id: String, val default: Boolean, val conditions: List<Expr>, val placements: List<Placement>)

/** [stacks]`[i]` are the placements of the instance at stack index i (newest = 0). */
data class PopupSpec(val id: String, val durationTicks: Int, val unique: Boolean, val stacks: List<List<Placement>>) {
    val maxStack: Int get() = stacks.size
}
```

`HudModel.kt`:

```kotlin
package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.config.HudSettings
import dev.slne.surf.hud.core.pack.VanillaAssets
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key

/** Where a shader slot is pinned on screen: the anchor plus an offset in GUI pixels. */
data class SlotSpec(val index: Int, val anchor: Anchor, val offsetX: Int, val offsetY: Int)

/** One generated font: the glyph sets drawn at local [y] in [slot]. Every font also carries the space glyphs. */
data class FontSpec(val key: Key, val slot: Int, val y: Int, val glyphSets: Set<GlyphSet>)

/** The compiled, immutable configuration: what the pack contains and what the renderer draws. */
class HudModel(
    val settings: HudSettings,
    val assets: VanillaAssets,
    val bossbarColor: BossBar.Color,
    val glyphSets: Map<String, GlyphSet>,
    val layouts: Map<String, LayoutSpec>,
    val huds: Map<String, HudSpec>,
    val popups: Map<String, PopupSpec>,
    val slots: List<SlotSpec>,
    val fonts: Map<Key, FontSpec>,
) {
    /** Font of [element] in [slot]: one per (slot, y); ascii sets get their own font so their characters cannot clash. */
    fun fontKey(slot: Int, element: ElementSpec): Key = fontKey(settings.namespace, slot, element)

    /** Any font of [slot]; used for cursor moves that are not attached to a glyph. */
    fun slotFont(slot: Int): Key = slotFonts.getValue(slot)

    private val slotFonts: Map<Int, Key> = fonts.values.groupBy { it.slot }.mapValues { (_, fonts) -> fonts.first().key }

    companion object {
        fun fontKey(namespace: String, slot: Int, element: ElementSpec): Key {
            val y = if (element.y < 0) "m${-element.y}" else "${element.y}"
            val suffix = if (element is TextSpec) "_${element.glyphSet.id}" else ""
            return Key.key(namespace, "hud/s${slot}_$y$suffix")
        }
    }
}
```

`ErrorCollector.kt`:

```kotlin
package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.expression.ExpressionException

/** Collects compile errors as `path: message`, where a [DefinitionException.field] is appended to the path. */
class ErrorCollector(initial: List<String> = emptyList()) {
    val errors = initial.toMutableList()

    fun add(path: String, message: String) {
        errors += "$path: $message"
    }

    /** Runs [block]; a [DefinitionException] or [ExpressionException] becomes an error at [path] and yields `null`. */
    inline fun <T> at(path: String, block: () -> T): T? = try {
        block()
    } catch (e: DefinitionException) {
        add(if (e.field != null) "$path.${e.field}" else path, e.message ?: "invalid")
        null
    } catch (e: ExpressionException) {
        add(path, e.message ?: "invalid expression")
        null
    }
}
```

`Compiler.kt`:

```kotlin
package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.config.*
import dev.slne.surf.hud.core.expression.*
import dev.slne.surf.hud.core.pack.VanillaAssets
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import java.nio.file.Path

sealed interface CompileResult {
    data class Success(val model: HudModel) : CompileResult
    data class Failure(val errors: List<String>) : CompileResult
}

/**
 * Validates a [DefinitionSet] and compiles it into a [HudModel]: glyph sets, layouts, HUDs and popups with
 * their shader slots and fonts. All errors are collected; any error makes the result a [CompileResult.Failure].
 */
class Compiler(private val assets: VanillaAssets, private val assetRoot: Path) {
    fun compile(definitions: DefinitionSet): CompileResult {
        val errors = ErrorCollector(definitions.errors)
        val settings = definitions.settings
        val bossbarColor = settings(settings, errors)
        val builder = GlyphSetBuilder(assets, assetRoot, CharAllocator())

        val texts = definitions.texts.values.mapNotNull { (id, file, definition) ->
            errors.at("$file > $id") { checkId(id); builder.text(id, definition) }?.let { id to it }
        }.toMap()
        val images = definitions.images.values.mapNotNull { (id, file, definition) ->
            errors.at("$file > $id") { checkId(id); builder.image(id, definition) }?.let { id to it }
        }.toMap()
        val layouts = definitions.layouts.values.mapNotNull { (id, file, definition) ->
            layout(id, "$file > $id", definition, texts, images, errors)?.let { id to it }
        }.toMap()

        val slots = mutableListOf<SlotSpec>()
        fun place(draft: Draft): Placement {
            val slot = slots.size
            slots += SlotSpec(slot, draft.anchor, draft.x, draft.y)
            return Placement(slot, draft.layout, draft.conditions)
        }

        val huds = definitions.huds.values.sortedBy { it.id }.mapNotNull { (id, file, definition) ->
            val path = "$file > $id"
            errors.at(path) { checkId(id) } ?: return@mapNotNull null
            val drafts = definition.layouts.orEmpty().mapIndexed { i, placement ->
                errors.at("$path > layouts[$i]") {
                    val anchor = placement.anchor?.let { Anchor.parse(it) ?: throw DefinitionException("unknown anchor '$it' (expected ${anchors()})", "anchor") }
                        ?: throw DefinitionException("missing", "anchor")
                    Draft(layoutRef(placement.layout, layouts, definitions), anchor, placement.x ?: 0, placement.y ?: 0,
                        conditions(placement.conditions, "conditions"))
                }
            }
            val conditions = errors.at(path) { conditions(definition.conditions, "conditions") }
            if (drafts.any { it == null } || conditions == null) return@mapNotNull null
            id to HudSpec(id, definition.default ?: false, conditions, drafts.map { place(it!!) })
        }.toMap()

        val popups = definitions.popups.values.sortedBy { it.id }.mapNotNull { (id, file, definition) ->
            val path = "$file > $id"
            val header = errors.at(path) { checkId(id); popupHeader(definition) } ?: return@mapNotNull null
            val placements = definition.layouts.orEmpty().mapIndexed { i, placement ->
                errors.at("$path > layouts[$i]") {
                    if (placement.anchor != null) throw DefinitionException("anchor is set on the popup", "anchor")
                    Triple(layoutRef(placement.layout, layouts, definitions), placement, conditions(placement.conditions, "conditions"))
                }
            }
            if (placements.any { it == null }) return@mapNotNull null
            if (placements.isEmpty()) {
                errors.add("$path.layouts", "missing")
                return@mapNotNull null
            }
            val stacks = (0 until header.maxStack).map { index ->
                placements.map { entry ->
                    val (layout, placement, conditions) = entry!!
                    place(
                        Draft(
                            layout, header.anchor,
                            header.x + (placement.x ?: 0) + header.stackX * index,
                            header.y + (placement.y ?: 0) + header.stackY * index,
                            conditions,
                        )
                    )
                }
            }
            id to PopupSpec(id, header.duration, header.unique, stacks)
        }.toMap()

        if (slots.size > HudEncoding.MAX_SLOTS) errors.add("huds/popups", "too many layout placements (${slots.size}, max ${HudEncoding.MAX_SLOTS})")
        if (errors.errors.isNotEmpty() || bossbarColor == null) return CompileResult.Failure(errors.errors)

        val fonts = LinkedHashMap<Key, FontSpec>()
        (huds.values.flatMap { it.placements } + popups.values.flatMap { it.stacks.flatten() }).forEach { placement ->
            placement.layout.elements.forEach { element ->
                val key = HudModel.fontKey(settings.namespace, placement.slot, element)
                val font = fonts[key] ?: FontSpec(key, placement.slot, element.y, emptySet())
                fonts[key] = font.copy(glyphSets = font.glyphSets + element.glyphSet)
            }
        }
        return CompileResult.Success(
            HudModel(settings, assets, bossbarColor, texts + images, layouts, huds, popups, slots, fonts)
        )
    }

    private data class Draft(val layout: LayoutSpec, val anchor: Anchor, val x: Int, val y: Int, val conditions: List<Expr>)

    private data class PopupHeader(
        val anchor: Anchor, val x: Int, val y: Int, val duration: Int, val unique: Boolean,
        val maxStack: Int, val stackX: Int, val stackY: Int,
    )

    private fun popupHeader(definition: PopupDefinition): PopupHeader {
        val anchor = definition.anchor?.let { Anchor.parse(it) ?: throw DefinitionException("unknown anchor '$it' (expected ${anchors()})", "anchor") }
            ?: throw DefinitionException("missing", "anchor")
        val duration = definition.duration ?: -1
        if (duration != -1 && duration <= 0) throw DefinitionException("must be -1 or positive, was $duration", "duration")
        val maxStack = definition.maxStack ?: 1
        if (maxStack !in 1..64) throw DefinitionException("must be between 1 and 64, was $maxStack", "max-stack")
        val offset = definition.stackOffset ?: listOf(0, 0)
        if (offset.size != 2) throw DefinitionException("must be [x, y]", "stack-offset")
        return PopupHeader(anchor, definition.x ?: 0, definition.y ?: 0, duration, definition.unique ?: false, maxStack, offset[0], offset[1])
    }

    private fun settings(settings: HudSettings, errors: ErrorCollector): BossBar.Color? {
        if (!Regex("[a-z0-9_.-]+").matches(settings.namespace)) errors.add("config.yml > namespace", "must match [a-z0-9_.-]+")
        if (settings.updateInterval < 1) errors.add("config.yml > update-interval", "must be at least 1")
        listOf("heads.timeout" to settings.heads.timeout, "heads.cache-ttl" to settings.heads.cacheTtl).forEach { (key, value) ->
            runCatching { Durations.parse(value) }.onFailure { errors.add("config.yml > $key", it.message ?: "invalid duration") }
        }
        return BossBar.Color.NAMES.value(settings.bossbarColor).also {
            if (it == null) errors.add("config.yml > bossbar-color", "unknown color '${settings.bossbarColor}' (expected ${BossBar.Color.NAMES.keys().joinToString()})")
        }
    }

    private fun layout(
        id: String, path: String, definition: LayoutDefinition,
        texts: Map<String, AsciiSet>, images: Map<String, GlyphSet>, errors: ErrorCollector,
    ): LayoutSpec? {
        val header = errors.at(path) {
            checkId(id)
            val elements = definition.elements.orEmpty()
            if (elements.isEmpty()) throw DefinitionException("missing", "elements")
            val duplicate = elements.mapNotNull { it.id }.groupBy { it }.filterValues { it.size > 1 }.keys.firstOrNull()
            if (duplicate != null) throw DefinitionException("duplicate element id '$duplicate'", "elements")
            val align = definition.align?.let { HAlign.parse(it) ?: throw DefinitionException("unknown align '$it' (expected left, center or right)", "align") } ?: HAlign.LEFT
            align to conditions(definition.conditions, "conditions")
        } ?: return null
        val definitions = definition.elements.orEmpty()
        val textIds = definitions.filter { it.type == "text" }.mapNotNull { it.id }.toSet()
        val elements = definitions.mapIndexed { index, element ->
            errors.at("$path > elements[$index]") { element(index, element, texts, images, textIds) }
        }
        if (elements.any { it == null }) return null
        return LayoutSpec(id, header.first, header.second, elements.map { it!! })
    }

    private fun element(
        index: Int, e: ElementDefinition, texts: Map<String, AsciiSet>, images: Map<String, GlyphSet>, textIds: Set<String>,
    ): ElementSpec {
        e.id?.let(::checkElementId)
        val x = e.x ?: 0
        val y = e.y ?: 0
        val conditions = conditions(e.conditions, "conditions", textIds)
        val color = color(e.color)
        val spec = when (val type = e.type) {
            "text" -> {
                val fontId = e.font ?: "default"
                val font = texts[fontId] ?: throw DefinitionException("unknown text '$fontId'", "font")
                val template = wrap("text") { TextTemplate.parse(e.text ?: throw DefinitionException("missing", "text")) }
                if (template.placeholders().any { it.name == "width" }) throw DefinitionException("texts cannot use [width:…]", "text")
                val align = e.align?.let { HAlign.parse(it) ?: throw DefinitionException("unknown align '$it'", "align") } ?: HAlign.LEFT
                TextSpec(index, e.id, x, y, conditions, font, template, align, e.shadow ?: true)
            }
            "image" -> when (val set = imageRef(e.image, images)) {
                is ImageSet -> {
                    if (e.width != null) throw DefinitionException("only strip images have a width", "width")
                    ImageSpec(index, e.id, x, y, conditions, set, color)
                }
                is StripSet -> StripSpec(index, e.id, x, y, conditions, set, expr("width", e.width, textIds, required = true)!!, color)
                else -> throw DefinitionException("pixel sets can only be drawn by head elements", "image")
            }
            "bar" -> {
                val set = imageRef(e.image, images) as? StripSet ?: throw DefinitionException("bars need a strip image", "image")
                val direction = e.direction?.let { FillDirection.parse(it) ?: throw DefinitionException("unknown direction '$it' (expected right or left)", "direction") } ?: FillDirection.RIGHT
                BarSpec(index, e.id, x, y, conditions, set, expr("width", e.width, textIds, true)!!, expr("value", e.value, textIds, true)!!, direction, color)
            }
            "head" -> {
                val set = imageRef(e.image, images) as? PixelSet ?: throw DefinitionException("heads need a pixel image", "image")
                if (set.rows.size < 8) throw DefinitionException("heads need a pixel set with at least 8 rows", "image")
                HeadSpec(index, e.id, x, y, conditions, set, expr("player", e.player, textIds, true)!!, expr("brightness", e.brightness ?: "1", textIds, true)!!)
            }
            null -> throw DefinitionException("missing", "type")
            else -> throw DefinitionException("unknown element type '$type' (expected text, image, bar or head)", "type")
        }
        if (!HudEncoding.fitsLocalY(y, spec.glyphSet.height)) {
            throw DefinitionException("must be between ${-HudEncoding.BIAS} and ${HudEncoding.STRIDE - HudEncoding.BIAS - spec.glyphSet.height - 1}", "y")
        }
        return spec
    }

    private fun imageRef(id: String?, images: Map<String, GlyphSet>): GlyphSet {
        if (id == null) throw DefinitionException("missing", "image")
        return images[id] ?: throw DefinitionException("unknown image '$id'", "image")
    }

    private fun layoutRef(id: String, layouts: Map<String, LayoutSpec>, definitions: DefinitionSet): LayoutSpec =
        layouts[id] ?: throw DefinitionException(
            if (id in definitions.layouts) "layout '$id' has errors" else "unknown layout '$id'", "layout",
        )

    private fun conditions(sources: List<String>?, field: String, textIds: Set<String> = emptySet()): List<Expr> =
        sources.orEmpty().mapIndexed { i, source -> expr("$field[$i]", source, textIds, true)!! }

    private fun expr(field: String, source: String?, textIds: Set<String>, required: Boolean): Expr? {
        if (source == null) {
            if (required) throw DefinitionException("missing", field)
            return null
        }
        val expression = wrap(field) { ExpressionParser.parse(source) }
        expression.placeholders().filter { it.name == "width" }.forEach {
            if (it.argument !in textIds) throw DefinitionException("unknown text element '${it.argument}' in [width:…]", field)
        }
        return expression
    }

    private inline fun <T> wrap(field: String, block: () -> T): T = try {
        block()
    } catch (e: ExpressionException) {
        throw DefinitionException(e.message ?: "invalid", field)
    }

    private fun color(source: String?): TextColor {
        if (source == null) return NamedTextColor.WHITE
        return TextColor.fromHexString(source) ?: NamedTextColor.NAMES.value(source)
            ?: throw DefinitionException("invalid color '$source' (expected #RRGGBB or a color name)", "color")
    }

    private fun checkId(id: String) {
        if (!ID.matches(id)) throw DefinitionException("invalid id '$id', must match [a-z0-9_-]+")
    }

    private fun checkElementId(id: String) {
        if (!ID.matches(id)) throw DefinitionException("invalid element id '$id', must match [a-z0-9_-]+", "id")
    }

    private fun anchors() = Anchor.entries.joinToString { it.name.lowercase() }

    private companion object {
        val ID = Regex("[a-z0-9_-]+")
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.model.*"`
Expected: PASS. If an assertion on an exact error string fails only because of wording, align the implementation's message with the test (the test strings are the contract).

- [ ] **Step 5: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): compile definitions into a HUD model with shader slots" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Pack generation

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/pack/{TextShaderPatcher.kt,ShaderInclude.kt,PackGenerator.kt,PackWriter.kt}`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/pack/{TextShaderPatcherTest.kt,PackGeneratorTest.kt}`

**Interfaces:**
- Consumes: `HudModel` (Task 6).
- Produces:
  - `class PackException(message: String) : RuntimeException`
  - `object TextShaderPatcher { fun patch(vanilla: String, versionId: String, include: String, transformFunction: String): String }`
  - `object ShaderInclude { fun generate(model: HudModel): String }`
  - `class PackGenerator(model: HudModel) { fun build(): ResourcePack }`
  - `object PackWriter { fun write(pack: ResourcePack, zip: Path, folder: Path): String /* SHA-1 hex */ }`, which also writes `<zip name without extension>.sha1`

- [ ] **Step 1: Write the failing tests**

`TextShaderPatcherTest.kt`:

```kotlin
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
```

`PackGeneratorTest.kt`:

```kotlin
package dev.slne.surf.hud.core.pack

import com.google.gson.JsonParser
import dev.slne.surf.hud.core.TestDefinitions
import dev.slne.surf.hud.core.model.HudEncoding
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.*

class PackGeneratorTest {
    @TempDir lateinit var root: Path

    private fun generate(): Pair<Path, String> {
        val model = TestDefinitions.compile(root)
        val folder = root.resolve("build/pack")
        val hash = PackWriter.write(PackGenerator(model).build(), root.resolve("build/surf-hud.zip"), folder)
        return folder to hash
    }

    @Test fun `writes mcmeta with the client pack format`() {
        val (folder, _) = generate()
        val pack = JsonParser.parseString(folder.resolve("pack.mcmeta").readText()).asJsonObject.getAsJsonObject("pack")
        assertEquals(97, pack["min_format"].asInt)
        assertEquals(97, pack["max_format"].asInt)
    }

    @Test fun `writes fonts with slot encoded ascents and spaces`() {
        val (folder, _) = generate()
        val font = JsonParser.parseString(folder.resolve("assets/surf/font/hud/s1_3_default.json").readText()).asJsonObject
        val providers = font.getAsJsonArray("providers").map { it.asJsonObject }
        val ascii = providers.single { it["type"].asString == "bitmap" }
        assertEquals("minecraft:font/ascii.png", ascii["file"].asString)
        assertEquals(HudEncoding.ascent(1, 3), ascii["ascent"].asInt)
        assertTrue(providers.any { it["type"].asString == "space" && it.getAsJsonObject("advances").has("") })
    }

    @Test fun `pixel rows are one size lower each`() {
        val (folder, _) = generate()
        val providers = JsonParser.parseString(folder.resolve("assets/surf/font/hud/s3_0.json").readText()).asJsonObject
            .getAsJsonArray("providers").map { it.asJsonObject }.filter { it["type"].asString == "bitmap" }
        assertEquals(8, providers.size)
        assertEquals(HudEncoding.ascent(3, 2), providers[1]["ascent"].asInt)
    }

    @Test fun `writes textures shader and blank bossbar`() {
        val (folder, _) = generate()
        assertTrue(folder.resolve("assets/surf/textures/font/hud/coin.png").exists())
        assertTrue(folder.resolve("assets/surf/textures/font/hud/panel/middle_256.png").exists())
        assertTrue(folder.resolve("assets/surf/textures/font/hud/head_small/pixel.png").exists())
        assertTrue(folder.resolve("assets/minecraft/textures/gui/sprites/boss_bar/pink_background.png").exists())
        assertContains(folder.resolve("assets/minecraft/shaders/core/text.vsh").readText(), "surf_hud_transform(Position)")
        val include = folder.resolve("assets/surf/shaders/include/hud.glsl").readText()
        assertContains(include, "const int SURF_HUD_SLOT_COUNT = 6;")
        assertContains(include, "vec4(1.0, 1.0, -4.0, -17.0), // slot 1")
    }

    @Test fun `zip and sha1 match`() {
        val (_, hash) = generate()
        assertEquals(40, hash.length)
        assertEquals(hash, root.resolve("build/surf-hud.sha1").readText())
        ZipFile(root.resolve("build/surf-hud.zip").toFile()).use { assertNotNull(it.getEntry("pack.mcmeta")) }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.pack.*"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`TextShaderPatcher.kt` (ported from `build-logic/.../TextShaderPatcher.kt`, throwing `PackException` instead of `GradleException`):

```kotlin
package dev.slne.surf.hud.core.pack

/** Thrown when the pack cannot be generated, e.g. because the vanilla shader changed. */
class PackException(message: String) : RuntimeException(message)

/**
 * Patches vanilla `core/text.vsh` with the two hooks the HUD needs, so the rest of the shader stays whatever
 * the target Minecraft version ships: an `#include` of the generated HUD include right after the projection
 * include, and the vertex position passed through the HUD transform before projection. Each anchor must match
 * exactly once; otherwise generation fails with the anchor's name.
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
            throw PackException(
                "Cannot patch core/text.vsh of Minecraft $versionId: expected the $name anchor once, found it $count times. " +
                    "Compare the new vanilla shader with TextShaderPatcher and adjust the anchor."
            )
        }
    }
}
```

`ShaderInclude.kt`:

```kotlin
package dev.slne.surf.hud.core.pack

import dev.slne.surf.hud.core.model.HudEncoding
import dev.slne.surf.hud.core.model.HudModel

/** Generates `<namespace>:hud.glsl`: encoding constants, the per-slot placement table and the vertex transform. */
object ShaderInclude {
    fun generate(model: HudModel): String {
        // GLSL forbids empty arrays; a model without slots still gets one unused entry.
        val slots = model.slots.ifEmpty { null }
        val count = slots?.size ?: 1
        val table = slots?.mapIndexed { index, slot ->
            val separator = if (index < slots.size - 1) "," else ""
            "    vec4(${slot.anchor.x}, ${slot.anchor.y}, ${slot.offsetX}.0, ${slot.offsetY}.0)$separator // slot ${slot.index}"
        }?.joinToString("\n") ?: "    vec4(0.0, 0.0, 0.0, 0.0) // unused"
        // Comments stay outside the vec4 list items' syntax: a comma inside a comment once broke compilation.
        return """
            |#ifndef SURF_HUD_GLSL
            |#define SURF_HUD_GLSL
            |
            |// Generated by surf-hud; regenerate instead of editing.
            |// Needs ProjMat (minecraft:projection.glsl) to be declared before this include.
            |
            |const float SURF_HUD_BASE = ${HudEncoding.BASE}.0;
            |const float SURF_HUD_STRIDE = ${HudEncoding.STRIDE}.0;
            |const float SURF_HUD_BIAS = ${HudEncoding.BIAS}.0;
            |const float SURF_HUD_LINE_Y = ${HudEncoding.bossbarLineY(0)}.0;
            |const int SURF_HUD_SLOT_COUNT = ${if (slots == null) 0 else count};
            |
            |// Per slot: anchor x/y as a fraction of the GUI size, then the offset from the anchor in GUI pixels.
            |const vec4 SURF_HUD_SLOTS[$count] = vec4[$count](
            |$table
            |);
            |
            |// Moves HUD glyphs (identified by their encoded y) from below the screen to their slot's anchor.
            |// Text on the HUD bossbar row has a total advance of 0, so the line starts at the GUI center.
            |vec3 surf_hud_transform(vec3 position) {
            |    // Only GUI rendering uses the orthographic projection that maps x = 0 to -1.
            |    if (ProjMat[3].x != -1.0) {
            |        return position;
            |    }
            |    float encoded = position.y - SURF_HUD_LINE_Y - SURF_HUD_BASE;
            |    if (encoded < 0.0) {
            |        return position;
            |    }
            |    int slot = int(floor(encoded / SURF_HUD_STRIDE));
            |    if (slot >= SURF_HUD_SLOT_COUNT) {
            |        return position;
            |    }
            |    vec2 gui = round(2.0 / vec2(ProjMat[0][0], -ProjMat[1][1]));
            |    vec4 placement = SURF_HUD_SLOTS[slot];
            |    float localX = position.x - floor(gui.x * 0.5);
            |    float localY = encoded - float(slot) * SURF_HUD_STRIDE - SURF_HUD_BIAS;
            |    return vec3(placement.x * gui.x + placement.z + localX, placement.y * gui.y + placement.w + localY, position.z);
            |}
            |
            |#endif
            |""".trimMargin()
    }
}
```

`PackGenerator.kt`:

```kotlin
package dev.slne.surf.hud.core.pack

import dev.slne.surf.hud.core.model.*
import net.kyori.adventure.key.Key
import team.unnamed.creative.ResourcePack
import team.unnamed.creative.base.Writable
import team.unnamed.creative.font.Font
import team.unnamed.creative.font.FontProvider
import team.unnamed.creative.texture.Texture
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * Builds the HUD resource pack for a [HudModel]: pack.mcmeta, glyph textures, one font per [FontSpec], the
 * patched `core/text.vsh` with its generated include, and blank bossbar sprites for the HUD bossbar color.
 */
class PackGenerator(private val model: HudModel) {
    private val ns = model.settings.namespace

    fun build(): ResourcePack {
        val pack = ResourcePack.resourcePack()
        pack.unknownFile("pack.mcmeta", Writable.stringUtf8(packMeta()))
        model.glyphSets.values.forEach { textures(pack, it) }
        model.fonts.values.forEach { font -> pack.font(Font.font(font.key, providers(font))) }
        val patched = TextShaderPatcher.patch(model.assets.textVsh, model.assets.versionId, "$ns:hud.glsl", "surf_hud_transform")
        pack.unknownFile("assets/minecraft/shaders/core/text.vsh", Writable.stringUtf8(patched))
        pack.unknownFile("assets/$ns/shaders/include/hud.glsl", Writable.stringUtf8(ShaderInclude.generate(model)))
        val blank = png(BufferedImage(182, 5, BufferedImage.TYPE_INT_ARGB))
        val color = model.settings.bossbarColor
        for (part in listOf("background", "progress")) {
            pack.texture(Texture.texture(Key.key("minecraft", "gui/sprites/boss_bar/${color}_$part.png"), Writable.bytes(blank)))
        }
        return pack
    }

    /** Formats from 65 on use min/max_format; older ones only understand pack_format. */
    private fun packMeta(): String {
        val format = model.assets.packFormat
        val formats = if (format >= 65) "\"min_format\":$format,\"max_format\":$format" else "\"pack_format\":$format"
        return """{"pack":{"description":"surf-hud",$formats}}"""
    }

    private fun textures(pack: ResourcePack, set: GlyphSet) {
        when (set) {
            is AsciiSet -> {}
            is ImageSet -> pack.texture(Texture.texture(textureKey(set, set.glyph), Writable.bytes(png(set.glyph.texture))))
            is StripSet -> (listOf(set.left, set.right) + set.middles).forEach {
                pack.texture(Texture.texture(textureKey(set, it), Writable.bytes(png(it.texture))))
            }
            is PixelSet -> pack.texture(Texture.texture(pixelKey(set), Writable.bytes(png(set.texture))))
        }
    }

    private fun providers(font: FontSpec): List<FontProvider> = buildList {
        val ascent = { localY: Int -> HudEncoding.ascent(font.slot, localY) }
        for (set in font.glyphSets) {
            when (set) {
                is AsciiSet -> {
                    // The space provider must come first: ascii.png also has a (blank) cell for ' ', and the
                    // first provider that knows a character wins.
                    add(FontProvider.space(mapOf(" " to set.advances.getValue(' '))))
                    add(FontProvider.bitMap(Key.key("minecraft", "font/ascii.png"), set.height, ascent(font.y), set.rows))
                }
                is ImageSet -> add(bitmap(textureKey(set, set.glyph), set.glyph, ascent(font.y)))
                is StripSet -> (listOf(set.left, set.right) + set.middles).forEach { add(bitmap(textureKey(set, it), it, ascent(font.y))) }
                is PixelSet -> set.rows.forEachIndexed { row, char ->
                    add(FontProvider.bitMap(pixelKey(set), set.size, ascent(font.y + row * set.size), listOf(char.toString())))
                }
            }
        }
        add(FontProvider.space(SpaceGlyphs.advancesByChar))
    }

    private fun bitmap(file: Key, glyph: Glyph, ascent: Int) =
        FontProvider.bitMap(file, glyph.height, ascent, listOf(glyph.char.toString()))

    private fun textureKey(set: GlyphSet, glyph: Glyph): Key = when (set) {
        is StripSet -> Key.key(ns, "font/hud/${set.id}/${glyph.name}.png")
        else -> Key.key(ns, "font/hud/${set.id}.png")
    }

    private fun pixelKey(set: PixelSet) = Key.key(ns, "font/hud/${set.id}/pixel.png")

    private fun png(image: BufferedImage): ByteArray =
        ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
}
```

`PackWriter.kt`:

```kotlin
package dev.slne.surf.hud.core.pack

import team.unnamed.creative.ResourcePack
import team.unnamed.creative.serialize.minecraft.MinecraftResourcePackWriter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.io.path.*

/** Writes a pack as a zip and as an unzipped folder; returns the zip's SHA-1, also written next to it as `.sha1`. */
object PackWriter {
    fun write(pack: ResourcePack, zip: Path, folder: Path): String {
        val writer = MinecraftResourcePackWriter.minecraft()
        zip.parent?.createDirectories()
        val temp = zip.resolveSibling(zip.name + ".tmp")
        writer.writeToZipFile(temp, pack)
        Files.move(temp, zip, StandardCopyOption.REPLACE_EXISTING)

        folder.toFile().deleteRecursively()
        folder.createDirectories()
        writer.writeToDirectory(folder.toFile(), pack)

        val hash = MessageDigest.getInstance("SHA-1").digest(zip.readBytes()).joinToString("") { "%02x".format(it) }
        zip.resolveSibling(zip.nameWithoutExtension + ".sha1").writeText(hash)
        return hash
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.pack.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): generate the HUD resource pack with creative" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: Placeholder registry, cache and render scope

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/placeholder/{HudClock.kt,Warnings.kt,PlaceholderRegistryImpl.kt,PlaceholderCache.kt,RenderScope.kt,LegacyText.kt}`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/placeholder/{PlaceholderCacheTest.kt,LegacyTextTest.kt}`

**Interfaces:**
- Consumes: API `PlaceholderRegistry`/`PlaceholderResolver` (Task 1), `Value`/`PlaceholderRef`/`Scope` (Task 2).
- Produces:
  - `fun interface HudClock { fun millis(): Long; companion object { val SYSTEM: HudClock } }`
  - `class WarningSink(log: (String) -> Unit) { fun once(key: String, message: String) }`
  - `class ResolverErrorLog(clock: HudClock, log: (String, Throwable) -> Unit) { fun report(name: String, error: Throwable) }` (at most once per minute per name)
  - `class PlaceholderRegistryImpl : PlaceholderRegistry { operator fun get(name: String): Registration? }` with `data class Registration(name, interval, resolver)`
  - `class PlaceholderCache(registry, clock, defaultIntervals: Map<String, Long>, warnings, errors) { fun nextFrame(); fun resolve(viewer: UUID, ref: PlaceholderRef): Value }`
  - `class RenderScope(viewer: UUID, cache: PlaceholderCache, variables: Map<String, Value>, warnings: WarningSink, widths: Map<String, Int> = emptyMap()) : Scope { fun withWidths(widths: Map<String, Int>): RenderScope }`
  - `object LegacyText { fun value(text: String): Any }` (a `§`-coded string becomes a `Component`; otherwise the string)

- [ ] **Step 1: Write the failing tests**

`PlaceholderCacheTest.kt`:

```kotlin
package dev.slne.surf.hud.core.placeholder

import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.Value
import java.util.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class PlaceholderCacheTest {
    private var now = 0L
    private val clock = HudClock { now }
    private val registry = PlaceholderRegistryImpl()
    private val warnings = mutableListOf<String>()
    private val errors = mutableListOf<String>()
    private val cache = PlaceholderCache(
        registry, clock, mapOf("papi" to 1000L), WarningSink { warnings += it },
        ResolverErrorLog(clock) { name, _ -> errors += name },
    )
    private val viewer = UUID.randomUUID()

    @Test fun `resolves once per frame without interval`() {
        var calls = 0
        registry.register("money") { _, _ -> ++calls }
        assertEquals(Value.Num(1.0), cache.resolve(viewer, PlaceholderRef("money")))
        assertEquals(Value.Num(1.0), cache.resolve(viewer, PlaceholderRef("money")))
        cache.nextFrame()
        assertEquals(Value.Num(2.0), cache.resolve(viewer, PlaceholderRef("money")))
    }

    @Test fun `caches for the registered interval`() {
        var calls = 0
        registry.register("slow", 2.seconds) { _, _ -> ++calls }
        cache.resolve(viewer, PlaceholderRef("slow"))
        cache.nextFrame(); now = 1999
        assertEquals(Value.Num(1.0), cache.resolve(viewer, PlaceholderRef("slow")))
        now = 2000
        assertEquals(Value.Num(2.0), cache.resolve(viewer, PlaceholderRef("slow")))
    }

    @Test fun `bridge names use the default interval and arguments are separate entries`() {
        registry.register("papi") { _, argument -> argument + now }
        assertEquals(Value.Str("a0"), cache.resolve(viewer, PlaceholderRef("papi", "a")))
        assertEquals(Value.Str("b0"), cache.resolve(viewer, PlaceholderRef("papi", "b")))
        cache.nextFrame(); now = 500
        assertEquals(Value.Str("a0"), cache.resolve(viewer, PlaceholderRef("papi", "a")))
    }

    @Test fun `unknown placeholder is missing and warns once`() {
        repeat(3) { cache.nextFrame(); assertEquals(Value.Missing, cache.resolve(viewer, PlaceholderRef("nope"))) }
        assertEquals(1, warnings.size)
    }

    @Test fun `throwing resolver is missing and logged at most once per minute`() {
        registry.register("bad") { _, _ -> error("boom") }
        repeat(3) { cache.nextFrame(); assertEquals(Value.Missing, cache.resolve(viewer, PlaceholderRef("bad"))) }
        now = 60_000; cache.nextFrame(); cache.resolve(viewer, PlaceholderRef("bad"))
        assertEquals(listOf("bad", "bad"), errors)
    }

    @Test fun `names are case insensitive and validated`() {
        registry.register("Money") { _, _ -> 1 }
        assertTrue(registry.isRegistered("money"))
        assertFailsWith<IllegalArgumentException> { registry.register("width") { _, _ -> 1 } }
        assertFailsWith<IllegalArgumentException> { registry.register("a b") { _, _ -> 1 } }
    }

    @Test fun `scope prefers variables then widths then registry`() {
        registry.register("left") { _, _ -> "registry" }
        val scope = RenderScope(viewer, cache, mapOf("left" to Value.Str("variable")), WarningSink {}).withWidths(mapOf("t" to 12))
        assertEquals(Value.Str("variable"), scope.resolve(PlaceholderRef("left")))
        assertEquals(Value.Num(12.0), scope.resolve(PlaceholderRef("width", "t")))
        assertEquals(Value.Missing, scope.resolve(PlaceholderRef("width", "other")))
    }
}
```

`LegacyTextTest.kt`:

```kotlin
package dev.slne.surf.hud.core.placeholder

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import kotlin.test.*

class LegacyTextTest {
    @Test fun `legacy section text becomes a component`() {
        val value = LegacyText.value("§aGreen")
        assertIs<Component>(value)
        assertEquals(NamedTextColor.GREEN, value.children().firstOrNull()?.color() ?: value.color())
    }

    @Test fun `plain text stays a string`() = assertEquals("1500", LegacyText.value("1500"))
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.placeholder.*"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`HudClock.kt`:

```kotlin
package dev.slne.surf.hud.core.placeholder

/** Wall clock in milliseconds; replaced in tests. */
fun interface HudClock {
    fun millis(): Long

    companion object {
        val SYSTEM = HudClock { System.currentTimeMillis() }
    }
}
```

`Warnings.kt`:

```kotlin
package dev.slne.surf.hud.core.placeholder

import java.util.concurrent.ConcurrentHashMap

/** Logs each warning key only once, so a broken HUD does not flood the log every update. */
class WarningSink(private val log: (String) -> Unit) {
    private val seen = ConcurrentHashMap.newKeySet<String>()

    fun once(key: String, message: String) {
        if (seen.add(key)) log(message)
    }
}

/** Logs resolver failures at most once per minute per placeholder name. */
class ResolverErrorLog(private val clock: HudClock, private val log: (String, Throwable) -> Unit) {
    private val last = ConcurrentHashMap<String, Long>()

    fun report(name: String, error: Throwable) {
        val now = clock.millis()
        val previous = last[name]
        if (previous == null || now - previous >= 60_000) {
            last[name] = now
            log(name, error)
        }
    }
}
```

`PlaceholderRegistryImpl.kt`:

```kotlin
package dev.slne.surf.hud.core.placeholder

import dev.slne.surf.hud.api.PlaceholderRegistry
import dev.slne.surf.hud.api.PlaceholderResolver
import dev.slne.surf.hud.core.expression.PlaceholderRef
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

class PlaceholderRegistryImpl : PlaceholderRegistry {
    data class Registration(val name: String, val interval: Duration?, val resolver: PlaceholderResolver)

    private val registrations = ConcurrentHashMap<String, Registration>()

    override fun register(name: String, interval: Duration?, resolver: PlaceholderResolver) {
        require(PlaceholderRef.NAME.matches(name)) { "Invalid placeholder name '$name'" }
        require(!name.equals("width", ignoreCase = true)) { "'width' is reserved for element widths" }
        registrations[name.lowercase()] = Registration(name.lowercase(), interval, resolver)
    }

    override fun unregister(name: String) {
        registrations.remove(name.lowercase())
    }

    override fun isRegistered(name: String) = name.lowercase() in registrations

    operator fun get(name: String): Registration? = registrations[name.lowercase()]
}
```

`PlaceholderCache.kt`:

```kotlin
package dev.slne.surf.hud.core.placeholder

import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.Value
import java.util.*

/**
 * Per-player placeholder values. A placeholder without interval is resolved at most once per frame (one HUD
 * update); one with an interval (registered, or from [defaultIntervals] by name) is reused until it expires.
 * Not thread-safe; the owning player state serializes access.
 */
class PlaceholderCache(
    private val registry: PlaceholderRegistryImpl,
    private val clock: HudClock,
    private val defaultIntervals: Map<String, Long>,
    private val warnings: WarningSink,
    private val errors: ResolverErrorLog,
) {
    private class Entry(val value: Value, val frame: Long, val expiresAt: Long)

    private val entries = HashMap<PlaceholderRef, Entry>()
    private var frame = 0L

    fun nextFrame() {
        frame++
    }

    fun resolve(viewer: UUID, ref: PlaceholderRef): Value {
        val registration = registry[ref.name] ?: run {
            warnings.once("unknown:${ref.name}", "Unknown placeholder [${ref.name}]")
            return Value.Missing
        }
        val now = clock.millis()
        val interval = registration.interval?.inWholeMilliseconds ?: defaultIntervals[ref.name] ?: 0L
        entries[ref]?.let { entry ->
            val fresh = if (interval > 0) entry.expiresAt > now else entry.frame == frame
            if (fresh) return entry.value
        }
        val value = try {
            Value.of(registration.resolver.resolve(viewer, ref.argument))
        } catch (e: Exception) {
            errors.report(ref.name, e)
            Value.Missing
        }
        entries[ref] = Entry(value, frame, now + interval)
        return value
    }
}
```

`RenderScope.kt`:

```kotlin
package dev.slne.surf.hud.core.placeholder

import dev.slne.surf.hud.core.expression.Expr
import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.Scope
import dev.slne.surf.hud.core.expression.Value
import java.util.*

/**
 * Placeholder lookup while rendering one layout: popup [variables] first, then `[width:id]` from [widths],
 * then registered placeholders through the player's [cache].
 */
class RenderScope(
    private val viewer: UUID,
    private val cache: PlaceholderCache,
    private val variables: Map<String, Value>,
    private val warnings: WarningSink,
    private val widths: Map<String, Int> = emptyMap(),
) : Scope {
    override fun resolve(ref: PlaceholderRef): Value {
        if (ref.argument == null) variables[ref.name]?.let { return it }
        if (ref.name == "width") return ref.argument?.let(widths::get)?.let { Value.Num(it.toDouble()) } ?: Value.Missing
        return cache.resolve(viewer, ref)
    }

    fun withWidths(widths: Map<String, Int>) = RenderScope(viewer, cache, variables, warnings, widths)

    override fun warn(expression: Expr, message: String) = warnings.once("expr:$expression:$message", "$message in $expression")
}
```

`LegacyText.kt`:

```kotlin
package dev.slne.surf.hud.core.placeholder

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer

/** Converts bridge output: text with `§` color codes becomes a component, anything else stays a string. */
object LegacyText {
    fun value(text: String): Any =
        if ('§' in text) LegacyComponentSerializer.legacySection().deserialize(text) else text
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.placeholder.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): add placeholder registry, cache and render scope" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: Text rendering

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/render/TextRenderer.kt`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/render/TextRendererTest.kt`

**Interfaces:**
- Consumes: `TextTemplate`, `Scope`, `Value` (Task 2), `AsciiSet` (Task 5), `WarningSink` (Task 8).
- Produces:
  - `data class TextRun(val text: String, val color: TextColor, val bold: Boolean, val width: Int)`
  - `data class RenderedText(val runs: List<TextRun>, val width: Int)`
  - `class TextRenderer(warnings: WarningSink) { fun render(template: TextTemplate, font: AsciiSet, scope: Scope): RenderedText }`

- [ ] **Step 1: Write the failing test**

`TextRendererTest.kt`:

```kotlin
package dev.slne.surf.hud.core.render

import dev.slne.surf.hud.core.TestAssets
import dev.slne.surf.hud.core.config.TextDefinition
import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.Scope
import dev.slne.surf.hud.core.expression.TextTemplate
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.model.CharAllocator
import dev.slne.surf.hud.core.model.GlyphSetBuilder
import dev.slne.surf.hud.core.placeholder.WarningSink
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import java.nio.file.Path
import kotlin.test.*

class TextRendererTest {
    private val font = GlyphSetBuilder(TestAssets.vanilla(), Path.of("."), CharAllocator()).text("default", TextDefinition())
    private val warnings = mutableListOf<String>()
    private val renderer = TextRenderer(WarningSink { warnings += it })

    private fun render(text: String, vararg values: Pair<String, Any?>): RenderedText {
        val map = values.associate { (k, v) -> PlaceholderRef(k) to Value.of(v) }
        return renderer.render(TextTemplate.parse(text), font, Scope { map[it] ?: Value.Missing })
    }

    @Test fun `measures and splits runs by color`() {
        val text = render("<gold>Ai</gold> A")
        assertEquals(listOf("Ai", " A"), text.runs.map { it.text })
        assertEquals(NamedTextColor.GOLD, text.runs[0].color)
        assertEquals(NamedTextColor.WHITE, text.runs[1].color)
        assertEquals(6 + 2 + 4 + 6, text.width)
    }

    @Test fun `bold adds one pixel except for spaces`() {
        val text = render("<bold>A A</bold>")
        assertEquals(7 + 4 + 7, text.width)
        assertEquals(listOf(true, false, true), text.runs.map { it.bold })
    }

    @Test fun `placeholder values are not parsed as MiniMessage`() {
        val text = render("[name]", "name" to "<red>x")
        assertEquals("<red>x", text.runs.joinToString("") { it.text })
        assertEquals(NamedTextColor.WHITE, text.runs.single().color)
    }

    @Test fun `component values keep their style`() {
        val text = render("[rank]", "rank" to Component.text("A", NamedTextColor.RED))
        assertEquals(NamedTextColor.RED, text.runs.single().color)
    }

    @Test fun `expressions are formatted`() {
        assertEquals("1,500", render("{[money] | #,##0}", "money" to 1500).runs.single().text)
        assertEquals("75", render("{[a] / [b] * 100 | 0}", "a" to 3, "b" to 4).runs.single().text)
        assertEquals("1.5", render("{[a]}", "a" to 1.5).runs.single().text)
    }

    @Test fun `characters outside the font become question marks`() {
        val text = render("[name]", "name" to "Jürgen 😀")
        assertEquals("J?rgen ?", text.runs.joinToString("") { it.text })
        assertTrue(warnings.isNotEmpty())
    }

    @Test fun `missing placeholders render as question mark`() {
        assertEquals("?", render("[nope]").runs.single().text)
    }

    @Test fun `unsupported decorations are ignored with a warning`() {
        render("<italic>A</italic>")
        assertTrue(warnings.any { "italic" in it })
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.render.TextRendererTest"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`TextRenderer.kt`:

```kotlin
package dev.slne.surf.hud.core.render

import dev.slne.surf.hud.core.expression.Scope
import dev.slne.surf.hud.core.expression.TextTemplate
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.model.AsciiSet
import dev.slne.surf.hud.core.placeholder.WarningSink
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.flattener.ComponentFlattener
import net.kyori.adventure.text.flattener.FlattenerListener
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.Style
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

/** A run of characters with one color and boldness, and its width in GUI pixels. */
data class TextRun(val text: String, val color: TextColor, val bold: Boolean, val width: Int)

data class RenderedText(val runs: List<TextRun>, val width: Int)

/**
 * Renders a [TextTemplate]: placeholder and expression values become MiniMessage tags whose content is
 * inserted unparsed (components as components), the result is flattened into colored runs and measured with
 * the font's advances. Characters the font lacks become `?`; italic, underline, strikethrough and obfuscated
 * are ignored with a warning.
 */
class TextRenderer(private val warnings: WarningSink) {
    private val miniMessage = MiniMessage.miniMessage()

    fun render(template: TextTemplate, font: AsciiSet, scope: Scope): RenderedText {
        val source = StringBuilder()
        val resolvers = ArrayList<TagResolver>()
        fun tag(index: Int, value: Value) {
            val name = "hud_v$index"
            resolvers += if (value is Value.Comp) Placeholder.component(name, value.value) else Placeholder.unparsed(name, value.text())
            source.append('<').append(name).append('>')
        }
        template.segments.forEachIndexed { index, segment ->
            when (segment) {
                is TextTemplate.Segment.Literal -> source.append(segment.miniMessage)
                is TextTemplate.Segment.Placeholder -> tag(index, scope.resolve(segment.ref))
                is TextTemplate.Segment.Expression -> {
                    val value = segment.expression.evaluate(scope)
                    val number = value.number()
                    tag(index, if (segment.format != null && number != null) Value.Str(format(segment.format, number)) else value)
                }
            }
        }
        return layout(miniMessage.deserialize(source.toString(), TagResolver.resolver(resolvers)), font)
    }

    private fun layout(component: Component, font: AsciiSet): RenderedText {
        val runs = ArrayList<TextRun>()
        val styles = ArrayDeque<Style>().apply { addLast(Style.empty()) }
        val current = StringBuilder()
        var currentColor: TextColor = NamedTextColor.WHITE
        var currentBold = false
        var currentWidth = 0
        var total = 0

        fun flush() {
            if (current.isNotEmpty()) runs += TextRun(current.toString(), currentColor, currentBold, currentWidth)
            current.clear()
            currentWidth = 0
        }

        fun emit(text: String, style: Style) {
            val color = style.color() ?: NamedTextColor.WHITE
            val bold = style.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE
            for (decoration in UNSUPPORTED) {
                if (style.decoration(decoration) == TextDecoration.State.TRUE) {
                    warnings.once("decoration:$decoration", "HUD text does not support ${decoration.name.lowercase()}; ignoring it")
                }
            }
            var i = 0
            while (i < text.length) {
                val codePoint = text.codePointAt(i)
                i += Character.charCount(codePoint)
                val char = if (codePoint <= 0xFFFF && font.advance(codePoint.toChar()) != null) codePoint.toChar() else {
                    warnings.once("char:$codePoint", "Character U+%04X is not in HUD font '%s'; drawing '?'".format(codePoint, font.id))
                    '?'
                }
                // Bold widens glyphs by 1 px; spaces are emitted non-bold so their width is exact.
                val charBold = bold && char != ' '
                if (current.isNotEmpty() && (color != currentColor || charBold != currentBold)) flush()
                currentColor = color
                currentBold = charBold
                val advance = font.advance(char)!! + if (charBold) 1 else 0
                current.append(char)
                currentWidth += advance
                total += advance
            }
        }

        ComponentFlattener.basic().flatten(component, object : FlattenerListener {
            override fun pushStyle(style: Style) {
                styles.addLast(style.merge(styles.last(), Style.Merge.Strategy.IF_ABSENT_ON_TARGET))
            }

            override fun component(text: String) = emit(text, styles.last())

            override fun popStyle(style: Style) {
                styles.removeLast()
            }
        })
        flush()
        return RenderedText(runs, total)
    }

    private fun format(pattern: String, number: Double) = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US)).format(number)

    private companion object {
        val UNSUPPORTED = listOf(TextDecoration.ITALIC, TextDecoration.UNDERLINED, TextDecoration.STRIKETHROUGH, TextDecoration.OBFUSCATED)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.render.TextRendererTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): render MiniMessage HUD texts into measured runs" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Glyph writer and layout renderer

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/head/HeadPixels.kt`
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/render/{GlyphWriter.kt,LayoutRenderer.kt}`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/render/LayoutRendererTest.kt`

**Interfaces:**
- Consumes: `HudModel`, specs (Task 6), `TextRenderer` (Task 9), `RenderScope` (Task 8), `SpaceGlyphs` (Task 5).
- Produces:
  - `class HeadPixels(val argb: Array<IntArray>) { fun dimmed(factor: Double): HeadPixels; companion fun placeholder(seed: String): HeadPixels }` (8×8, `[row][column]`)
  - `data class HeadKey(val slot: Int, val element: Int)`
  - `class GlyphWriter(spaceFont: Key) { val cursor: Int; val width: Int; fun glyphs(font, text, advance, color, shadow, bold); fun moveTo(target: Int); fun build(align: HAlign): Component }`
  - `class LayoutRenderer(model: HudModel, text: TextRenderer) { fun render(slot: Int, layout: LayoutSpec, scope: RenderScope, heads: (HeadKey, String) -> HeadPixels): Component }`

- [ ] **Step 1: Write the failing test**

`LayoutRendererTest.kt`:

```kotlin
package dev.slne.surf.hud.core.render

import dev.slne.surf.hud.core.TestDefinitions
import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.head.HeadPixels
import dev.slne.surf.hud.core.model.*
import dev.slne.surf.hud.core.placeholder.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.TextComponent
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import kotlin.test.*

class LayoutRendererTest {
    @TempDir lateinit var root: Path

    private lateinit var model: HudModel
    private val values = HashMap<String, Any?>()
    private val warnings = WarningSink {}

    private fun scope(): RenderScope {
        val registry = PlaceholderRegistryImpl()
        listOf("money", "health", "max_health", "target").forEach { name -> registry.register(name) { _, _ -> values[name] } }
        val cache = PlaceholderCache(registry, HudClock { 0 }, emptyMap(), warnings, ResolverErrorLog(HudClock { 0 }) { _, _ -> })
        return RenderScope(UUID.randomUUID(), cache, emptyMap(), warnings)
    }

    /** Sums the advance of every character using the model's glyph sets and the space glyphs. */
    private fun advance(component: Component): Int {
        val advances = HashMap<Char, Int>()
        SpaceGlyphs.advancesByChar.forEach { (c, a) -> advances[c[0]] = a }
        model.glyphSets.values.forEach { set ->
            when (set) {
                is ImageSet -> advances[set.glyph.char] = set.glyph.advance
                is StripSet -> (listOf(set.left, set.right) + set.middles).forEach { advances[it.char] = it.advance }
                is PixelSet -> set.rows.forEach { advances[it] = set.advance }
                is AsciiSet -> {}
            }
        }
        val ascii = model.glyphSets.getValue("default") as AsciiSet
        fun walk(c: Component): Int = ((c as? TextComponent)?.content()?.sumOf { ch ->
            val bold = c.decoration(net.kyori.adventure.text.format.TextDecoration.BOLD) == net.kyori.adventure.text.format.TextDecoration.State.TRUE
            advances[ch] ?: (ascii.advance(ch)!! + if (bold) 1 else 0)
        } ?: 0) + c.children().sumOf(::walk)
        return walk(component)
    }

    private fun render(layoutId: String): Component {
        model = if (::model.isInitialized) model else TestDefinitions.compile(root)
        val placement = model.huds.values.flatMap { it.placements }.firstOrNull { it.layout.id == layoutId }
            ?: model.popups.values.flatMap { it.stacks.flatten() }.first { it.layout.id == layoutId }
        return LayoutRenderer(model, TextRenderer(warnings)).render(placement.slot, placement.layout, scope()) { _, _ -> HeadPixels.placeholder("x") }
    }

    @Test fun `every layout has zero total advance`() {
        values += mapOf("money" to 1500, "health" to 10, "max_health" to 20, "target" to "Notch")
        for (layout in listOf("money", "health", "head")) assertEquals(0, advance(render(layout)), layout)
    }

    @Test fun `panel width follows the text width`() {
        values["money"] = "AAA"
        val component = render("money")
        val strip = model.glyphSets.getValue("panel") as StripSet
        val text = (component.children().map { it as TextComponent }).joinToString("") { it.content() }
        // 3 * 6 px text + 14 = 32 px panel: left + 16 + 8 + 4 + 2 + right
        val middles = text.filter { c -> strip.middles.any { it.char == c } }.map { c -> strip.middles.first { it.char == c }.width }
        assertEquals(listOf(16, 8, 4, 2), middles)
    }

    @Test fun `bar fill follows the value`() {
        values += mapOf("health" to 15, "max_health" to 20)
        val strip = model.glyphSets.getValue("panel") as StripSet
        val text = render("health").children().joinToString("") { (it as TextComponent).content() }
        val drawn = text.filter { c -> c == strip.left.char || c == strip.right.char || strip.middles.any { it.char == c } }
        val width = drawn.sumOf { c -> (listOf(strip.left, strip.right) + strip.middles).first { it.char == c }.width }
        assertEquals(45, width)
    }

    @Test fun `invalid widths draw nothing`() {
        values += mapOf("health" to "abc", "max_health" to 0)
        assertEquals(Component.empty(), render("health"))
        values += mapOf("health" to -5, "max_health" to 20)
        assertEquals(Component.empty(), render("health"))
    }

    @Test fun `heads draw one tinted glyph per opaque pixel`() {
        values["target"] = "Notch"
        val component = render("head")
        val pixels = model.glyphSets.getValue("head_small") as PixelSet
        val glyphs = component.children().sumOf { (it as TextComponent).content().count { c -> c in pixels.rows } }
        assertEquals(64, glyphs)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.render.LayoutRendererTest"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`head/HeadPixels.kt`:

```kotlin
package dev.slne.surf.hud.core.head

/** An 8x8 head face as ARGB pixels, indexed `[row][column]`, hat layer already composited. */
class HeadPixels(val argb: Array<IntArray>) {
    init {
        require(argb.size == 8 && argb.all { it.size == 8 }) { "Head must be 8x8" }
    }

    /** The same head with every color multiplied by [factor] (0..1). */
    fun dimmed(factor: Double): HeadPixels {
        if (factor >= 1.0) return this
        val f = factor.coerceIn(0.0, 1.0)
        return HeadPixels(Array(8) { row ->
            IntArray(8) { column ->
                val pixel = argb[row][column]
                val r = (((pixel shr 16) and 0xFF) * f).toInt()
                val g = (((pixel shr 8) and 0xFF) * f).toInt()
                val b = ((pixel and 0xFF) * f).toInt()
                (pixel and 0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
            }
        })
    }

    companion object {
        /** A plain two-tone face, colored by [seed]; used when no fallback image exists. */
        fun placeholder(seed: String): HeadPixels {
            val skin = 0xFFC8A07A.toInt()
            val hair = 0xFF000000.toInt() or (seed.hashCode() and 0x7F7F7F)
            return HeadPixels(Array(8) { row ->
                IntArray(8) { column ->
                    when {
                        row < 2 -> hair
                        row == 3 && (column == 1 || column == 6) -> 0xFF222244.toInt()
                        row == 6 && column in 2..5 -> 0xFF7A4A3A.toInt()
                        else -> skin
                    }
                }
            })
        }
    }
}
```

`render/GlyphWriter.kt`:

```kotlin
package dev.slne.surf.hud.core.render

import dev.slne.surf.hud.core.model.HAlign
import dev.slne.surf.hud.core.model.SpaceGlyphs
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.ShadowColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration

/**
 * Builds the component of one placed layout. Glyphs are laid out left to right from x = 0; [moveTo] jumps
 * with space glyphs, which is how backgrounds end up behind text. [build] shifts the layout for its alignment
 * and returns the cursor to 0, so the component's total advance is 0 and the shader can recover each glyph's x.
 * Runs split only where font, color, shadow or boldness change. Spaces are never added to bold runs, because
 * bold could widen them.
 */
class GlyphWriter(private val spaceFont: Key) {
    private class Run(val font: Key, val color: TextColor, val shadow: Boolean, val bold: Boolean) {
        val text = StringBuilder()
    }

    private val runs = ArrayList<Run>()

    var cursor = 0
        private set

    /** Widest x reached; the layout's width for alignment. */
    var width = 0
        private set

    fun glyphs(font: Key, text: String, advance: Int, color: TextColor = WHITE, shadow: Boolean = false, bold: Boolean = false) {
        if (text.isEmpty()) return
        val last = runs.lastOrNull()
        val run = if (last != null && last.font == font && last.color == color && last.shadow == shadow && last.bold == bold) last
        else Run(font, color, shadow, bold).also(runs::add)
        run.text.append(text)
        cursor += advance
        width = maxOf(width, cursor)
    }

    fun moveTo(target: Int) {
        if (target == cursor) return
        val last = runs.lastOrNull()
        val run = if (last != null && !last.bold) last else Run(last?.font ?: spaceFont, WHITE, false, false).also(runs::add)
        run.text.append(SpaceGlyphs.encode(target - cursor))
        cursor = target
        width = maxOf(width, cursor)
    }

    fun build(align: HAlign): Component {
        if (runs.none { run -> run.text.any { it !in SPACE_CHARS } }) return Component.empty()
        val start = when (align) {
            HAlign.LEFT -> 0
            HAlign.CENTER -> -width / 2
            HAlign.RIGHT -> -width
        }
        val parts = ArrayList<Component>(runs.size + 2)
        SpaceGlyphs.encode(start).takeIf { it.isNotEmpty() }?.let { parts += Component.text(it).font(spaceFont) }
        runs.forEach { run ->
            var part = Component.text(run.text.toString(), run.color).font(run.font).decoration(TextDecoration.BOLD, run.bold)
            if (!run.shadow) part = part.shadowColor(ShadowColor.none())
            parts += part
        }
        SpaceGlyphs.encode(-(start + cursor)).takeIf { it.isNotEmpty() }?.let { parts += Component.text(it).font(spaceFont) }
        return Component.text().append(parts).build()
    }

    private companion object {
        val WHITE: TextColor = NamedTextColor.WHITE
        val SPACE_CHARS = SpaceGlyphs.advancesByChar.keys.map { it[0] }.toSet()
    }
}
```

`render/LayoutRenderer.kt`:

```kotlin
package dev.slne.surf.hud.core.render

import dev.slne.surf.hud.core.expression.isTrue
import dev.slne.surf.hud.core.expression.number
import dev.slne.surf.hud.core.head.HeadPixels
import dev.slne.surf.hud.core.model.*
import dev.slne.surf.hud.core.placeholder.RenderScope
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import kotlin.math.roundToInt

/** Identifies a head element within a slot, so a head can be kept while its replacement loads. */
data class HeadKey(val slot: Int, val element: Int)

/**
 * Renders a placed layout into its zero-width component: texts first (their widths feed `[width:id]`), then
 * every visible element in definition order, so earlier elements are drawn below later ones.
 */
class LayoutRenderer(private val model: HudModel, private val text: TextRenderer) {
    fun render(slot: Int, layout: LayoutSpec, scope: RenderScope, heads: (HeadKey, String) -> HeadPixels): Component {
        val visible = layout.elements.filter { element -> element.conditions.all { it.isTrue(scope) } }
        val texts = visible.filterIsInstance<TextSpec>().associateWith { text.render(it.template, it.glyphSet, scope) }
        val widthScope = scope.withWidths(texts.entries.mapNotNull { (spec, rendered) -> spec.id?.let { it to rendered.width } }.toMap())
        val writer = GlyphWriter(model.slotFont(slot))
        for (element in visible) {
            val font = model.fontKey(slot, element)
            when (element) {
                is TextSpec -> {
                    val rendered = texts.getValue(element)
                    writer.moveTo(element.x - when (element.align) {
                        HAlign.LEFT -> 0
                        HAlign.CENTER -> rendered.width / 2
                        HAlign.RIGHT -> rendered.width
                    })
                    rendered.runs.forEach { writer.glyphs(font, it.text, it.width, it.color, element.shadow, it.bold) }
                }
                is ImageSpec -> {
                    writer.moveTo(element.x)
                    writer.glyphs(font, element.glyphSet.glyph.char.toString(), element.glyphSet.glyph.advance, element.color)
                }
                is StripSpec -> {
                    writer.moveTo(element.x)
                    strip(writer, font, element.glyphSet, pixels(element.width.number(widthScope)), element.color)
                }
                is BarSpec -> {
                    val width = pixels(element.width.number(widthScope))
                    val value = element.value.number(widthScope)?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0) ?: 0.0
                    val fill = (value * width).roundToInt()
                    writer.moveTo(if (element.direction == FillDirection.RIGHT) element.x else element.x + width - fill)
                    strip(writer, font, element.glyphSet, fill, element.color)
                    if (width > 0) writer.moveTo(element.x + width)
                }
                is HeadSpec -> {
                    val name = element.player.evaluate(widthScope).text()
                    val brightness = element.brightness.number(widthScope)?.takeIf { it.isFinite() } ?: 1.0
                    head(writer, font, element, heads(HeadKey(slot, element.index), name).dimmed(brightness))
                }
            }
        }
        return writer.build(layout.align)
    }

    /** A non-negative whole pixel count; anything invalid is 0. */
    private fun pixels(value: Double?): Int = value?.takeIf { it.isFinite() }?.roundToInt()?.coerceIn(0, 4096) ?: 0

    /** Draws [set] exactly [pixels] wide (edges included). Pieces must touch, so each takes back vanilla's 1 px gap. */
    private fun strip(writer: GlyphWriter, font: Key, set: StripSet, pixels: Int, color: TextColor) {
        if (pixels <= 0) return
        fun piece(glyph: Glyph) {
            writer.glyphs(font, glyph.char.toString(), glyph.advance, color)
            writer.moveTo(writer.cursor - 1)
        }
        if (pixels == 1) {
            piece(set.middles.last())
            return
        }
        piece(set.left)
        var remaining = pixels - 2
        for (glyph in set.middles) {
            while (remaining >= glyph.width) {
                piece(glyph)
                remaining -= glyph.width
            }
        }
        piece(set.right)
    }

    /** Draws a head one tinted pixel glyph per opaque pixel; row r uses the pixel set's r-th (lower) glyph. */
    private fun head(writer: GlyphWriter, font: Key, element: HeadSpec, pixels: HeadPixels) {
        val set = element.glyphSet
        writer.moveTo(element.x)
        val left = writer.cursor
        pixels.argb.forEachIndexed { row, line ->
            writer.moveTo(left)
            for (argb in line) {
                if ((argb ushr 24) == 0) {
                    writer.moveTo(writer.cursor + set.size)
                } else {
                    writer.glyphs(font, set.rows[row].toString(), set.advance, TextColor.color(argb and 0xFFFFFF))
                    writer.moveTo(writer.cursor - 1)
                }
            }
        }
        writer.moveTo(left + 8 * set.size)
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.render.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): render layouts into zero-width components" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: Head service

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/head/{SkinFace.kt,SkinSource.kt,HeadService.kt}`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/head/HeadServiceTest.kt`

**Interfaces:**
- Consumes: `HeadPixels` (Task 10), `HttpGet` (Task 4), `HudClock` (Task 8).
- Produces:
  - `object SkinFace { fun extract(skin: BufferedImage): HeadPixels }`
  - `interface SkinSource { suspend fun skinUrl(player: String): String? }`, `class MojangSkinSource(http: HttpGet) : SkinSource`
  - `class HeadService(sources: List<SkinSource>, http: HttpGet, cacheDir: Path, ttl: Duration, clock: HudClock, scope: CoroutineScope, val fallback: HeadPixels) { val version: Long; fun cached(player: String): HeadPixels?; fun request(player: String); suspend fun await(player: String, timeout: Duration): HeadPixels }`

- [ ] **Step 1: Write the failing test**

`HeadServiceTest.kt`:

```kotlin
package dev.slne.surf.hud.core.head

import dev.slne.surf.hud.core.placeholder.HudClock
import dev.slne.surf.hud.core.pack.HttpGet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.io.TempDir
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class HeadServiceTest {
    @TempDir lateinit var cache: Path
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var now = 0L

    private fun skin(face: Int, hat: Int?, legacy: Boolean = false): ByteArray {
        val image = BufferedImage(64, if (legacy) 32 else 64, BufferedImage.TYPE_INT_ARGB)
        for (x in 8 until 16) for (y in 8 until 16) image.setRGB(x, y, face)
        if (hat != null) for (x in 40 until 48) for (y in 8 until 16) image.setRGB(x, y, hat)
        if (legacy) for (x in 32 until 64) for (y in 0 until 16) if (image.getRGB(x, y) == 0) image.setRGB(x, y, 0xFF000000.toInt())
        return ByteArrayOutputStream().also { ImageIO.write(image, "png", it) }.toByteArray()
    }

    private fun service(skins: Map<String, ByteArray>, gate: CompletableDeferred<Unit>? = null): HeadService {
        val source = object : SkinSource {
            override suspend fun skinUrl(player: String): String? {
                gate?.await()
                return if (player in skins) "https://skin/$player" else null
            }
        }
        val http = HttpGet { url -> skins.getValue(url.substringAfterLast('/')) }
        return HeadService(listOf(source), http, cache, 1.days, HudClock { now }, scope, HeadPixels.placeholder("fallback"))
    }

    @Test fun `hat is composited over the face`() = runBlocking {
        val head = service(mapOf("Notch" to skin(0xFF112233.toInt(), 0xFF445566.toInt()))).await("Notch", 5.seconds)
        assertEquals(0xFF445566.toInt(), head.argb[0][0])
    }

    @Test fun `legacy skins ignore a fully opaque overlay`() = runBlocking {
        val head = service(mapOf("Old" to skin(0xFF112233.toInt(), null, legacy = true))).await("Old", 5.seconds)
        assertEquals(0xFF112233.toInt(), head.argb[0][0])
    }

    @Test fun `loads are cached in memory and on disk`() = runBlocking {
        val skins = mapOf("Notch" to skin(0xFF112233.toInt(), null))
        val first = service(skins)
        first.await("Notch", 5.seconds)
        assertNotNull(first.cached("notch"))
        assertTrue(first.version > 0)
        val second = service(emptyMap())
        assertNotNull(second.cached("Notch"))
    }

    @Test fun `unknown players and timeouts give the fallback`() = runBlocking {
        val service = service(emptyMap())
        assertSame(service.fallback, service.await("Nobody", 5.seconds))
        val gated = service(mapOf("Slow" to skin(0, null)), CompletableDeferred())
        assertSame(gated.fallback, gated.await("Slow", 50.milliseconds))
    }

    @Test fun `cache expires after the ttl`() = runBlocking {
        service(mapOf("Notch" to skin(0xFF112233.toInt(), null))).await("Notch", 5.seconds)
        now = 2.days.inWholeMilliseconds + System.currentTimeMillis()
        assertNull(service(emptyMap()).cached("Notch"))
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.head.*"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`SkinFace.kt`:

```kotlin
package dev.slne.surf.hud.core.head

import java.awt.image.BufferedImage

/** Cuts the 8x8 face out of a skin and composites the hat layer on top. */
object SkinFace {
    /** Face at (8, 8) and hat at (40, 8) in both 64x64 and legacy 64x32 skins (scaled for HD skins). */
    fun extract(skin: BufferedImage): HeadPixels {
        val scale = (skin.width / 64).coerceAtLeast(1)
        fun pixel(x: Int, y: Int) = skin.getRGB(x * scale, y * scale)
        // Like vanilla's "Notch transparency hack": legacy 64x32 skins often fill the overlay area with opaque
        // pixels, and vanilla ignores that layer when it has no transparent pixel at all.
        val legacy = skin.height == skin.width / 2
        val overlayOpaque = (32 until 64).all { x -> (0 until 16).all { y -> (pixel(x, y) ushr 24) >= 128 } }
        val useHat = !(legacy && overlayOpaque)
        return HeadPixels(Array(8) { row ->
            IntArray(8) { column ->
                val hat = pixel(40 + column, 8 + row)
                if (useHat && (hat ushr 24) >= 128) hat or 0xFF000000.toInt() else pixel(8 + column, 8 + row) or 0xFF000000.toInt()
            }
        })
    }
}
```

`SkinSource.kt`:

```kotlin
package dev.slne.surf.hud.core.head

import com.google.gson.JsonParser
import dev.slne.surf.hud.core.pack.HttpGet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*

/** Finds the skin texture URL of a player given by name or UUID; `null` when this source does not know them. */
interface SkinSource {
    suspend fun skinUrl(player: String): String?
}

/** Mojang API: name → UUID (skipped for UUIDs) → session profile → textures. */
class MojangSkinSource(private val http: HttpGet) : SkinSource {
    override suspend fun skinUrl(player: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val id = runCatching { UUID.fromString(player).toString().replace("-", "") }.getOrNull()
                ?: JsonParser.parseString(http.get("https://api.mojang.com/users/profiles/minecraft/$player").decodeToString())
                    .asJsonObject["id"].asString
            val session = JsonParser.parseString(http.get("https://sessionserver.mojang.com/session/minecraft/profile/$id").decodeToString()).asJsonObject
            val textures = session.getAsJsonArray("properties").map { it.asJsonObject }.first { it["name"].asString == "textures" }
            val decoded = JsonParser.parseString(Base64.getDecoder().decode(textures["value"].asString).decodeToString()).asJsonObject
            decoded.getAsJsonObject("textures").getAsJsonObject("SKIN")["url"].asString
        }.getOrNull()
    }
}
```

`HeadService.kt`:

```kotlin
package dev.slne.surf.hud.core.head

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.hud.core.pack.HttpGet
import dev.slne.surf.hud.core.placeholder.HudClock
import kotlinx.coroutines.*
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.time.Duration

/**
 * Loads and caches player heads. Lookups hit memory, then `cacheDir/<player>.png` (fresh for [ttl]); loads
 * run on [scope], try each [SkinSource] in order and are not retried for a minute after a failure.
 * [version] increases with every loaded head, so renderers know when to redraw.
 */
class HeadService(
    private val sources: List<SkinSource>,
    private val http: HttpGet,
    private val cacheDir: Path,
    private val ttl: Duration,
    private val clock: HudClock,
    private val scope: CoroutineScope,
    val fallback: HeadPixels,
) {
    private val log = logger()
    private val memory = ConcurrentHashMap<String, HeadPixels>()
    private val loading = ConcurrentHashMap<String, Deferred<HeadPixels?>>()
    private val failedAt = ConcurrentHashMap<String, Long>()

    @Volatile
    var version = 0L
        private set

    fun cached(player: String): HeadPixels? {
        val key = key(player) ?: return null
        memory[key]?.let { return it }
        val file = cacheDir.resolve("$key.png")
        if (!file.exists() || file.getLastModifiedTime().toMillis() + ttl.inWholeMilliseconds <= clock.millis()) return null
        val image = runCatching { ImageIO.read(file.toFile()) }.getOrNull() ?: return null
        return toPixels(image).also { memory[key] = it }
    }

    /** Starts loading [player] unless it is loading or failed within the last minute. */
    fun request(player: String) {
        start(player)
    }

    /** The cached head, or the loaded one, or [fallback] after [timeout] or on failure. */
    suspend fun await(player: String, timeout: Duration): HeadPixels =
        cached(player) ?: withTimeoutOrNull(timeout) { start(player)?.await() } ?: fallback

    private fun start(player: String): Deferred<HeadPixels?>? {
        val key = key(player) ?: return null
        failedAt[key]?.let { if (clock.millis() - it < 60_000) return null }
        return loading.computeIfAbsent(key) {
            scope.async {
                try {
                    load(player, key).also { if (it == null) failedAt[key] = clock.millis() }
                } finally {
                    loading.remove(key)
                }
            }
        }
    }

    private suspend fun load(player: String, key: String): HeadPixels? {
        for (source in sources) {
            val url = runCatching { source.skinUrl(player) }.getOrNull() ?: continue
            val skin = runCatching { withContext(Dispatchers.IO) { ImageIO.read(ByteArrayInputStream(http.get(url))) } }
                .onFailure { log.atWarning().withCause(it).log("Could not download the skin of %s", player) }
                .getOrNull() ?: continue
            val head = SkinFace.extract(skin)
            memory[key] = head
            withContext(Dispatchers.IO) { store(key, head) }
            version++
            return head
        }
        return null
    }

    private fun store(key: String, head: HeadPixels) {
        runCatching {
            cacheDir.createDirectories()
            val image = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
            for (row in 0 until 8) for (column in 0 until 8) image.setRGB(column, row, head.argb[row][column])
            ImageIO.write(image, "png", cacheDir.resolve("$key.png").toFile())
        }.onFailure { log.atWarning().withCause(it).log("Could not cache head %s", key) }
    }

    private fun toPixels(image: BufferedImage) = HeadPixels(Array(8) { row -> IntArray(8) { column -> image.getRGB(column, row) } })

    /** Cache key: lower-case name or UUID; `null` for values that are neither (e.g. blank or `?`). */
    private fun key(player: String): String? =
        player.trim().lowercase().takeIf { it.isNotEmpty() && it.all { c -> c.isLetterOrDigit() || c == '_' || c == '-' } }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.head.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): load and cache player heads" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: Player runtime, popups and engine

This task builds `PlayerHud` and `HudEngine` together because they call each other. The tests drive both through the public API.

**Files:**
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/platform/HudPlatform.kt`
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/runtime/{PopupInstance.kt,PlayerHud.kt}`
- Create: `surf-hud-core/src/main/kotlin/dev/slne/surf/hud/core/HudEngine.kt`
- Create (test support): `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/FakePlatform.kt`
- Test: `surf-hud-core/src/test/kotlin/dev/slne/surf/hud/core/runtime/PlayerHudTest.kt`

**Interfaces:**
- Consumes: everything from Tasks 2–11.
- Produces:
  - `interface HudPlatform { val dataFolder: Path; fun viewer(uuid: UUID): Audience?; fun execute(uuid: UUID, task: () -> Unit): Boolean }`
  - `open class HudEngine(platform: HudPlatform, reader: DefinitionReader, assetProvider: (root: Path, version: String) -> VanillaAssets, skinSources: List<SkinSource>, http: HttpGet, clock: HudClock = HudClock.SYSTEM) : SurfHud` with `val model: HudModel?`, `val scope: CoroutineScope`, `val placeholderRegistry: PlaceholderRegistryImpl`, `fun hud(uuid): PlayerHud`, `fun handleJoin(uuid)`, `fun handleQuit(uuid)`, `fun update(uuid)` (call once per tick on the player's thread), `fun generatePack(): String`, `fun shutdown()`
  - `class PlayerHud : HudPlayer` (internal API used by the engine: `update()`, `onModelChanged(model, first)`, `invalidate()`, `dispose()`, `closePopup(instance)`)

- [ ] **Step 1: Write the test support and the failing test**

`FakePlatform.kt`:

```kotlin
package dev.slne.surf.hud.core

import dev.slne.surf.hud.core.config.DefinitionReader
import dev.slne.surf.hud.core.pack.HttpGet
import dev.slne.surf.hud.core.placeholder.HudClock
import dev.slne.surf.hud.core.platform.HudPlatform
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import java.nio.file.Path
import java.util.*

class RecordingAudience : Audience {
    val bars = mutableListOf<BossBar>()
    override fun showBossBar(bar: BossBar) { bars += bar }
    override fun hideBossBar(bar: BossBar) { bars -= bar }
}

class FakePlatform(override val dataFolder: Path) : HudPlatform {
    val audiences = HashMap<UUID, RecordingAudience>()
    var online = true
    override fun viewer(uuid: UUID) = audiences[uuid]
    override fun execute(uuid: UUID, task: () -> Unit): Boolean {
        if (!online) return false
        task(); return true
    }
}

fun testEngine(
    platform: FakePlatform,
    clock: HudClock,
    reader: DefinitionReader = ConfigurateTestReader,
    assets: (Path, String) -> dev.slne.surf.hud.core.pack.VanillaAssets = { _, _ -> TestAssets.vanilla() },
) = HudEngine(platform, reader, assets, emptyList(), HttpGet { error("no network in tests") }, clock)
```

`PlayerHudTest.kt`:

```kotlin
package dev.slne.surf.hud.core.runtime

import dev.slne.surf.hud.core.*
import dev.slne.surf.hud.core.placeholder.HudClock
import kotlinx.coroutines.runBlocking
import net.kyori.adventure.text.Component
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.*

class PlayerHudTest {
    @TempDir lateinit var root: Path
    private var now = 0L
    private val uuid = UUID.randomUUID()
    private lateinit var platform: FakePlatform
    private lateinit var engine: HudEngine
    private val audience get() = platform.audiences.getValue(uuid)

    @BeforeTest fun setUp() {
        TestDefinitions.write(root, TestDefinitions.standardFiles())
        platform = FakePlatform(root).also { it.audiences[uuid] = RecordingAudience() }
        engine = testEngine(platform, HudClock { now })
        engine.placeholderRegistry.register("money") { _, _ -> 1500 }
        engine.placeholderRegistry.register("health") { _, _ -> 10 }
        engine.placeholderRegistry.register("max_health") { _, _ -> 20 }
    }

    private fun reload() = runBlocking { engine.reload() }
    private fun tick() = engine.update(uuid)
    private fun name(): Component = audience.bars.single().name()

    @Test fun `reload writes the pack and shows default huds`() {
        val result = reload()
        assertTrue(result.success, result.errors.toString())
        assertNotNull(result.packHash)
        assertTrue(root.resolve("build/surf-hud.zip").exists())
        engine.handleJoin(uuid)
        tick()
        assertEquals(setOf("main"), engine.player(uuid).shownHuds)
        assertEquals(1, audience.bars.size)
    }

    @Test fun `bossbar name is only sent when it changes`() {
        reload(); engine.handleJoin(uuid); tick()
        val first = name()
        tick(); tick()
        assertSame(first, name())
    }

    @Test fun `disabled player sees nothing`() {
        reload(); engine.handleJoin(uuid); tick()
        engine.player(uuid).enabled = false
        tick()
        assertTrue(audience.bars.isEmpty())
    }

    @Test fun `unknown ids throw`() {
        reload()
        assertFailsWith<IllegalArgumentException> { runBlocking { engine.player(uuid).show("nope") } }
        assertFailsWith<IllegalArgumentException> { runBlocking { engine.player(uuid).popup("nope") } }
    }

    @Test fun `stacking popups drop the oldest and expire`() = runBlocking {
        reload(); engine.handleJoin(uuid)
        val player = engine.player(uuid)
        val a = player.popup("feed", mapOf("target" to "A"))
        val b = player.popup("feed", mapOf("target" to "B"))
        val c = player.popup("feed", mapOf("target" to "C"))
        assertFalse(a.isOpen)
        assertEquals(listOf(c, b), player.popups("feed"))
        now = 100 * 50L
        tick()
        assertTrue(player.popups("feed").isEmpty())
        assertFalse(b.isOpen)
    }

    @Test fun `unique popups update in place`() = runBlocking {
        reload(); engine.handleJoin(uuid)
        val player = engine.player(uuid)
        val first = player.popup("vote", mapOf("target" to "A"))
        val second = player.popup("vote", mapOf("target" to "B"))
        assertSame(first, second)
        assertEquals(1, player.popups("vote").size)
        first.close()
        assertFalse(first.isOpen)
        assertTrue(player.popups().isEmpty())
    }

    @Test fun `reload drops removed huds and popups`() = runBlocking {
        reload(); engine.handleJoin(uuid)
        val player = engine.player(uuid)
        player.show("extra")
        val popup = player.popup("vote")
        root.resolve("huds/main.yml").writeText("main: { default: true, layouts: [ { layout: money, anchor: top_left } ] }")
        root.resolve("popups/feed.yml").writeText("feed: { anchor: center, layouts: [ head ] }")
        assertTrue(engine.reload().success)
        tick()
        assertEquals(setOf("main"), player.shownHuds)
        assertFalse(popup.isOpen)
    }

    @Test fun `failed reload keeps the previous model`() {
        reload()
        val model = engine.model
        root.resolve("layouts/stats.yml").writeText("money: { elements: [ { type: nope } ] }")
        val result = reload()
        assertFalse(result.success)
        assertSame(model, engine.model)
    }

    @Test fun `missing vanilla assets are a reload error`() {
        engine = testEngine(platform, HudClock { now }, assets = { _, _ -> error("offline") })
        val result = reload()
        assertContains(result.errors.single(), "offline")
        assertNull(engine.model)
    }

    @Test fun `show on an offline player does not wait`() = runBlocking {
        root.resolve("huds/heads.yml").writeText("heads: { layouts: [ { layout: head, anchor: center } ] }")
        reload()
        platform.online = false
        engine.player(uuid).show("heads")
        assertTrue("heads" in engine.player(uuid).shownHuds)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.runtime.*"`
Expected: FAIL, compilation errors.

- [ ] **Step 3: Implement**

`platform/HudPlatform.kt`:

```kotlin
package dev.slne.surf.hud.core.platform

import net.kyori.adventure.audience.Audience
import java.nio.file.Path
import java.util.*

/** What the engine needs from the server platform. */
interface HudPlatform {
    /** The plugin folder holding `config.yml`, the definition folders, `assets/`, `cache/` and `build/`. */
    val dataFolder: Path

    /** The online player as an audience, or `null`. */
    fun viewer(uuid: UUID): Audience?

    /** Runs [task] on the player's thread; returns `false` (and drops the task) if the player is offline. */
    fun execute(uuid: UUID, task: () -> Unit): Boolean
}
```

`runtime/PopupInstance.kt`:

```kotlin
package dev.slne.surf.hud.core.runtime

import dev.slne.surf.hud.api.PopupHandle
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.model.PopupSpec
import java.util.concurrent.ConcurrentHashMap

/** One open popup; [spec] is swapped on reload, [openedAt] restarts when a unique popup is retriggered. */
class PopupInstance(
    @Volatile var spec: PopupSpec,
    val variables: ConcurrentHashMap<String, Value>,
    @Volatile var openedAt: Long,
) {
    @Volatile
    var open = true

    lateinit var handle: PopupHandle
}

class PopupHandleImpl(private val instance: PopupInstance, private val owner: PlayerHud) : PopupHandle {
    override val id: String get() = instance.spec.id
    override val isOpen: Boolean get() = instance.open

    override fun update(variables: Map<String, Any?>) {
        if (!instance.open) return
        variables.forEach { (key, value) -> instance.variables[key.lowercase()] = Value.of(value) }
        owner.refresh()
    }

    override fun update(vararg variables: Pair<String, Any?>) = update(variables.toMap())

    override fun close() = owner.closePopup(instance)
}
```

`runtime/PlayerHud.kt`:

```kotlin
package dev.slne.surf.hud.core.runtime

import dev.slne.surf.hud.api.HudPlayer
import dev.slne.surf.hud.api.PopupHandle
import dev.slne.surf.hud.core.HudEngine
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.expression.isTrue
import dev.slne.surf.hud.core.head.HeadPixels
import dev.slne.surf.hud.core.model.HeadSpec
import dev.slne.surf.hud.core.model.HudModel
import dev.slne.surf.hud.core.model.Placement
import dev.slne.surf.hud.core.model.PopupSpec
import dev.slne.surf.hud.core.placeholder.PlaceholderCache
import dev.slne.surf.hud.core.placeholder.RenderScope
import dev.slne.surf.hud.core.render.HeadKey
import net.kyori.adventure.audience.Audience
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.text.Component
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * One player's HUD state: shown HUDs, open popups and the last rendered bossbar. API calls may come from any
 * thread; rendering ([update]) runs on the player's thread. All state is guarded by [lock].
 */
class PlayerHud(override val uuid: UUID, private val engine: HudEngine) : HudPlayer {
    private class CachedLayout(val key: List<Any?>, val component: Component)

    private val lock = Any()
    private val shown = LinkedHashSet<String>()
    private val stacks = LinkedHashMap<String, MutableList<PopupInstance>>()
    private val layouts = HashMap<Int, CachedLayout>()
    private val rememberedHeads = HashMap<HeadKey, HeadPixels>()
    private var cache: PlaceholderCache? = null
    private val bar = BossBar.bossBar(Component.empty(), 0f, BossBar.Color.PINK, BossBar.Overlay.PROGRESS)
    private var shownTo: Audience? = null
    private var lastName: Component? = null
    private var ticks = 0

    @Volatile
    private var forced = true

    @Volatile
    override var enabled: Boolean = true
        set(value) {
            field = value
            refresh()
        }

    override val shownHuds: Set<String> get() = synchronized(lock) { shown.toSet() }

    override suspend fun show(hud: String) {
        val spec = engine.requireModel().huds[hud] ?: throw IllegalArgumentException("Unknown HUD '$hud'")
        engine.awaitHeads(this, spec.placements, emptyMap())
        synchronized(lock) { shown += hud }
        refresh()
    }

    override fun hide(hud: String) {
        require(hud in engine.requireModel().huds) { "Unknown HUD '$hud'" }
        synchronized(lock) { shown -= hud }
        refresh()
    }

    override suspend fun popup(id: String, variables: Map<String, Any?>): PopupHandle {
        val spec = engine.requireModel().popups[id] ?: throw IllegalArgumentException("Unknown popup '$id'")
        val values = variables.entries.associate { (key, value) -> key.lowercase() to Value.of(value) }
        engine.awaitHeads(this, spec.stacks.first(), values)
        val handle = synchronized(lock) { open(spec, values) }
        refresh()
        return handle
    }

    override fun popups(id: String?): List<PopupHandle> = synchronized(lock) {
        stacks.filterKeys { id == null || it == id }.values.flatten().map { it.handle }
    }

    override fun refresh() {
        forced = true
    }

    private fun open(spec: PopupSpec, values: Map<String, Value>): PopupHandle {
        val now = engine.clock.millis()
        val stack = stacks.getOrPut(spec.id) { mutableListOf() }
        if (spec.unique && stack.isNotEmpty()) {
            val instance = stack[0]
            instance.variables.clear()
            instance.variables.putAll(values)
            instance.openedAt = now
            return instance.handle
        }
        val instance = PopupInstance(spec, ConcurrentHashMap(values), now)
        instance.handle = PopupHandleImpl(instance, this)
        stack.add(0, instance)
        while (stack.size > spec.maxStack) stack.removeLast().open = false
        return instance.handle
    }

    fun closePopup(instance: PopupInstance) {
        synchronized(lock) {
            instance.open = false
            stacks[instance.spec.id]?.remove(instance)
        }
        refresh()
    }

    /** Head names the given placements would draw now; evaluated on the player's thread. */
    fun headNames(placements: List<Placement>, variables: Map<String, Value>): List<String> = synchronized(lock) {
        val scope = RenderScope(uuid, cache(), variables, engine.warnings)
        placements.flatMap { it.layout.elements }.filterIsInstance<HeadSpec>()
            .map { it.player.evaluate(scope) }
            .filter { it != Value.Missing }
            .map { it.text() }
            .filter { it.isNotBlank() }
    }

    /** Called every tick on the player's thread; renders when forced or when the update interval elapsed. */
    fun update() {
        val model = engine.model ?: return
        if (!forced && ++ticks < model.settings.updateInterval) return
        ticks = 0
        forced = false
        val audience = engine.platform.viewer(uuid) ?: return
        synchronized(lock) {
            expirePopups()
            val cache = cache()
            cache.nextFrame()
            val parts = ArrayList<Pair<Int, Component>>()
            if (enabled) {
                val base = RenderScope(uuid, cache, emptyMap(), engine.warnings)
                for (id in shown) {
                    val hud = model.huds[id] ?: continue
                    if (!hud.conditions.all { it.isTrue(base) }) continue
                    hud.placements.forEach { placement -> render(placement, base, null)?.let { parts += placement.slot to it } }
                }
                for (stack in stacks.values) {
                    stack.forEachIndexed { index, instance ->
                        val scope = RenderScope(uuid, cache, HashMap(instance.variables), engine.warnings)
                        instance.spec.stacks.getOrNull(index)?.forEach { placement ->
                            render(placement, scope, instance)?.let { parts += placement.slot to it }
                        }
                    }
                }
            }
            parts.sortBy { it.first }
            send(audience, model, Component.text().append(parts.map { it.second }).build(), parts.isNotEmpty())
        }
    }

    private fun render(placement: Placement, scope: RenderScope, owner: PopupInstance?): Component? {
        if (!placement.conditions.all { it.isTrue(scope) } || !placement.layout.conditions.all { it.isTrue(scope) }) return null
        val inputs = placement.layout.placeholders.map { scope.resolve(it) }
        val key = listOf(owner, inputs, engine.heads.version)
        val component = layouts[placement.slot]?.takeIf { it.key == key }?.component
            ?: engine.renderer.render(placement.slot, placement.layout, scope, ::head).also {
                layouts[placement.slot] = CachedLayout(key, it)
            }
        return component.takeUnless { it == Component.empty() }
    }

    private fun head(key: HeadKey, player: String): HeadPixels {
        val heads = engine.heads
        heads.cached(player)?.let { pixels ->
            rememberedHeads[key] = pixels
            return pixels
        }
        heads.request(player)
        if (engine.requireModel().settings.heads.awaitLoaded) rememberedHeads[key]?.let { return it }
        return heads.fallback
    }

    private fun expirePopups() {
        val now = engine.clock.millis()
        stacks.values.forEach { stack ->
            stack.removeAll { instance ->
                val duration = instance.spec.durationTicks
                (duration > 0 && now - instance.openedAt >= duration * 50L).also { if (it) instance.open = false }
            }
        }
        stacks.values.removeAll { it.isEmpty() }
    }

    private fun send(audience: Audience, model: HudModel, name: Component, visible: Boolean) {
        if (!visible) {
            shownTo?.hideBossBar(bar)
            shownTo = null
            return
        }
        if (bar.color() != model.bossbarColor) bar.color(model.bossbarColor)
        if (name != lastName) {
            bar.name(name)
            lastName = name
        }
        if (shownTo !== audience) {
            shownTo?.hideBossBar(bar)
            audience.showBossBar(bar)
            shownTo = audience
        }
    }

    private fun cache(): PlaceholderCache = cache ?: engine.newPlaceholderCache().also { cache = it }

    /** Applies a new model: drops unknown HUDs, closes removed popups, clears caches; on the first model, shows the defaults. */
    fun onModelChanged(model: HudModel, first: Boolean) {
        synchronized(lock) {
            if (first) model.huds.values.filter { it.default }.forEach { shown += it.id }
            shown.retainAll(model.huds.keys)
            val iterator = stacks.entries.iterator()
            while (iterator.hasNext()) {
                val (id, stack) = iterator.next()
                val spec = model.popups[id]
                if (spec == null) {
                    stack.forEach { it.open = false }
                    iterator.remove()
                } else {
                    stack.forEach { it.spec = spec }
                    while (stack.size > spec.maxStack) stack.removeLast().open = false
                }
            }
            layouts.clear()
            rememberedHeads.clear()
            cache = null
        }
        refresh()
    }

    /** The player (re)joined: the client lost the bossbar, so it must be shown again. */
    fun invalidate() {
        synchronized(lock) {
            shownTo = null
            lastName = null
        }
        refresh()
    }

    fun dispose() {
        synchronized(lock) {
            shownTo?.hideBossBar(bar)
            shownTo = null
            stacks.values.flatten().forEach { it.open = false }
            stacks.clear()
        }
    }
}
```

`HudEngine.kt`:

```kotlin
package dev.slne.surf.hud.core

import dev.slne.surf.api.core.util.logger
import dev.slne.surf.hud.api.HudPlayer
import dev.slne.surf.hud.api.PlaceholderRegistry
import dev.slne.surf.hud.api.PopupHandle
import dev.slne.surf.hud.api.ReloadResult
import dev.slne.surf.hud.api.SurfHud
import dev.slne.surf.hud.core.config.DefinitionLoader
import dev.slne.surf.hud.core.config.DefinitionReader
import dev.slne.surf.hud.core.config.Durations
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.head.HeadPixels
import dev.slne.surf.hud.core.head.HeadService
import dev.slne.surf.hud.core.head.SkinFace
import dev.slne.surf.hud.core.head.SkinSource
import dev.slne.surf.hud.core.model.CompileResult
import dev.slne.surf.hud.core.model.Compiler
import dev.slne.surf.hud.core.model.HudModel
import dev.slne.surf.hud.core.model.Placement
import dev.slne.surf.hud.core.pack.HttpGet
import dev.slne.surf.hud.core.pack.PackGenerator
import dev.slne.surf.hud.core.pack.PackWriter
import dev.slne.surf.hud.core.pack.VanillaAssets
import dev.slne.surf.hud.core.placeholder.*
import dev.slne.surf.hud.core.platform.HudPlatform
import dev.slne.surf.hud.core.render.LayoutRenderer
import dev.slne.surf.hud.core.render.TextRenderer
import dev.slne.surf.hud.core.runtime.PlayerHud
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.coroutines.resume
import kotlin.io.path.exists

/**
 * The platform-neutral surf-hud implementation: loads and compiles the configuration, writes the pack,
 * keeps every player's [PlayerHud] and renders it when the platform calls [update] once per tick.
 */
open class HudEngine(
    val platform: HudPlatform,
    private val reader: DefinitionReader,
    private val assetProvider: (root: Path, version: String) -> VanillaAssets,
    private val skinSources: List<SkinSource>,
    private val http: HttpGet,
    val clock: HudClock = HudClock.SYSTEM,
) : SurfHud {
    private val log = logger()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val warnings = WarningSink { log.atWarning().log("%s", it) }
    private val resolverErrors = ResolverErrorLog(clock) { name, error ->
        log.atWarning().withCause(error).log("Placeholder [%s] failed", name)
    }
    val placeholderRegistry = PlaceholderRegistryImpl()
    override val placeholders: PlaceholderRegistry get() = placeholderRegistry

    @Volatile
    var model: HudModel? = null
        private set

    @Volatile
    lateinit var heads: HeadService
        private set

    @Volatile
    lateinit var renderer: LayoutRenderer
        private set

    private val players = ConcurrentHashMap<UUID, PlayerHud>()
    private val reloadMutex = Mutex()

    override val huds: Set<String> get() = model?.huds?.keys.orEmpty()
    override val popups: Set<String> get() = model?.popups?.keys.orEmpty()

    override fun player(uuid: UUID): HudPlayer = hud(uuid)

    fun hud(uuid: UUID): PlayerHud = players.computeIfAbsent(uuid) { id ->
        PlayerHud(id, this).also { player -> model?.let { player.onModelChanged(it, first = true) } }
    }

    fun requireModel(): HudModel = model ?: throw IllegalStateException("surf-hud has not loaded a configuration yet")

    fun handleJoin(uuid: UUID) = hud(uuid).invalidate()

    fun handleQuit(uuid: UUID) {
        players.remove(uuid)?.dispose()
    }

    /** Call once per tick on the player's thread. */
    fun update(uuid: UUID) {
        players[uuid]?.update()
    }

    fun newPlaceholderCache() = PlaceholderCache(
        placeholderRegistry, clock, requireModel().settings.placeholderIntervals.millis(), warnings, resolverErrors,
    )

    override suspend fun popup(id: String, viewers: Collection<UUID>, variables: Map<String, Any?>): List<PopupHandle> =
        coroutineScope { viewers.map { async { hud(it).popup(id, variables) } }.awaitAll() }

    override suspend fun reload(): ReloadResult = reloadMutex.withLock { withContext(Dispatchers.IO) { doReload() } }

    private fun doReload(): ReloadResult {
        val root = platform.dataFolder
        val definitions = DefinitionLoader(reader).load(root)
        val settings = definitions.settings
        val assets = try {
            assetProvider(root.resolve("cache"), settings.minecraftVersion)
        } catch (e: Exception) {
            return failed(listOf("Could not load the vanilla assets of Minecraft ${settings.minecraftVersion}: ${e.message}"))
        }
        val model = when (val result = Compiler(assets, root.resolve("assets")).compile(definitions)) {
            is CompileResult.Failure -> return failed(result.errors)
            is CompileResult.Success -> result.model
        }
        val hash = try {
            writePack(model)
        } catch (e: Exception) {
            return failed(listOf("Could not write the resource pack: ${e.message}"))
        }
        heads = HeadService(
            skinSources, http, root.resolve("cache/heads"), Durations.parse(settings.heads.cacheTtl), clock, scope,
            fallbackHead(root.resolve("assets").resolve(settings.heads.fallback)),
        )
        renderer = LayoutRenderer(model, TextRenderer(warnings))
        val first = this.model == null
        this.model = model
        players.values.forEach { it.onModelChanged(model, first) }
        log.atInfo().log("Loaded %d HUDs and %d popups in %d slots; pack SHA-1 %s", model.huds.size, model.popups.size, model.slots.size, hash)
        return ReloadResult(emptyList(), hash)
    }

    /** Regenerates the pack from the active model. */
    fun generatePack(): String = writePack(requireModel())

    private fun writePack(model: HudModel): String {
        val root = platform.dataFolder
        return PackWriter.write(PackGenerator(model).build(), root.resolve(model.settings.output.zip), root.resolve(model.settings.output.folder))
    }

    private fun failed(errors: List<String>): ReloadResult {
        errors.forEach { log.atSevere().log("%s", it) }
        return ReloadResult(errors, null)
    }

    private fun fallbackHead(file: Path): HeadPixels {
        if (!file.exists()) return HeadPixels.placeholder("fallback")
        val image = runCatching { ImageIO.read(file.toFile()) }.getOrNull() ?: return HeadPixels.placeholder("fallback")
        return if (image.width == 8 && image.height == 8) HeadPixels(Array(8) { row -> IntArray(8) { image.getRGB(it, row) } })
        else SkinFace.extract(image)
    }

    /** With `heads.await-loaded`, waits (up to the timeout) for every head the placements would draw. */
    internal suspend fun awaitHeads(player: PlayerHud, placements: List<Placement>, variables: Map<String, Value>) {
        val model = requireModel()
        if (!model.settings.heads.awaitLoaded) return
        if (placements.none { p -> p.layout.elements.any { it is dev.slne.surf.hud.core.model.HeadSpec } }) return
        val names = onPlayerThread(player.uuid) { player.headNames(placements, variables) } ?: return
        val timeout = Durations.parse(model.settings.heads.timeout)
        coroutineScope { names.distinct().map { async { heads.await(it, timeout) } }.awaitAll() }
    }

    private suspend fun <T> onPlayerThread(uuid: UUID, block: () -> T): T? = suspendCancellableCoroutine { continuation ->
        val scheduled = platform.execute(uuid) { continuation.resume(runCatching(block).getOrNull()) }
        if (!scheduled) continuation.resume(null)
    }

    fun shutdown() {
        players.values.forEach(PlayerHud::dispose)
        players.clear()
        scope.cancel()
    }
}
```

Note: `assetProvider` receives `root.resolve("cache")` so the fetcher caches under `cache/<version>/`.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :surf-hud-core:test --tests "dev.slne.surf.hud.core.runtime.*"`
Expected: PASS.

- [ ] **Step 5: Run the whole core test suite**

Run: `./gradlew :surf-hud-core:test`
Expected: PASS (all tests from Tasks 1–12).

- [ ] **Step 6: Commit**

```bash
git add surf-hud-core/src
git commit -m "feat(core): add HUD engine with per-player rendering and popups" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: Paper platform, built-in placeholders and default files

**Files:**
- Create: `surf-hud-paper/src/main/kotlin/dev/slne/surf/hud/paper/{PaperSurfHud.kt,PaperHudPlatform.kt,PaperSkinSource.kt,PaperPlaceholders.kt,BuiltinPlaceholders.kt,DefaultFiles.kt}`
- Rewrite: `surf-hud-paper/src/main/kotlin/dev/slne/surf/hud/paper/PaperMain.kt`
- Create: `surf-hud-paper/src/main/kotlin/dev/slne/surf/hud/paper/listener/HudConnectionListener.kt`
- Create: `surf-hud-paper/src/main/resources/defaults/{texts/default.yml,layouts/main.yml,huds/main.yml}`

**Interfaces:**
- Consumes: `HudEngine`, `HudPlatform`, `SurfConfigDefinitionReader`, `VanillaAssetFetcher`, `JdkHttpGet`, `MojangSkinSource`, `SkinSource`.
- Produces: `PaperSurfHud.INSTANCE`, `fun PlaceholderRegistry.player(name: String, interval: Duration? = null, resolver: (Player, String?) -> Any?)`, `object HudConnectionListener { fun attach(player: Player) }`

There are no unit tests here because this code needs a running server. Verification is the build plus the end-to-end run in Task 16.

- [ ] **Step 1: Platform, engine binding and skin source**

`PaperHudPlatform.kt`:

```kotlin
package dev.slne.surf.hud.paper

import dev.slne.surf.hud.core.platform.HudPlatform
import org.bukkit.Bukkit
import java.nio.file.Path
import java.util.*

/** Folia-safe platform: per-player work runs on the player's entity scheduler. */
object PaperHudPlatform : HudPlatform {
    override val dataFolder: Path get() = plugin.dataPath

    override fun viewer(uuid: UUID) = Bukkit.getPlayer(uuid)?.takeIf { it.isOnline }

    override fun execute(uuid: UUID, task: () -> Unit): Boolean {
        val player = Bukkit.getPlayer(uuid) ?: return false
        return player.scheduler.run(plugin, { task() }, null) != null
    }
}
```

`PaperSkinSource.kt`:

```kotlin
package dev.slne.surf.hud.paper

import dev.slne.surf.hud.core.head.SkinSource
import org.bukkit.Bukkit
import java.util.*

/** Skins of players on this server, read from their profile without any network request. */
object PaperSkinSource : SkinSource {
    override suspend fun skinUrl(player: String): String? {
        val online = runCatching { UUID.fromString(player) }.getOrNull()?.let(Bukkit::getPlayer) ?: Bukkit.getPlayerExact(player)
        return online?.playerProfile?.textures?.skin?.toString()
    }
}
```

`PaperSurfHud.kt`:

```kotlin
package dev.slne.surf.hud.paper

import com.google.auto.service.AutoService
import dev.slne.surf.hud.api.SurfHud
import dev.slne.surf.hud.core.HudEngine
import dev.slne.surf.hud.core.config.SurfConfigDefinitionReader
import dev.slne.surf.hud.core.head.MojangSkinSource
import dev.slne.surf.hud.core.pack.JdkHttpGet
import dev.slne.surf.hud.core.pack.VanillaAssetFetcher
import net.kyori.adventure.util.Services

private val http = JdkHttpGet()

@AutoService(SurfHud::class)
class PaperSurfHud : HudEngine(
    platform = PaperHudPlatform,
    reader = SurfConfigDefinitionReader(),
    assetProvider = { cache, version -> VanillaAssetFetcher(http, cache).fetch(version) },
    skinSources = listOf(PaperSkinSource, MojangSkinSource(http)),
    http = http,
), Services.Fallback {
    companion object {
        val INSTANCE: PaperSurfHud get() = SurfHud.instance as PaperSurfHud
    }
}
```

- [ ] **Step 2: Built-in placeholders**

`PaperPlaceholders.kt`:

```kotlin
package dev.slne.surf.hud.paper

import dev.slne.surf.hud.api.PlaceholderRegistry
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import kotlin.time.Duration

/** Registers a placeholder resolved from the online [Player]; `null` while the player is offline. */
fun PlaceholderRegistry.player(name: String, interval: Duration? = null, resolver: (Player, String?) -> Any?) =
    register(name, interval) { uuid, argument -> Bukkit.getPlayer(uuid)?.let { resolver(it, argument) } }
```

`BuiltinPlaceholders.kt`:

```kotlin
package dev.slne.surf.hud.paper

import dev.slne.surf.hud.api.PlaceholderRegistry
import org.bukkit.Bukkit
import org.bukkit.attribute.Attribute

/** Player data every HUD can use without other plugins. */
object BuiltinPlaceholders {
    fun register(registry: PlaceholderRegistry) = with(registry) {
        player("name") { p, _ -> p.name }
        player("display_name") { p, _ -> p.displayName() }
        player("health") { p, _ -> p.health }
        player("max_health") { p, _ -> p.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0 }
        player("food") { p, _ -> p.foodLevel }
        player("saturation") { p, _ -> p.saturation }
        player("armor") { p, _ -> p.getAttribute(Attribute.ARMOR)?.value ?: 0.0 }
        player("level") { p, _ -> p.level }
        player("exp") { p, _ -> p.exp }
        player("exp_percent") { p, _ -> p.exp * 100.0 }
        player("air") { p, _ -> p.remainingAir }
        player("max_air") { p, _ -> p.maximumAir }
        player("world") { p, _ -> p.world.name }
        player("x") { p, _ -> p.location.x }
        player("y") { p, _ -> p.location.y }
        player("z") { p, _ -> p.location.z }
        player("yaw") { p, _ -> p.location.yaw }
        player("ping") { p, _ -> p.ping }
        player("gamemode") { p, _ -> p.gameMode.name.lowercase() }
        player("sneaking") { p, _ -> p.isSneaking }
        player("flying") { p, _ -> p.isFlying }
        register("online") { _, _ -> Bukkit.getOnlinePlayers().size }
        register("max_players") { _, _ -> Bukkit.getMaxPlayers() }
    }
}
```

- [ ] **Step 3: Default files**

`DefaultFiles.kt`:

```kotlin
package dev.slne.surf.hud.paper

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/** Installs the bundled minimal configuration on first start (when `huds/` does not exist yet). */
object DefaultFiles {
    private val FILES = listOf("texts/default.yml", "layouts/main.yml", "huds/main.yml")

    fun install(dataFolder: Path) {
        if (dataFolder.resolve("huds").exists()) return
        for (file in FILES) {
            val target = dataFolder.resolve(file)
            target.parent.createDirectories()
            DefaultFiles::class.java.getResourceAsStream("/defaults/$file")!!.use { Files.copy(it, target) }
        }
        dataFolder.resolve("assets").createDirectories()
    }
}
```

`src/main/resources/defaults/texts/default.yml`:

```yaml
# Vanilla ascii font at its default height (8). Add more entries with another height, e.g.
# title: { type: ascii, height: 16 }
default: { type: ascii }
```

`src/main/resources/defaults/layouts/main.yml`:

```yaml
welcome:
  elements:
    - { type: text, text: "<gray>surf-hud</gray> <dark_gray>·</dark_gray> <gray>[ping]ms</gray>" }
```

`src/main/resources/defaults/huds/main.yml`:

```yaml
main:
  default: true
  layouts:
    - { layout: welcome, anchor: center_left, x: 4, y: 0 }
```

- [ ] **Step 4: Connection listener and plugin main**

`listener/HudConnectionListener.kt`:

```kotlin
package dev.slne.surf.hud.paper.listener

import dev.slne.surf.hud.paper.PaperSurfHud
import dev.slne.surf.hud.paper.plugin
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/** Creates a player's HUD on join and ticks it every tick on the player's own scheduler. */
object HudConnectionListener : Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) = attach(event.player)

    @EventHandler(priority = EventPriority.MONITOR)
    fun onQuit(event: PlayerQuitEvent) {
        PaperSurfHud.INSTANCE.handleQuit(event.player.uniqueId)
    }

    fun attach(player: Player) {
        val engine = PaperSurfHud.INSTANCE
        val uuid = player.uniqueId
        engine.handleJoin(uuid)
        // The entity scheduler retires the task when the player leaves.
        player.scheduler.runAtFixedRate(plugin, { engine.update(uuid) }, null, 1L, 1L)
    }
}
```

`PaperMain.kt`:

```kotlin
package dev.slne.surf.hud.paper

import dev.slne.surf.api.paper.event.register
import dev.slne.surf.hud.api.SurfHud
import dev.slne.surf.hud.paper.listener.HudConnectionListener
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin

val plugin get() = JavaPlugin.getPlugin(PaperMain::class.java)

class PaperMain : JavaPlugin() {
    override fun onEnable() {
        DefaultFiles.install(dataPath)
        BuiltinPlaceholders.register(SurfHud.placeholders)
        HudConnectionListener.register()
        Bukkit.getOnlinePlayers().forEach(HudConnectionListener::attach)

        val engine = PaperSurfHud.INSTANCE
        engine.scope.launch {
            val result = engine.reload()
            if (!result.success) logger.warning("surf-hud started without HUDs; fix the errors above and run /hud reload")
        }
    }

    override fun onDisable() {
        PaperSurfHud.INSTANCE.shutdown()
    }
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :surf-hud-paper:build`
Expected: BUILD SUCCESSFUL. Check that the shadow jar contains `team/unnamed/creative` and does not contain `net/kyori/adventure/text/Component.class`:
`unzip -l surf-hud-paper/build/libs/*-all.jar | grep -c "team/unnamed/creative"` (> 0) and `unzip -l surf-hud-paper/build/libs/*-all.jar | grep -c "net/kyori/adventure/text/Component.class"` (0). Use whichever jar `shadowJar` produces if the name differs.

- [ ] **Step 6: Commit**

```bash
git add surf-hud-paper
git commit -m "feat(paper): add Paper platform, built-in placeholders and default HUD" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 14: PlaceholderAPI and MiniPlaceholders bridges

**Files:**
- Modify: `surf-hud-paper/build.gradle.kts`
- Create: `surf-hud-paper/src/main/kotlin/dev/slne/surf/hud/paper/bridge/{PapiBridge.kt,MiniPlaceholdersBridge.kt}`
- Modify: `surf-hud-paper/src/main/kotlin/dev/slne/surf/hud/paper/PaperMain.kt`

**Interfaces:**
- Consumes: `PlaceholderRegistry.player` (Task 13), `LegacyText` (Task 8).
- Produces: `[papi:<identifier>]` and `[mini:<tag>]` placeholders when the plugins are installed.

- [ ] **Step 1: Dependencies and soft dependencies**

`surf-hud-paper/build.gradle.kts`:

```kotlin
import dev.slne.surf.api.gradle.util.registerSoft

plugins {
    id("dev.slne.surf.api.gradle.paper-plugin")
}

repositories {
    maven("https://repo.extendedclip.com/releases/")
}

surfPaperPluginApi {
    mainClass("dev.slne.surf.hud.paper.PaperMain")
    generateLibraryLoader(false)
    foliaSupported(true)

    authors.add("red")

    serverDependencies {
        registerSoft("PlaceholderAPI")
        registerSoft("MiniPlaceholders")
    }
}

dependencies {
    api(projects.surfHudCore)
    compileOnly("me.clip:placeholderapi:2.12.3")
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:3.2.0")
}
```

- [ ] **Step 2: Bridges**

`bridge/PapiBridge.kt`:

```kotlin
package dev.slne.surf.hud.paper.bridge

import dev.slne.surf.hud.api.PlaceholderRegistry
import dev.slne.surf.hud.core.placeholder.LegacyText
import dev.slne.surf.hud.paper.player
import me.clip.placeholderapi.PlaceholderAPI

/** `[papi:vault_eco_balance]` → `%vault_eco_balance%`; `§` colors become components. Cached by `placeholder-intervals.papi`. */
object PapiBridge {
    fun register(registry: PlaceholderRegistry) {
        registry.player("papi") { player, identifier ->
            identifier?.let { LegacyText.value(PlaceholderAPI.setPlaceholders(player, "%$it%")) }
        }
    }
}
```

`bridge/MiniPlaceholdersBridge.kt`:

```kotlin
package dev.slne.surf.hud.paper.bridge

import dev.slne.surf.hud.api.PlaceholderRegistry
import dev.slne.surf.hud.paper.player
import io.github.miniplaceholders.api.MiniPlaceholders
import net.kyori.adventure.text.minimessage.MiniMessage

/** `[mini:player_name]` → the MiniPlaceholders tag `<player_name>` for this player, as a component. */
object MiniPlaceholdersBridge {
    fun register(registry: PlaceholderRegistry) {
        registry.player("mini") { player, tag ->
            tag?.let { MiniMessage.miniMessage().deserialize("<$it>", player, MiniPlaceholders.audienceGlobalPlaceholders()) }
        }
    }
}
```

In `PaperMain.onEnable`, after `BuiltinPlaceholders.register(...)`:

```kotlin
        val plugins = Bukkit.getPluginManager()
        if (plugins.isPluginEnabled("PlaceholderAPI")) PapiBridge.register(SurfHud.placeholders)
        if (plugins.isPluginEnabled("MiniPlaceholders")) MiniPlaceholdersBridge.register(SurfHud.placeholders)
```

(Add imports `dev.slne.surf.hud.paper.bridge.PapiBridge` and `dev.slne.surf.hud.paper.bridge.MiniPlaceholdersBridge`.) Each bridge is its own object, so the PlaceholderAPI and MiniPlaceholders classes only load when that plugin is present.

- [ ] **Step 3: Build**

Run: `./gradlew :surf-hud-paper:build`
Expected: BUILD SUCCESSFUL. Check that the generated `paper-plugin.yml` in `surf-hud-paper/build/` (find it with `find surf-hud-paper/build -name "paper-plugin.yml"`) lists both plugins as optional server dependencies. If `MiniMessage.deserialize(String, Pointered, TagResolver)` does not exist in Adventure 5.2, use `MiniMessage.miniMessage().deserialize("<$it>", MiniPlaceholders.audienceGlobalPlaceholders())` with `TagResolver` target support from the MiniPlaceholders docs for 3.x, and keep the player as the audience target.

- [ ] **Step 4: Commit**

```bash
git add surf-hud-paper
git commit -m "feat(paper): bridge PlaceholderAPI and MiniPlaceholders placeholders" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 15: /hud command

**Files:**
- Create: `surf-hud-paper/src/main/kotlin/dev/slne/surf/hud/paper/command/HudCommand.kt`
- Modify: `surf-hud-paper/src/main/kotlin/dev/slne/surf/hud/paper/PaperMain.kt` (call `hudCommand()` in `onEnable`)

**Interfaces:**
- Consumes: `SurfHud`, `PaperSurfHud.INSTANCE.scope`, `generatePack()`, `hudPlayer`.
- Produces: `fun hudCommand()`.

- [ ] **Step 1: Write the command**

`command/HudCommand.kt`:

```kotlin
package dev.slne.surf.hud.paper.command

import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.kotlindsl.*
import dev.slne.surf.hud.api.SurfHud
import dev.slne.surf.hud.api.hudPlayer
import dev.slne.surf.hud.paper.PaperSurfHud
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private const val PERMISSION = "surf.hud.command"

/** `/hud reload | generate | show | hide | popup | toggle`. */
fun hudCommand() = commandTree("hud") {
    withPermission(PERMISSION)

    literalArgument("reload") {
        withPermission("$PERMISSION.reload")
        anyExecutor { sender, _ -> launch(sender) { reportReload(sender) } }
    }

    literalArgument("generate") {
        withPermission("$PERMISSION.generate")
        anyExecutor { sender, _ ->
            launch(sender) { sender.sendPlainMessage("surf-hud pack written, SHA-1 ${PaperSurfHud.INSTANCE.generatePack()}") }
        }
    }

    literalArgument("show") {
        withPermission("$PERMISSION.show")
        playerArgument("player") {
            stringArgument("hud") {
                replaceSuggestions(ArgumentSuggestions.strings { SurfHud.huds.toTypedArray() })
                anyExecutor { sender, args ->
                    val player = args["player"] as Player
                    launch(sender) { player.hudPlayer.show(args["hud"] as String) }
                }
            }
        }
    }

    literalArgument("hide") {
        withPermission("$PERMISSION.hide")
        playerArgument("player") {
            stringArgument("hud") {
                replaceSuggestions(ArgumentSuggestions.strings { SurfHud.huds.toTypedArray() })
                anyExecutor { sender, args ->
                    val player = args["player"] as Player
                    launch(sender) { player.hudPlayer.hide(args["hud"] as String) }
                }
            }
        }
    }

    literalArgument("popup") {
        withPermission("$PERMISSION.popup")
        playerArgument("player") {
            stringArgument("popup") {
                replaceSuggestions(ArgumentSuggestions.strings { SurfHud.popups.toTypedArray() })
                greedyStringArgument("variables", optional = true) {
                    anyExecutor { sender, args ->
                        val player = args["player"] as Player
                        val variables = parseVariables(args["variables"] as String?)
                        launch(sender) { player.hudPlayer.popup(args["popup"] as String, variables) }
                    }
                }
            }
        }
    }

    literalArgument("toggle") {
        withPermission("$PERMISSION.toggle")
        playerArgument("player", optional = true) {
            anyExecutor { sender, args ->
                val target = args["player"] as Player? ?: sender as? Player
                if (target == null) {
                    sender.sendPlainMessage("Console must name a player")
                    return@anyExecutor
                }
                val hud = target.hudPlayer
                hud.enabled = !hud.enabled
                sender.sendPlainMessage("HUD of ${target.name} ${if (hud.enabled) "enabled" else "disabled"}")
            }
        }
    }
}

/** `key=value key2=value2`; values without `=` are ignored. */
private fun parseVariables(input: String?): Map<String, Any?> = input.orEmpty().split(' ')
    .filter { '=' in it }
    .associate { it.substringBefore('=') to it.substringAfter('=') }

private suspend fun reportReload(sender: CommandSender) {
    val result = SurfHud.reload()
    if (result.success) {
        sender.sendPlainMessage("surf-hud reloaded, pack SHA-1 ${result.packHash}")
    } else {
        sender.sendPlainMessage("surf-hud reload failed, the previous configuration stays active:")
        result.errors.forEach { sender.sendPlainMessage(" - $it") }
    }
}

/** Runs [block] off the main thread and reports failures (e.g. unknown ids) to [sender]. */
private fun launch(sender: CommandSender, block: suspend () -> Unit) {
    PaperSurfHud.INSTANCE.scope.launch {
        runCatching { block() }.onFailure { sender.sendPlainMessage(it.message ?: it.toString()) }
    }
}
```

Add `hudCommand()` to `PaperMain.onEnable()` right after `HudConnectionListener.register()` (import `dev.slne.surf.hud.paper.command.hudCommand`).

- [ ] **Step 2: Build**

Run: `./gradlew :surf-hud-paper:build`
Expected: BUILD SUCCESSFUL. If the CommandAPI Kotlin DSL in this surf-api version has no `optional` parameter on `playerArgument`/`greedyStringArgument`, register two branches instead (one with the argument, one without), each with its own executor.

- [ ] **Step 3: Commit**

```bash
git add surf-hud-paper
git commit -m "feat(paper): add /hud command" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

### Task 16: Example plugin and end-to-end verification

**Files:**
- Rewrite: `surf-hud-paper-example/src/main/kotlin/dev/slne/surf/hud/example/paper/PaperExampleMain.kt`
- Create: `surf-hud-paper-example/src/main/kotlin/dev/slne/surf/hud/example/paper/{ExampleInstaller.kt,ExamplePlaceholders.kt,VoteLoop.kt}`
- Create: `surf-hud-paper-example/src/main/resources/surf-hud/{texts/demo.yml,images/demo.yml,layouts/demo.yml,layouts/vote.yml,huds/demo.yml,popups/vote.yml}`
- Copy: `prototypes/core-shader/textures/icons/*.png` → `surf-hud-paper-example/src/main/resources/surf-hud/assets/icons/`, `prototypes/core-shader/textures/strips/*.png` → `.../assets/strips/`
- Modify: `surf-hud-paper-example/build.gradle.kts` (dev runtime for the end-to-end run)
- Create: `docs/hud-config.md` (admin reference)

**Interfaces:**
- Consumes: the public API (`SurfHud`, `hudPlayer`, `PopupHandle`) and `PlaceholderRegistry`.

- [ ] **Step 1: Copy the textures and write the demo YAML**

```bash
mkdir -p surf-hud-paper-example/src/main/resources/surf-hud/assets
cp -r prototypes/core-shader/textures/icons prototypes/core-shader/textures/strips surf-hud-paper-example/src/main/resources/surf-hud/assets/
python -c "from PIL import Image; [print(p, Image.open(p).size) for p in __import__('glob').glob('surf-hud-paper-example/src/main/resources/surf-hud/assets/*/*.png')]"
```

Note the icon heights from the output. The `y` values below assume 8 px icons and 9–11 px strips, as in the prototype layout. Adjust a `y` only if an icon is taller.

`texts/demo.yml`:

```yaml
title: { type: ascii, height: 16 }
vs: { type: ascii, height: 24 }
```

`images/demo.yml`:

```yaml
heart: { type: single, file: icons/heart.png }
coin: { type: single, file: icons/coin.png }
clock: { type: single, file: icons/clock.png }
star: { type: single, file: icons/star.png }
player: { type: single, file: icons/player.png }
panel: { type: strip, file: strips/panel.png }
bar_bg: { type: strip, file: strips/bar_bg.png }
bar_fill: { type: strip, file: strips/bar_fill.png }
bar_tint: { type: strip, file: strips/bar_tint.png }
head_big: { type: pixel, size: 5 }
```

`layouts/demo.yml`:

```yaml
demo_title:
  align: center
  elements:
    - { type: text, text: "<gradient:#5BC0EB:#9BC53D>surf-hud demo</gradient>" }
demo_status:
  elements:
    - { type: image, image: star }
    - { type: text, x: 10, y: 1, text: "<yellow>[stars]</yellow>" }
    - { type: image, image: heart, x: 30 }
    - { type: text, x: 40, y: 1, text: "<red>{[health] | 0}</red>" }
demo_timer:
  align: right
  elements:
    - { type: image, image: panel, width: "[width:clock_text] + 8" }
    - { id: clock_text, type: text, x: 4, y: 3, text: "<aqua>[time]</aqua>" }
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
demo_player:
  elements:
    - { type: image, image: player }
    - { type: text, x: 10, y: 1, text: "[name] <gray>([rank])</gray>" }
```

`huds/demo.yml`:

```yaml
demo:
  default: true
  layouts:
    - { layout: demo_title, anchor: top_center, x: 0, y: 4 }
    - { layout: demo_status, anchor: top_left, x: 4, y: 4 }
    - { layout: demo_timer, anchor: top_right, x: -4, y: 4 }
    - { layout: demo_money, anchor: bottom_right, x: -4, y: -17 }
    - { layout: demo_health, anchor: bottom_right, x: -4, y: -30 }
    - { layout: demo_player, anchor: bottom_left, x: 4, y: -14 }
```

`layouts/vote.yml`. Every vote layout is 130 px wide from x = 0 and placed at x = -65, so the vote is centered:

```yaml
vote_title:
  elements:
    - { type: text, font: title, x: 65, align: center, text: "<gold>[title]</gold>" }
vote_heads:
  elements:
    - { type: head, image: head_big, player: "[left]", brightness: "[left_brightness]" }
    - { type: text, font: vs, x: 65, y: 8, align: center, text: "<red><bold>VS</bold></red>" }
    - { type: head, image: head_big, x: 90, player: "[right]", brightness: "[right_brightness]" }
vote_names:
  elements:
    - { type: text, x: 20, align: center, text: "<aqua>[left]</aqua>" }
    - { type: text, x: 110, align: center, text: "<light_purple>[right]</light_purple>" }
vote_bar:
  elements:
    - { type: text, x: -4, align: right, text: "<aqua>[left_votes]</aqua>" }
    - { type: image, image: bar_bg, y: 1, width: 130 }
    - { type: bar, image: bar_tint, y: 2, width: 130, value: "[time_left] / [time_total]", color: "#55FFFF" }
    - { type: text, x: 65, align: center, text: "{[left_votes] / max([left_votes] + [right_votes], 1) * 100 | 0}% : {[right_votes] / max([left_votes] + [right_votes], 1) * 100 | 0}%" }
    - { type: text, x: 134, text: "<light_purple>[right_votes]</light_purple>" }
vote_timer:
  elements:
    - { type: image, image: clock, x: 52, y: -1 }
    - { type: text, x: 62, text: "[time_left]s" }
```

`popups/vote.yml`:

```yaml
vote:
  anchor: center
  unique: true
  layouts:
    - { layout: vote_title, x: -65, y: -80 }
    - { layout: vote_heads, x: -65, y: -60 }
    - { layout: vote_names, x: -65, y: -16 }
    - { layout: vote_bar, x: -65, y: 8 }
    - { layout: vote_timer, x: -65, y: 18 }
```

- [ ] **Step 2: Installer, placeholders and vote loop**

`ExampleInstaller.kt`:

```kotlin
package dev.slne.surf.hud.example.paper

import org.bukkit.Bukkit
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists

/** Copies the demo definitions and textures into surf-hud's folder; existing files are left alone. */
object ExampleInstaller {
    private val FILES = listOf(
        "texts/demo.yml", "images/demo.yml", "layouts/demo.yml", "layouts/vote.yml", "huds/demo.yml", "popups/vote.yml",
        "assets/icons/heart.png", "assets/icons/coin.png", "assets/icons/clock.png", "assets/icons/star.png",
        "assets/icons/player.png", "assets/strips/panel.png", "assets/strips/bar_bg.png", "assets/strips/bar_fill.png",
        "assets/strips/bar_tint.png",
    )

    fun install(): Path {
        val target = Bukkit.getPluginManager().getPlugin("surf-hud-paper")!!.dataPath
        for (file in FILES) {
            val path = target.resolve(file)
            if (path.exists()) continue
            path.parent.createDirectories()
            ExampleInstaller::class.java.getResourceAsStream("/surf-hud/$file")!!.use { Files.copy(it, path) }
        }
        return target
    }
}
```

`ExamplePlaceholders.kt`:

```kotlin
package dev.slne.surf.hud.example.paper

import dev.slne.surf.hud.api.SurfHud
import org.bukkit.Bukkit
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/** Simulated data for the demo HUD: a drifting balance, a rank, stars and the time. */
object ExamplePlaceholders {
    private val money = ConcurrentHashMap<UUID, Double>()
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun register() = with(SurfHud.placeholders) {
        register("money") { uuid, _ ->
            money.merge(uuid, 1000.0) { old, _ -> (old + Random.nextDouble(-25.0, 40.0)).coerceAtLeast(0.0) }
        }
        register("rank") { uuid, _ -> if (Bukkit.getPlayer(uuid)?.isOp == true) "Admin" else "Player" }
        register("stars", 1.seconds) { uuid, _ -> (uuid.hashCode() and 0x7FFFFFFF) % 50 }
        register("time", 1.seconds) { _, _ -> LocalTime.now().format(clock) }
    }
}
```

`VoteLoop.kt`:

```kotlin
package dev.slne.surf.hud.example.paper

import dev.slne.surf.hud.api.SurfHud
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Runs vote rounds for everyone online: 30 s voting with live counts, 5 s result, 5 s pause. */
class VoteLoop(private val scope: CoroutineScope, private val left: String, private val right: String) {
    private var job: Job? = null

    fun start() {
        job = scope.launch { while (isActive) round() }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun round() {
        val viewers = Bukkit.getOnlinePlayers().map { it.uniqueId }
        if (viewers.isEmpty()) {
            delay(5.seconds)
            return
        }
        val total = 30
        var leftVotes = 0
        var rightVotes = 0
        val handles = SurfHud.popup(
            "vote", viewers, mapOf(
                "title" to "Who wins?", "left" to left, "right" to right,
                "left_votes" to 0, "right_votes" to 0, "time_left" to total, "time_total" to total,
                "left_brightness" to 1, "right_brightness" to 1,
            )
        )
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = (System.currentTimeMillis() - start) / 1000.0
            if (elapsed >= total) break
            // About 6 votes per second, with a bias that swings between the candidates.
            val bias = 0.5 + 0.2 * sin(elapsed / 5.0)
            repeat(Random.nextInt(0, 3)) { if (Random.nextDouble() < bias) leftVotes++ else rightVotes++ }
            handles.forEach { it.update("left_votes" to leftVotes, "right_votes" to rightVotes, "time_left" to (total - elapsed).toInt()) }
            delay(250.milliseconds)
        }
        val leftWins = leftVotes >= rightVotes
        handles.forEach {
            it.update(
                "title" to "${if (leftWins) left else right} wins!", "time_left" to 0,
                "left_brightness" to if (leftWins) 1 else 0.4, "right_brightness" to if (leftWins) 0.4 else 1,
            )
        }
        delay(5.seconds)
        handles.forEach { it.close() }
        delay(5.seconds)
    }
}
```

`PaperExampleMain.kt`:

```kotlin
package dev.slne.surf.hud.example.paper

import dev.slne.surf.hud.api.SurfHud
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin

val plugin get() = JavaPlugin.getPlugin(PaperExampleMain::class.java)

class PaperExampleMain : JavaPlugin() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var vote: VoteLoop? = null

    override fun onEnable() {
        ExamplePlaceholders.register()
        ExampleInstaller.install()
        scope.launch {
            val result = SurfHud.reload()
            if (!result.success) {
                logger.warning("Demo HUD did not load: ${result.errors}")
                return@launch
            }
            vote = VoteLoop(scope, "Notch", "jeb_").also { it.start() }
        }
    }

    override fun onDisable() {
        vote?.stop()
        scope.cancel()
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all core tests pass.

- [ ] **Step 4: End-to-end run on a Paper dev server**

1. Check how `runServer` is configured for this project: `./gradlew :surf-hud-paper-example:tasks --all | grep -i run` and the surf gradle plugin's `runServer` settings. Make the example's `runServer` load the surf-hud-paper shadow jar, either with `pluginJars(project(":surf-hud-paper").tasks.named("shadowJar"))` in a `tasks.runServer { }` block or through the surf plugin's own option. Set the Minecraft version to `26.3` if `runServer` asks for one.
2. Start the server: `./gradlew :surf-hud-paper-example:runServer`. In the server log, expect `Loaded 2 HUDs and 1 popups in … slots; pack SHA-1 …`.
3. Apply the build-logic dev runtime to the example module so the dev client joins this server with the generated pack. In `surf-hud-paper-example/build.gradle.kts`, add the plugin `id("surf-hud.minecraft-dev-runtime")` and a `minecraftDev { version = "26.3"; resourcePack("surf-hud", layout.projectDirectory.dir("run/plugins/surf-hud-paper/build/pack")) }` block (check the exact server run directory and port first; set `serverPort` to the Paper server's port). Then run `./gradlew :surf-hud-paper-example:runDevClient`, and optionally a second client with `-Pminecraft.username=Dev2`.
4. Check and screenshot (store in `docs/screenshots/hud-engine/`):
   - all demo elements at their anchors (title, status, timer, money, health, player) plus the default `welcome` text;
   - Dev and Dev2 see their own money and name;
   - the vote popup with both heads, the live bar and counts, then the result with the loser dimmed;
   - edit `layouts/demo.yml` (for example change the title text), run `/hud reload`, and the change shows without restarting. Change an anchor, run `/hud reload`, the zip is regenerated, and the client shows the new position after reloading the pack (F3+T);
   - `/hud toggle`, `/hud hide Dev demo`, `/hud popup Dev vote title=Test left=Notch right=jeb_`.
5. Write down every check that fails and fix it before continuing. A layout `y` that is off by a pixel or two only needs its YAML changed.

- [ ] **Step 5: Admin reference**

Write `docs/hud-config.md`. It's a concise reference built from the spec's "Directory layout", "Configuration model", "Expressions" and "Placeholders" sections, written for server admins: folder layout, every field with its default, placeholder syntax, the built-in placeholder list, the bridges, the `/hud` commands with permissions, and how to merge `build/pack` into the main pack (which must not ship its own `core/text.vsh`). Show the demo files as examples.

- [ ] **Step 6: Commit**

```bash
git add surf-hud-paper-example docs/hud-config.md docs/screenshots
git commit -m "feat(example): demo HUD and vote popup on the new engine" -m "Co-Authored-By: Claude Opus 5.5 (1M context) <noreply@anthropic.com>"
```

---

## Self-review notes

- Spec coverage: config.yml (T3), folders and ids (T3, T6), images/texts (T5), layouts and element types (T6, T10), huds/popups (T6, T12), expressions and formats (T2, T9), placeholder sources and intervals (T8, T13, T14), pack and slots (T6, T7), vanilla assets (T4), rendering (T9, T10), heads and await (T11, T12), transport and diff (T12), popups (T12), API (T1, T12), commands (T15), error handling (T3, T6, T12), example (T16), testing (every core task plus T16 end to end).
- Deviation from the spec: the fallback head is generated when `assets/heads/fallback.png` is missing, instead of being bundled as a PNG. The behavior is the same and no binary file has to be shipped.
- Known risks, each with an early check: creative/Adventure 5 binary compatibility (T1 step 6), `nodeFromParent` on maps in the Configurate fork (T3 step 4), and the MiniMessage target overload (T14 step 3).
