package dev.slne.surf.hud.api

/** Outcome of [SurfHud.reload]: the errors (empty on success) and the SHA-1 of the generated pack. */
data class ReloadResult(val errors: List<String>, val packHash: String?) {
    val success: Boolean get() = errors.isEmpty()
}
