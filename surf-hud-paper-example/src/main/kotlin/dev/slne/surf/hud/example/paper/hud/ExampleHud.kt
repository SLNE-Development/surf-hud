package dev.slne.surf.hud.example.paper.hud

import dev.slne.surf.api.core.font.toSmallCaps
import dev.slne.surf.hud.api.hud
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.LocalTime
import java.time.format.DateTimeFormatter

object ExampleHud {
    private val TEXT = TextColor.color(0xC5F1FC)
    private val LABEL = TextColor.color(0xD5DCE2)
    private val MUTED = TextColor.color(0xA9B4BE)
    private val DANGER = TextColor.color(0xFFA7B1)

    private val clockFormat = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun apply(player: Player) {
        player.hud.set {
            line(0) {
                element("online", -1, online())
                center("clock", clock())
                element("ping", 1, ping(player))
            }
            line(1) {
                center("position", position(player))
            }
        }
    }

    fun single(player: Player) {
        player.hud.set {
            line(0) {
                element(
                    "single",
                    -3,
                    Component.text("Ich liege auf -3 und bin trotzdem mittig", MUTED)
                )
            }
        }
    }

    fun banner(player: Player) {
        player.hud.set {
            line(0) {
                element("left-far", -2, Component.text("-2", MUTED))
                element("left", -1, labelled("Links", "-1"))
                center("center", labelled("Mitte", "0"))
                element("right", 1, labelled("Rechts", "1"))
                element("right-far", 2, Component.text("2", MUTED))
            }
            line(1) {
                element("bold", -1, Component.text("Fetter Text", TEXT, TextDecoration.BOLD))
                element("plain", 1, Component.text("ohne Hintergrund", MUTED), background = false)
                element("smallcaps", 0, Component.text("Smallcaps Font".toSmallCaps(), MUTED))
            }
        }
    }

    fun update() {
        Bukkit.getOnlinePlayers().forEach { player ->
            val hud = player.hud
            hud.content("online", online())
            hud.content("clock", clock())
            hud.content("ping", ping(player))
            hud.content("position", position(player))
        }
    }

    private fun online() =
        labelled("Online", "${Bukkit.getOnlinePlayers().size}/${Bukkit.getMaxPlayers()}")

    private fun clock() = labelled("Uhrzeit", LocalTime.now().format(clockFormat))

    private fun ping(player: Player): Component {
        val ping = player.ping
        return labelled("Ping", "${ping}ms", if (ping > 150) DANGER else TEXT)
    }

    private fun position(player: Player): Component {
        val location = player.location
        return labelled("Position", "${location.blockX} ${location.blockY} ${location.blockZ}")
    }

    private fun labelled(label: String, value: String, valueColor: TextColor = TEXT): Component =
        Component.text()
            .append(Component.text("$label ", LABEL))
            .append(Component.text(value, valueColor))
            .build()
}
