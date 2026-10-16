package dev.slne.surf.hud.prototype.coreshader

import com.google.gson.JsonObject
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A looping fake vote between two player heads: [VOTE_SECONDS] of voting with simulated votes, then the result
 * for [RESULT_SECONDS], then hidden for [HIDDEN_SECONDS]. Rounds alternate the title between the 1.5x and the
 * 2x font so both can be compared in game.
 */
class VoteHud(private val meta: HudMetadata, private val left: PlayerHead, private val right: PlayerHead) {
    enum class Phase { VOTING, RESULT, HIDDEN }

    private val random = Random(42)
    private var round = -1
    private var leftVotes = 0
    private var rightVotes = 0
    private var lastTick = 0.0

    var phase = Phase.HIDDEN
        private set

    private var roundStart = 0.0
    private var secondsLeft = 0.0

    /** Advances the vote to time [t] (seconds); returns true when the static head components must be resent. */
    fun update(t: Double): Boolean {
        if (round < 0 || t - roundStart >= ROUND_SECONDS) {
            round++
            roundStart = t
            leftVotes = 0
            rightVotes = 0
            lastTick = t
        }
        val elapsed = t - roundStart
        val newPhase = when {
            elapsed < VOTE_SECONDS -> Phase.VOTING
            elapsed < VOTE_SECONDS + RESULT_SECONDS -> Phase.RESULT
            else -> Phase.HIDDEN
        }
        secondsLeft = (VOTE_SECONDS - elapsed).coerceAtLeast(0.0)
        if (newPhase == Phase.VOTING) simulateVotes(t, elapsed)
        val changed = newPhase != phase || elapsed < 0.001
        phase = newPhase
        return changed
    }

    /** Roughly 6 votes per second, with a bias that swings between the candidates during the round. */
    private fun simulateVotes(t: Double, elapsed: Double) {
        val bias = 0.5 + 0.3 * sin(elapsed / 6.0 + round * 1.3)
        var budget = ((t - lastTick) * 6).toInt()
        if (budget > 0) lastTick = t
        while (budget-- > 0) {
            if (random.nextDouble() < bias) leftVotes++ else rightVotes++
        }
    }

    private val winner: PlayerHead? get() = when {
        leftVotes > rightVotes -> left
        rightVotes > leftVotes -> right
        else -> null
    }

    private fun percent(votes: Int): Int {
        val total = leftVotes + rightVotes
        return if (total == 0) 0 else (votes * 100.0 / total).roundToInt()
    }

    /** The heavy parts that only change with the phase: the big heads and the mini heads. */
    fun staticParts(): List<JsonObject> {
        if (phase == Phase.HIDDEN) return emptyList()
        val (leftHead, rightHead) = when {
            phase == Phase.RESULT && winner == left -> left to right.dimmed(0.35)
            phase == Phase.RESULT && winner == right -> left.dimmed(0.35) to right
            else -> left to right
        }
        val bigSize = meta.pixels("pixel_big").size * 8
        val heads = HudElementBuilder(meta, "vote_heads").apply {
            moveTo(-HEAD_CENTER - bigSize / 2).image("pixel_big", leftHead.pixels)
            moveTo(HEAD_CENTER - bigSize / 2).image("pixel_big", rightHead.pixels)
        }.build(Align.LEFT)
        val smallSize = meta.pixels("pixel_small").size * 8
        val mini = HudElementBuilder(meta, "vote_mini").apply {
            image("pixel_small", leftHead.pixels)
            moveTo(smallSize + MINI_TEXT_WIDTH).image("pixel_small", rightHead.pixels)
        }.build(Align.LEFT)
        return listOf(heads, mini)
    }

    /** Title, VS, names, timer bar with vote stats, countdown and the mini vote text; resent every update. */
    fun dynamicParts(): List<JsonObject> {
        if (phase == Phase.HIDDEN) return emptyList()
        val result = phase == Phase.RESULT
        val parts = mutableListOf<JsonObject>()

        val twoX = round % 2 == 1
        val titleElement = if (twoX) "vote_title_2x" else "vote_title_15"
        val titleSet = if (twoX) "title_2x" else "title_15"
        parts += HudElementBuilder(meta, titleElement)
            .text("Who builds the next spawn? [${if (twoX) "2x" else "1.5x"}]", color = "#FFFFFF", set = titleSet)
            .build(Align.CENTER)

        parts += HudElementBuilder(meta, "vote_vs").text("VS", color = "#FFD23C", set = "vs").build(Align.CENTER)

        parts += HudElementBuilder(meta, "vote_names").apply {
            for ((head, center) in listOf(left to -HEAD_CENTER, right to HEAD_CENTER)) {
                val color = when {
                    result && winner == head -> "#FFD23C"
                    result -> "#7A7A7A"
                    else -> "#FFFFFF"
                }
                moveTo(center - textWidth(head.name) / 2).text(head.name, color = color)
            }
        }.build(Align.LEFT)

        parts += HudElementBuilder(meta, "vote_bar").apply {
            val fraction = secondsLeft / VOTE_SECONDS
            moveTo(-BAR_WIDTH / 2).strip("bar_bg", BAR_WIDTH)
            moveTo(-BAR_WIDTH / 2 + 1).partialStrip("bar_tint", BAR_WIDTH - 2, ((BAR_WIDTH - 2) * fraction).roundToInt(), timeColor(fraction))
            val leftStats = "${percent(leftVotes)}% ($leftVotes)"
            moveTo(-BAR_WIDTH / 2 - 4 - textWidth(leftStats)).text(leftStats, color = "#9BE89B")
            moveTo(BAR_WIDTH / 2 + 4).text("${percent(rightVotes)}% ($rightVotes)", color = "#8EC5FF")
        }.build(Align.LEFT)

        parts += HudElementBuilder(meta, "vote_timer").apply {
            if (result) {
                text(winner?.let { "${it.name} wins!" } ?: "Tie!", color = "#FFD23C")
            } else {
                val seconds = kotlin.math.ceil(secondsLeft).toInt()
                icon("clock").gap(2).text("%d:%02d".format(seconds / 60, seconds % 60), color = "#B4E1FF")
            }
        }.build(Align.CENTER)

        val smallSize = meta.pixels("pixel_small").size * 8
        parts += HudElementBuilder(meta, "vote_mini").apply {
            val text = "${percent(leftVotes)}% : ${percent(rightVotes)}%"
            moveTo(smallSize + (MINI_TEXT_WIDTH - textWidth(text)) / 2).text(text)
        }.build(Align.LEFT)
        return parts
    }

    /** Green while plenty of time is left, through yellow to red at the end. */
    private fun timeColor(fraction: Double): String {
        val f = fraction.coerceIn(0.0, 1.0)
        val r = if (f > 0.5) (255 * (1 - f) * 2).roundToInt() else 255
        val g = if (f > 0.5) 220 else (220 * f * 2).roundToInt()
        return "#%02X%02X%02X".format(r, g, 60)
    }

    private companion object {
        const val VOTE_SECONDS = 30.0
        const val RESULT_SECONDS = 5.0
        const val HIDDEN_SECONDS = 5.0
        const val ROUND_SECONDS = VOTE_SECONDS + RESULT_SECONDS + HIDDEN_SECONDS
        /** Distance of each big head's center from the screen center, in GUI pixels. */
        const val HEAD_CENTER = 50
        const val BAR_WIDTH = 100
        const val MINI_TEXT_WIDTH = 64
    }
}
