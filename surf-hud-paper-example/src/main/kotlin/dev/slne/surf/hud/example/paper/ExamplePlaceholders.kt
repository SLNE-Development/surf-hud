package dev.slne.surf.hud.example.paper

import dev.slne.surf.hud.api.SurfHud
import org.bukkit.Bukkit
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

/** Simulated data for the demo HUD: a drifting balance, a rank, stars and the time. */
object ExamplePlaceholders {
    private val money = ConcurrentHashMap<UUID, Double>()
    private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

    fun register() = with(SurfHud.placeholders) {
        // Cached for a second so the balance drifts by a few coins per second, not per HUD update.
        register("money", 1.seconds) { uuid, _ ->
            money.merge(uuid, 1000.0) { old, _ -> (old + Random.nextDouble(-25.0, 40.0)).coerceAtLeast(0.0) }
        }
        register("rank") { uuid, _ -> if (Bukkit.getPlayer(uuid)?.isOp == true) "Admin" else "Player" }
        register("stars", 1.seconds) { uuid, _ -> (uuid.hashCode() and 0x7FFFFFFF) % 50 }
        register("time", 1.seconds) { _, _ -> LocalTime.now().format(clock) }
    }
}
