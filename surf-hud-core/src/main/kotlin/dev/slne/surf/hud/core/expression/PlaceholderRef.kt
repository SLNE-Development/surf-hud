package dev.slne.surf.hud.core.expression

/** A placeholder reference `[name]` or `[name:argument]`; [name] is stored lower case. */
data class PlaceholderRef(val name: String, val argument: String? = null) {
    override fun toString() = if (argument == null) "[$name]" else "[$name:$argument]"

    companion object {
        /** Characters allowed in a placeholder name. */
        val NAME = Regex("[A-Za-z0-9_.-]+")
    }
}
