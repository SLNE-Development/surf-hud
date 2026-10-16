package dev.slne.surf.hud.buildlogic.paper

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/** Configures the Paper dev server that `surf-hud.paper-dev` adds to `runServer`. */
abstract class PaperDevExtension {
    /** The Paper server directory; defaults to `paper/` in `minecraftDev.runDirectory` (next to `client/`). */
    abstract val runDirectory: DirectoryProperty

    /**
     * Path of the project whose `shadowJar` (the surf-hud Paper plugin) the server loads next to this project's
     * own plugin jar; defaults to `:surf-hud-paper`. Ignored when it is this project.
     */
    abstract val hudPluginProject: Property<String>

    /**
     * The unzipped pack that surf-hud generates in the server (`output.folder` in its `config.yml`), linked into
     * every dev client as pack `surf-hud`; defaults to `plugins/surf-hud-paper/build/pack` in [runDirectory].
     */
    abstract val hudPackDirectory: DirectoryProperty

    /** Whether the server also downloads the surf API server plugins and their dependencies; defaults to `true`. */
    abstract val withSurfApi: Property<Boolean>
}
