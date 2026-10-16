package dev.slne.surf.hud.core.model

import dev.slne.surf.hud.core.expression.ExpressionException

/** Collects compile errors as `path: message`, where a [DefinitionException.field] is appended to the path. */
class ErrorCollector(initial: List<String> = emptyList()) {
    val errors = initial.toMutableList()

    fun add(path: String, message: String) {
        errors += "$path: $message"
    }

    /** Runs [block]; a [DefinitionException] or [ExpressionException] becomes an error at [path] and yields `null`. */
    inline fun <T> at(path: String, block: () -> T): T? = try {
        block()
    } catch (e: DefinitionException) {
        add(if (e.field != null) "$path.${e.field}" else path, e.message ?: "invalid")
        null
    } catch (e: ExpressionException) {
        add(path, e.message ?: "invalid expression")
        null
    }
}
