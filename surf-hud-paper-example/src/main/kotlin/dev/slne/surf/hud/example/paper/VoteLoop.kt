package dev.slne.surf.hud.example.paper

import dev.slne.surf.hud.api.SurfHud
import kotlinx.coroutines.*
import org.bukkit.Bukkit
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Runs vote rounds for everyone online: 30 s voting with live counts, 5 s result, 5 s pause. */
class VoteLoop(private val scope: CoroutineScope, private val left: String, private val right: String) {
    private var job: Job? = null

    fun start() {
        job = scope.launch { while (isActive) round() }
    }

    fun stop() {
        job?.cancel()
    }

    private suspend fun round() {
        val viewers = Bukkit.getOnlinePlayers().map { it.uniqueId }
        if (viewers.isEmpty()) {
            delay(5.seconds)
            return
        }
        val total = 30
        var leftVotes = 0
        var rightVotes = 0
        val handles = SurfHud.popup(
            "vote", viewers, mapOf(
                "title" to "Who wins?", "left" to left, "right" to right,
                "left_votes" to 0, "right_votes" to 0, "time_left" to total, "time_total" to total,
                "left_brightness" to 1, "right_brightness" to 1,
            )
        )
        val start = System.currentTimeMillis()
        while (true) {
            val elapsed = (System.currentTimeMillis() - start) / 1000.0
            if (elapsed >= total) break
            // About 6 votes per second, with a bias that swings between the candidates.
            val bias = 0.5 + 0.2 * sin(elapsed / 5.0)
            repeat(Random.nextInt(0, 3)) { if (Random.nextDouble() < bias) leftVotes++ else rightVotes++ }
            handles.forEach { it.update("left_votes" to leftVotes, "right_votes" to rightVotes, "time_left" to (total - elapsed).toInt()) }
            delay(250.milliseconds)
        }
        val leftWins = leftVotes >= rightVotes
        handles.forEach {
            it.update(
                "title" to "${if (leftWins) left else right} wins!", "time_left" to 0,
                "left_brightness" to if (leftWins) 1 else 0.4, "right_brightness" to if (leftWins) 0.4 else 1,
            )
        }
        delay(5.seconds)
        handles.forEach { it.close() }
        delay(5.seconds)
    }
}
