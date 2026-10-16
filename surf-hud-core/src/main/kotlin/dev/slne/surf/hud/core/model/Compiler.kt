package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.config.*
import dev.slne.surf.hud.core.expression.*
import dev.slne.surf.hud.core.pack.VanillaAssets
import net.kyori.adventure.bossbar.BossBar
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import java.nio.file.InvalidPathException
import java.nio.file.Path

sealed interface CompileResult {
    data class Success(val model: HudModel) : CompileResult
    data class Failure(val errors: List<String>) : CompileResult
}

/**
 * Validates a [DefinitionSet] and compiles it into a [HudModel]: glyph sets, layouts, HUDs and popups with
 * their shader slots and fonts. All errors are collected; any error makes the result a [CompileResult.Failure].
 * Image files are read from `assets/` below [dataFolder]; the output paths are checked against it.
 */
class Compiler(private val assets: VanillaAssets, private val dataFolder: Path) {
    fun compile(definitions: DefinitionSet): CompileResult {
        val errors = ErrorCollector(definitions.errors)
        val settings = definitions.settings
        val bossbarColor = settings(settings, errors)
        val builder = GlyphSetBuilder(assets, dataFolder.resolve("assets"), CharAllocator())

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
        outputPaths(settings.output, errors)
        return BossBar.Color.NAMES.value(settings.bossbarColor).also {
            if (it == null) errors.add("config.yml > bossbar-color", "unknown color '${settings.bossbarColor}' (expected ${BossBar.Color.NAMES.keys().joinToString()})")
        }
    }

    /**
     * The pack folder is deleted and rewritten on every write, so both output paths must resolve strictly inside
     * the data folder, away from `config.yml` and the folders holding user files, and the zip must not lie in
     * the pack folder (it would zip itself).
     */
    private fun outputPaths(output: OutputSettings, errors: ErrorCollector) {
        val root = dataFolder.toAbsolutePath().normalize()
        val reserved = RESERVED.map { root.resolve(it) }
        fun check(key: String, value: String): Path? {
            val path = try {
                root.resolve(value).normalize()
            } catch (e: InvalidPathException) {
                errors.add("config.yml > $key", "invalid path '$value': ${e.reason}")
                return null
            }
            val problem = when {
                !path.startsWith(root) || path == root -> "must be inside the plugin folder, was '$value'"
                reserved.any { path.startsWith(it) } -> "must not be or lie in ${RESERVED.joinToString()}, was '$value'"
                else -> null
            }
            if (problem != null) errors.add("config.yml > $key", problem)
            return path.takeIf { problem == null }
        }
        val folder = check("output.folder", output.folder)
        val zip = check("output.zip", output.zip)
        if (folder != null && zip != null && zip.startsWith(folder)) {
            errors.add("config.yml > output.zip", "must not lie in output.folder '${output.folder}', was '${output.zip}'")
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

        /** Files and folders of the data folder that hold user data; output paths must stay out of them. */
        val RESERVED = listOf("config.yml", "assets", "cache", "images", "texts", "layouts", "huds", "popups")
    }
}
