package dev.slne.surf.hud.core.config.definition

import dev.slne.surf.api.core.config.serializer.surfSpongeConfigSerializers
import dev.slne.surf.api.core.config.surfConfigApi
import dev.slne.surf.hud.core.config.PlacementDefinitionSerializer
import dev.slne.surf.hud.core.config.definition.definitions.PlacementDefinition
import java.nio.file.Path

/** Reads one YAML file into [type]; a missing file yields the type's defaults. */
interface DefinitionReader {
    fun <T : Any> read(type: Class<T>, folder: Path, fileName: String): T
}

/** Production reader backed by surfConfigApi. Note that surfConfigApi writes each file back after loading. */
class SurfConfigDefinitionReader : DefinitionReader {
    init {
        SERIALIZERS_REGISTERED
    }

    override fun <T : Any> read(type: Class<T>, folder: Path, fileName: String): T =
        surfConfigApi.createSpongeYmlConfigManager(
            configClass = type,
            configFolder = folder,
            configFileName = fileName,
        ).config
}

/** Registers the placement serializer on the global surf-api serializers once per JVM. */
private val SERIALIZERS_REGISTERED by lazy {
    surfSpongeConfigSerializers.registerClassSerializer(
        PlacementDefinition::class.java,
        PlacementDefinitionSerializer
    )
}
