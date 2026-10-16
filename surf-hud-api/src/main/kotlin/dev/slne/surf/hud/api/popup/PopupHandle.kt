package dev.slne.surf.hud.api

/** One open popup instance. */
interface PopupHandle {
    val id: String

    /** `false` once the popup closed, expired or was pushed out of its stack. */
    val isOpen: Boolean

    /** Merges [variables] into the popup's variables and re-renders it on the next tick. No-op when closed. */
    fun update(variables: Map<String, Any?>)

    fun update(vararg variables: Pair<String, Any?>) = update(variables.toMap())

    fun close()
}
