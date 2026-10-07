package dev.slne.surf.hud.example.paper.command

import dev.jorel.commandapi.kotlindsl.commandTree
import dev.jorel.commandapi.kotlindsl.literalArgument
import dev.jorel.commandapi.kotlindsl.playerExecutor
import dev.slne.surf.hud.api.hud
import dev.slne.surf.hud.example.paper.hud.ExampleHud

fun hudExampleCommand() = commandTree("hudexample") {
    withPermission("surf.hud.example.command")

    literalArgument("default") {
        playerExecutor { player, _ -> ExampleHud.apply(player) }
    }

    literalArgument("single") {
        playerExecutor { player, _ -> ExampleHud.single(player) }
    }

    literalArgument("banner") {
        playerExecutor { player, _ -> ExampleHud.banner(player) }
    }

    literalArgument("toggle") {
        playerExecutor { player, _ ->
            val hud = player.hud
            hud.visible = !hud.visible
        }
    }

    literalArgument("clear") {
        playerExecutor { player, _ -> player.hud.clear() }
    }
}
