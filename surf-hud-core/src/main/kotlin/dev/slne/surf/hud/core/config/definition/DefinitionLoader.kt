package dev.slne.surf.hud.core.config.definition

import dev.slne.surf.hud.core.config.HudSettingsConfig
import dev.slne.surf.hud.core.config.definition.definitions.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.streams.asSequence

/** Reads `config.yml` and every `.yml` file below `images/`, `texts/`, `layouts/`, `huds/` and `popups/`. */
class DefinitionLoader(private val reader: DefinitionReader) {
    fun load(root: Path): DefinitionSet {
        val errors = mutableListOf<String>()
        val settings =
            runCatching { reader.read(HudSettingsConfig::class.java, root, "config.yml") }
                .getOrElse {
                    errors += "config.yml: ${it.message}"
                    HudSettingsConfig()
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
    ): Map<String, SourcedDefinition<D>> {
        val dir = root.resolve(name)
        if (!dir.exists()) return emptyMap()
        val files = Files.walk(dir).use { stream ->
            stream.asSequence().filter { it.isRegularFile() && it.name.endsWith(".yml") }.sorted()
                .toList()
        }
        val result = LinkedHashMap<String, SourcedDefinition<D>>()
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
                    result[id] = SourcedDefinition(id, relative, definition)
                }
            }
        }
        return result
    }
}
