package dev.slne.surf.hud.prototype.coreshader

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import java.nio.file.Path
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

private val gson = GsonBuilder().disableHtmlEscaping().create()

/** The values a server would track per player; here they are simulated so every update changes something. */
private class PlayerState(val name: String, index: Int) {
    private val startedAt = System.currentTimeMillis()
    private val phase = index * 1.7
    private val baseMoney = 1_250 + index * 98_000

    fun money(t: Double) = baseMoney + (t * 37).toInt()
    fun health(t: Double) = 0.5 + 0.5 * sin(t * 2 * PI / 8 + phase)
    fun elapsedSeconds() = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
    fun lives(t: Double) = 3 - ((t / 5).toInt() + (phase * 2).toInt()) % 3
    fun stars(t: Double) = (t / 2).toInt() % 100 + if (phase > 0) 40 else 0
}

/**
 * Composes every HUD element for one player. Each element shows one kind the HUD needs: plain text,
 * icons as glyphs, text on a background, text on a background with an icon prefix, a progress bar, and
 * text with an icon prefix.
 */
private fun composeHud(meta: HudMetadata, player: PlayerState, t: Double): List<JsonObject> {
    val money = NumberFormat.getIntegerInstance(Locale.US).format(player.money(t))
    val health = player.health(t)
    val elapsed = player.elapsedSeconds()
    val timer = "%02d:%02d".format(elapsed / 60, elapsed % 60)

    val title = HudElementBuilder(meta, "title")
        .text("SURF HUD", color = "#FFD23C")
        .text(" core shader demo", color = "#FFFFFF")
        .build(Align.CENTER)

    val status = HudElementBuilder(meta, "status").apply {
        icon("heart").gap(1).text("x${player.lives(t)}").gap(4)
        icon("star").gap(1).text("${player.stars(t)}").gap(4)
        icon("clock")
    }.build(Align.LEFT)

    val timerElement = HudElementBuilder(meta, "timer").apply {
        val panelWidth = textWidth(timer) + 7
        strip("panel", panelWidth)
        moveTo(4).text(timer, color = "#B4E1FF")
        moveTo(panelWidth)
    }.build(Align.RIGHT)

    val moneyElement = HudElementBuilder(meta, "money").apply {
        val panelWidth = 3 + 10 + 2 + textWidth(money) + 3
        strip("panel", panelWidth)
        moveTo(3).icon("coin").gap(2).text(money, color = "#FFE07A")
        moveTo(panelWidth)
    }.build(Align.RIGHT)

    val healthElement = HudElementBuilder(meta, "health").apply {
        val barWidth = 82
        val percent = "${(health * 100).roundToInt()}%"
        icon("heart").gap(2)
        val barStart = x
        strip("bar_bg", barWidth)
        moveTo(barStart + 1).partialStrip("bar_fill", barWidth - 2, ((barWidth - 2) * health).roundToInt())
        moveTo(barStart + barWidth + 3).text(percent.padStart(4))
    }.build(Align.RIGHT)

    val playerElement = HudElementBuilder(meta, "player").apply {
        icon("player").gap(2).text(player.name, color = "#9BE89B")
    }.build(Align.LEFT)

    return listOf(title, status, timerElement, moneyElement, healthElement, playerElement)
}

/**
 * Pushes live HUD data to each player's own bossbar on the vanilla dev server.
 *
 * Vanilla RCON accepts at most 1460 bytes per request, which one full HUD line exceeds, so each element is
 * appended to command storage and the bossbar name is an `nbt` component that joins those parts. A real
 * plugin sends the finished component in one bossbar packet instead.
 *
 * Usage: HudDemo <hud-metadata.json> <rcon port> <rcon password> <player,player,…> [seconds]
 */
fun main(args: Array<String>) {
    val meta = HudMetadata.read(Path.of(args[0]))
    val players = args[3].split(',').filter { it.isNotBlank() }.mapIndexed { index, name -> PlayerState(name, index) }
    val runForMillis = args.getOrNull(4)?.toLong()?.times(1000) ?: Long.MAX_VALUE
    val start = System.currentTimeMillis()

    Rcon("localhost", args[1].toInt(), args[2]).use { rcon ->
        // Two demos writing the same storage make the HUD flicker between their values. The newest demo claims
        // the storage; an older one that sees a different owner stops (stopping Gradle does not always kill it).
        val owner = "demo-${ProcessHandle.current().pid()}-$start"
        rcon.command("data modify storage $OWNER_STORAGE owner set value \"$owner\"")

        for (player in players) {
            val bar = bossbarId(player)
            rcon.command("bossbar add $bar \"\"")
            for (setting in listOf("color ${meta.bossbarColor}", "style progress", "value 0", "players ${player.name}", "visible true")) {
                println("$bar $setting: ${rcon.command("bossbar set $bar $setting")}")
            }
        }

        var firstRound = true
        while (System.currentTimeMillis() - start < runForMillis) {
            if (owner !in rcon.command("data get storage $OWNER_STORAGE owner")) {
                println("Another HudDemo took over the HUD storage, stopping.")
                return
            }
            val t = (System.currentTimeMillis() - start) / 1000.0
            for (player in players) {
                val storage = bossbarId(player)
                rcon.command("data modify storage $storage parts set value []")
                for (element in composeHud(meta, player, t)) {
                    val snbt = gson.toJson(element)
                    val reply = rcon.command("data modify storage $storage parts append value $snbt")
                    if (firstRound) println("${player.name}: element ${snbt.toByteArray().size} bytes -> $reply")
                }
                val name = """{"nbt":"parts[]","storage":"$storage","interpret":true,"separator":""}"""
                val reply = rcon.command("bossbar set ${bossbarId(player)} name $name")
                if (firstRound) println("${player.name}: bossbar -> $reply")
            }
            firstRound = false
            Thread.sleep(250)
        }
    }
}

private const val OWNER_STORAGE = "surf:hud_demo"

private fun bossbarId(player: PlayerState) = "surf:hud_${player.name.lowercase(Locale.ROOT)}"
