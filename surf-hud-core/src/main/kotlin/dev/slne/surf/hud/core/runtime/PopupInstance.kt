package dev.slne.surf.hud.core.runtime

import dev.slne.surf.hud.api.PopupHandle
import dev.slne.surf.hud.core.expression.Value
import dev.slne.surf.hud.core.model.PopupSpec
import java.util.concurrent.ConcurrentHashMap

/** One open popup; [spec] is swapped on reload, [openedAt] restarts when a unique popup is retriggered. */
class PopupInstance(
    @Volatile var spec: PopupSpec,
    val variables: ConcurrentHashMap<String, Value>,
    @Volatile var openedAt: Long,
) {
    @Volatile
    var open = true

    lateinit var handle: PopupHandle
}

/** The API handle of a [PopupInstance]; variable updates and closing go through its [owner]. */
class PopupHandleImpl(private val instance: PopupInstance, private val owner: PlayerHud) : PopupHandle {
    override val id: String get() = instance.spec.id
    override val isOpen: Boolean get() = instance.open

    override fun update(variables: Map<String, Any?>) {
        if (!instance.open) return
        variables.forEach { (key, value) -> instance.variables[key.lowercase()] = Value.of(value) }
        owner.refresh()
    }

    override fun update(vararg variables: Pair<String, Any?>) = update(variables.toMap())

    override fun close() = owner.closePopup(instance)
}
