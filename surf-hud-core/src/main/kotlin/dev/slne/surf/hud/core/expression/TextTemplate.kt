package dev.slne.surf.hud.core.expression

import java.text.DecimalFormat

/**
 * A HUD text: MiniMessage with `[placeholder]` and `{expression}` or `{expression | DecimalFormat pattern}`.
 * `\[` and `\{` insert the bracket literally. Literal segments are MiniMessage source.
 */
class TextTemplate(val segments: List<Segment>) {
    sealed interface Segment {
        data class Literal(val miniMessage: String) : Segment
        data class Placeholder(val ref: PlaceholderRef) : Segment
        data class Expression(val expression: Expr, val format: String?) : Segment
    }

    fun placeholders(): Set<PlaceholderRef> = segments.flatMapTo(HashSet()) {
        when (it) {
            is Segment.Literal -> emptySet()
            is Segment.Placeholder -> setOf(it.ref)
            is Segment.Expression -> it.expression.placeholders()
        }
    }

    companion object {
        private val PLACEHOLDER = Regex("""\[([A-Za-z0-9_.-]+)(?::([^\]]*))?]""")

        fun parse(source: String): TextTemplate {
            val segments = mutableListOf<Segment>()
            val literal = StringBuilder()
            fun flush() {
                if (literal.isNotEmpty()) segments += Segment.Literal(literal.toString())
                literal.clear()
            }
            var i = 0
            while (i < source.length) {
                val c = source[i]
                when {
                    c == '\\' && i + 1 < source.length && source[i + 1] in "[{" -> {
                        literal.append(source[i + 1]); i += 2
                    }
                    c == '[' -> {
                        val match = PLACEHOLDER.matchAt(source, i)
                        if (match == null) {
                            literal.append(c); i++
                        } else {
                            flush()
                            segments += Segment.Placeholder(PlaceholderRef(match.groupValues[1].lowercase(), match.groups[2]?.value))
                            i = match.range.last + 1
                        }
                    }
                    c == '{' -> {
                        flush()
                        val end = closingBrace(source, i)
                        segments += expression(source.substring(i + 1, end), i + 1)
                        i = end + 1
                    }
                    else -> { literal.append(c); i++ }
                }
            }
            flush()
            return TextTemplate(segments)
        }

        private fun closingBrace(source: String, open: Int): Int {
            var quote: Char? = null
            for (i in open + 1 until source.length) {
                val c = source[i]
                when {
                    quote != null -> if (c == quote) quote = null
                    c == '\'' || c == '"' -> quote = c
                    c == '}' -> return i
                }
            }
            throw ExpressionException("unclosed '{'", open)
        }

        /** Splits off the format at the last single `|` outside quotes (`||` is the or operator). */
        private fun expression(body: String, offset: Int): Segment.Expression {
            var separator = -1
            var quote: Char? = null
            var i = 0
            while (i < body.length) {
                val c = body[i]
                when {
                    quote != null -> if (c == quote) quote = null
                    c == '\'' || c == '"' -> quote = c
                    c == '|' && i + 1 < body.length && body[i + 1] == '|' -> i++
                    c == '|' -> separator = i
                }
                i++
            }
            val source = if (separator < 0) body else body.substring(0, separator)
            val format = if (separator < 0) null else body.substring(separator + 1).trim()
            if (format != null) {
                try {
                    DecimalFormat(format)
                } catch (e: IllegalArgumentException) {
                    throw ExpressionException("invalid number format '$format'", offset + separator)
                }
            }
            val expression = try {
                ExpressionParser.parse(source)
            } catch (e: ExpressionException) {
                throw ExpressionException(e.message!!.substringBeforeLast(" at position"), offset + e.position)
            }
            return Segment.Expression(expression, format)
        }
    }
}
