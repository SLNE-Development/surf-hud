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
import dev.slne.surf.hud.core.model.HeadSpec
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

    /** The active model with the renderer and head service built for it; published as one unit on reload. */
    class Runtime(val model: HudModel, val renderer: LayoutRenderer, val heads: HeadService)

    /** The active [Runtime], or `null` before the first successful reload. Read it once per frame. */
    @Volatile
    var runtime: Runtime? = null
        private set

    val model: HudModel? get() = runtime?.model

    val heads: HeadService get() = requireRuntime().heads

    val renderer: LayoutRenderer get() = requireRuntime().renderer

    private val players = ConcurrentHashMap<UUID, PlayerHud>()
    private val reloadMutex = Mutex()

    override val huds: Set<String> get() = model?.huds?.keys.orEmpty()
    override val popups: Set<String> get() = model?.popups?.keys.orEmpty()

    override fun player(uuid: UUID): HudPlayer = hud(uuid)

    /** The player's HUD state, created (with the default HUDs shown) on first access. */
    fun hud(uuid: UUID): PlayerHud = players.computeIfAbsent(uuid) { id ->
        PlayerHud(id, this).also { player -> model?.let { player.onModelChanged(it, first = true) } }
    }

    /** The active model; throws [IllegalStateException] before the first successful reload. */
    fun requireModel(): HudModel = requireRuntime().model

    /** The active runtime; throws [IllegalStateException] before the first successful reload. */
    fun requireRuntime(): Runtime = runtime ?: throw IllegalStateException("surf-hud has not loaded a configuration yet")

    /** The player joined: the bossbar is shown again on the next tick. */
    fun handleJoin(uuid: UUID) = hud(uuid).invalidate()

    /** The player left: drops its state and closes its popups. */
    fun handleQuit(uuid: UUID) {
        players.remove(uuid)?.dispose()
    }

    /** Call once per tick on the player's thread. */
    fun update(uuid: UUID) {
        players[uuid]?.update()
    }

    /** A fresh per-player placeholder cache using the active model's default intervals. */
    fun newPlaceholderCache() = PlaceholderCache(
        placeholderRegistry, clock, requireModel().settings.placeholderIntervals.millis(), warnings, resolverErrors,
    )

    override suspend fun popup(id: String, viewers: Collection<UUID>, variables: Map<String, Any?>): List<PopupHandle> =
        coroutineScope { viewers.map { async { hud(it).popup(id, variables) } }.awaitAll() }

    override suspend fun reload(): ReloadResult = reloadMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                doReload()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log.atSevere().withCause(e).log("Reload failed")
                failed(listOf("Reload failed: ${e.message}"))
            }
        }
    }

    /** Loads, compiles and writes the pack; swaps the new model in only when every step succeeded. */
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
        val heads = HeadService(
            skinSources, http, root.resolve("cache/heads"), Durations.parse(settings.heads.cacheTtl), clock, scope,
            fallbackHead(root.resolve("assets").resolve(settings.heads.fallback)),
        )
        val first = runtime == null
        runtime = Runtime(model, LayoutRenderer(model, TextRenderer(warnings)), heads)
        players.values.forEach { it.onModelChanged(model, first) }
        log.atInfo().log("Loaded %d HUDs and %d popups in %d slots; pack SHA-1 %s", model.huds.size, model.popups.size, model.slots.size, hash)
        return ReloadResult(emptyList(), hash)
    }

    /** Regenerates the pack from the active model; waits for a running reload. */
    suspend fun generatePack(): String = reloadMutex.withLock { withContext(Dispatchers.IO) { writePack(requireModel()) } }

    private fun writePack(model: HudModel): String {
        val root = platform.dataFolder
        val generator = PackGenerator(model)
        return PackWriter.write(
            generator.build(), generator.packMeta(),
            root.resolve(model.settings.output.zip), root.resolve(model.settings.output.folder),
        )
    }

    private fun failed(errors: List<String>): ReloadResult {
        errors.forEach { log.atSevere().log("%s", it) }
        return ReloadResult(errors, null)
    }

    /** Reads the fallback head (an 8x8 face or a full skin); a generated face when missing or unreadable. */
    private fun fallbackHead(file: Path): HeadPixels {
        if (!file.exists()) return HeadPixels.placeholder("fallback")
        val image = runCatching { ImageIO.read(file.toFile()) }.getOrNull() ?: return HeadPixels.placeholder("fallback")
        return if (image.width == 8 && image.height == 8) HeadPixels(Array(8) { row -> IntArray(8) { image.getRGB(it, row) } })
        else SkinFace.extract(image)
    }

    /**
     * With `heads.await-loaded`, waits (up to the timeout) for every head the placements would draw. Skips
     * waiting when the player is offline or the player's thread does not run the lookup within the timeout.
     */
    internal suspend fun awaitHeads(player: PlayerHud, placements: List<Placement>, variables: Map<String, Value>) {
        val runtime = requireRuntime()
        val settings = runtime.model.settings.heads
        if (!settings.awaitLoaded) return
        if (placements.none { p -> p.layout.elements.any { it is HeadSpec } }) return
        val timeout = Durations.parse(settings.timeout)
        val names = withTimeoutOrNull(timeout) { onPlayerThread(player.uuid) { player.headNames(placements, variables) } } ?: return
        coroutineScope { names.distinct().map { async { runtime.heads.await(it, timeout) } }.awaitAll() }
    }

    /** Runs [block] on the player's thread; `null` when the player is offline or [block] failed. */
    private suspend fun <T> onPlayerThread(uuid: UUID, block: () -> T): T? = suspendCancellableCoroutine { continuation ->
        val scheduled = platform.execute(uuid) { continuation.resume(runCatching(block).getOrNull()) }
        if (!scheduled) continuation.resume(null)
    }

    /** Hides every player's HUD and cancels background work. */
    fun shutdown() {
        players.values.forEach(PlayerHud::dispose)
        players.clear()
        scope.cancel()
    }
}
