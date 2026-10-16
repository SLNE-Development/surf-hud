package dev.slne.surf.hud.core

import dev.slne.surf.hud.core.config.PlacementDefinitionSerializer
import dev.slne.surf.hud.core.config.definition.DefinitionReader
import dev.slne.surf.hud.core.config.definition.definitions.PlacementDefinition
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
                    serializers.register(
                        PlacementDefinition::class.java,
                        PlacementDefinitionSerializer
                    )
                }
            }
            .build()
        return loader.load().get(type) ?: error("$fileName did not deserialize")
    }
}
