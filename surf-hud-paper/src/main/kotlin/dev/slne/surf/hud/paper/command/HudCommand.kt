package dev.slne.surf.hud.paper.command

import dev.jorel.commandapi.arguments.ArgumentSuggestions
import dev.jorel.commandapi.kotlindsl.*
import dev.slne.surf.hud.api.SurfHud
import dev.slne.surf.hud.api.hudPlayer
import dev.slne.surf.hud.core.HudEngine
import kotlinx.coroutines.launch
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** Prefix of the per-subcommand permissions `surf.hud.command.<sub>`; `/hud` itself needs no permission. */
private const val PERMISSION = "surf.hud.command"

private val engine: HudEngine get() = SurfHud.instance as HudEngine

/** `/hud reload | generate | show | hide | popup | toggle`. */
fun hudCommand() = commandTree("hud") {
    literalArgument("reload") {
        withPermission("$PERMISSION.reload")
        anyExecutor { sender, _ -> launch(sender) { reportReload(sender) } }
    }

    literalArgument("generate") {
        withPermission("$PERMISSION.generate")
        anyExecutor { sender, _ ->
            launch(sender) { sender.sendPlainMessage("surf-hud pack written, SHA-1 ${engine.generatePack()}") }
        }
    }

    literalArgument("show") {
        withPermission("$PERMISSION.show")
        entitySelectorArgumentOnePlayer("player") {
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
        entitySelectorArgumentOnePlayer("player") {
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
        entitySelectorArgumentOnePlayer("player") {
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
        entitySelectorArgumentOnePlayer("player", optional = true) {
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
    engine.scope.launch {
        runCatching { block() }.onFailure { sender.sendPlainMessage(it.message ?: it.toString()) }
    }
}
