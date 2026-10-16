package dev.slne.surf.hud.core.placeholder

import dev.slne.surf.hud.core.expression.Expr
import dev.slne.surf.hud.core.expression.PlaceholderRef
import dev.slne.surf.hud.core.expression.Scope
import dev.slne.surf.hud.core.expression.Value
import java.util.*

/**
 * Placeholder lookup while rendering one layout: popup [variables] first, then `[width:id]` from [widths],
 * then registered placeholders through the player's [cache].
 */
class RenderScope(
    private val viewer: UUID,
    private val cache: PlaceholderCache,
    private val variables: Map<String, Value>,
    private val warnings: WarningSink,
    private val widths: Map<String, Int> = emptyMap(),
) : Scope {
    override fun resolve(ref: PlaceholderRef): Value {
        if (ref.argument == null) variables[ref.name]?.let { return it }
        if (ref.name == "width") return ref.argument?.let(widths::get)?.let { Value.Num(it.toDouble()) } ?: Value.Missing
        return cache.resolve(viewer, ref)
    }

    /** Returns a scope sharing this one's state but answering `[width:id]` from [widths]. */
    fun withWidths(widths: Map<String, Int>) = RenderScope(viewer, cache, variables, warnings, widths)

    override fun warn(expression: Expr, message: String) = warnings.once("expr:$expression:$message", "$message in $expression")
}
