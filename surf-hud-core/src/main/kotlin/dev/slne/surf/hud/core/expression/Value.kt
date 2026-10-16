package dev.slne.surf.hud.core.expression

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.*

/** A value in a HUD expression or text. [Missing] stands for an unknown placeholder and renders as `?`. */
sealed interface Value {
    data class Num(val value: Double) : Value
    data class Str(val value: String) : Value
    data class Bool(val value: Boolean) : Value
    data class Comp(val value: Component) : Value
    data object Missing : Value

    /** The numeric view: numbers, and strings that parse as numbers (e.g. PlaceholderAPI output). */
    fun number(): Double? = when (this) {
        is Num -> value
        is Str -> value.trim().toDoubleOrNull()
        else -> null
    }

    /** True for values that `+` concatenates instead of adding. */
    fun isTextual(): Boolean = when (this) {
        is Str -> number() == null
        is Comp, is Bool -> true
        else -> false
    }

    fun text(): String = when (this) {
        is Num -> formatNumber(value)
        is Str -> value
        is Bool -> value.toString()
        is Comp -> PlainTextComponentSerializer.plainText().serialize(value)
        Missing -> "?"
    }

    companion object {
        private val DEFAULT_FORMAT: ThreadLocal<DecimalFormat> =
            ThreadLocal.withInitial { DecimalFormat("0.##", DecimalFormatSymbols(Locale.US)) }

        fun formatNumber(value: Double): String = DEFAULT_FORMAT.get().format(value)

        /** Converts a placeholder or variable value; `null` becomes an empty string. */
        fun of(value: Any?): Value = when (value) {
            null -> Str("")
            is Value -> value
            is Number -> Num(value.toDouble())
            is Boolean -> Bool(value)
            is Component -> Comp(value)
            else -> Str(value.toString())
        }
    }
}
