package dev.slne.surf.hud.core.expression

/** Thrown for invalid expression or template syntax; [position] is the character index in the source. */
class ExpressionException(message: String, val position: Int) : IllegalArgumentException("$message at position $position")

/**
 * Recursive-descent parser for HUD expressions:
 * `||` < `&&` < `== !=` < `< <= > >=` < `+ -` < `* /` < unary `! -` < literals, `[placeholders]`, calls, `( )`.
 */
object ExpressionParser {
    fun parse(source: String): Expr {
        val parser = Parser(source)
        val expression = parser.or()
        parser.skipWhitespace()
        if (!parser.atEnd()) throw ExpressionException("unexpected '${source[parser.pos]}'", parser.pos)
        return expression
    }

    private class Parser(val src: String) {
        var pos = 0

        fun atEnd() = pos >= src.length
        fun skipWhitespace() { while (!atEnd() && src[pos].isWhitespace()) pos++ }

        private fun accept(token: String): Boolean {
            skipWhitespace()
            if (!src.startsWith(token, pos)) return false
            // "|" and "&" alone are not operators; "<" must not swallow "<=".
            pos += token.length
            return true
        }

        fun or(): Expr {
            var left = and()
            while (accept("||")) left = Expr.Binary(BinaryOp.OR, left, and())
            return left
        }

        private fun and(): Expr {
            var left = equality()
            while (accept("&&")) left = Expr.Binary(BinaryOp.AND, left, equality())
            return left
        }

        private fun equality(): Expr {
            var left = comparison()
            while (true) {
                left = when {
                    accept("==") -> Expr.Binary(BinaryOp.EQ, left, comparison())
                    accept("!=") -> Expr.Binary(BinaryOp.NE, left, comparison())
                    else -> return left
                }
            }
        }

        private fun comparison(): Expr {
            var left = additive()
            while (true) {
                left = when {
                    accept("<=") -> Expr.Binary(BinaryOp.LE, left, additive())
                    accept(">=") -> Expr.Binary(BinaryOp.GE, left, additive())
                    accept("<") -> Expr.Binary(BinaryOp.LT, left, additive())
                    accept(">") -> Expr.Binary(BinaryOp.GT, left, additive())
                    else -> return left
                }
            }
        }

        private fun additive(): Expr {
            var left = multiplicative()
            while (true) {
                left = when {
                    accept("+") -> Expr.Binary(BinaryOp.PLUS, left, multiplicative())
                    accept("-") -> Expr.Binary(BinaryOp.MINUS, left, multiplicative())
                    else -> return left
                }
            }
        }

        private fun multiplicative(): Expr {
            var left = unary()
            while (true) {
                left = when {
                    accept("*") -> Expr.Binary(BinaryOp.TIMES, left, unary())
                    accept("/") -> Expr.Binary(BinaryOp.DIV, left, unary())
                    else -> return left
                }
            }
        }

        private fun unary(): Expr = when {
            peekNot() -> { pos++; Expr.Not(unary()) }
            accept("-") -> Expr.Negate(unary())
            else -> primary()
        }

        private fun peekNot(): Boolean {
            skipWhitespace()
            return !atEnd() && src[pos] == '!' && !src.startsWith("!=", pos)
        }

        private fun primary(): Expr {
            skipWhitespace()
            if (atEnd()) throw ExpressionException("unexpected end of expression", pos)
            val c = src[pos]
            return when {
                c == '(' -> {
                    pos++
                    val inner = or()
                    if (!accept(")")) throw ExpressionException("missing ')'", pos)
                    inner
                }
                c == '[' -> placeholder()
                c == '\'' || c == '"' -> string(c)
                c.isDigit() || c == '.' -> number()
                c.isLetter() -> identifier()
                else -> throw ExpressionException("unexpected '$c'", pos)
            }
        }

        private fun placeholder(): Expr {
            val start = pos
            val end = src.indexOf(']', pos)
            if (end < 0) throw ExpressionException("unclosed placeholder", start)
            val body = src.substring(pos + 1, end)
            val name = body.substringBefore(':')
            if (!PlaceholderRef.NAME.matches(name)) throw ExpressionException("invalid placeholder name '$name'", start)
            val argument = if (':' in body) body.substringAfter(':') else null
            pos = end + 1
            return Expr.Placeholder(PlaceholderRef(name.lowercase(), argument))
        }

        private fun string(quote: Char): Expr {
            val start = pos
            val end = src.indexOf(quote, pos + 1)
            if (end < 0) throw ExpressionException("unclosed string", start)
            pos = end + 1
            return Expr.Literal(Value.Str(src.substring(start + 1, end)))
        }

        private fun number(): Expr {
            val start = pos
            while (!atEnd() && (src[pos].isDigit() || src[pos] == '.')) pos++
            val value = src.substring(start, pos).toDoubleOrNull()
                ?: throw ExpressionException("invalid number '${src.substring(start, pos)}'", start)
            return Expr.Literal(Value.Num(value))
        }

        private fun identifier(): Expr {
            val start = pos
            while (!atEnd() && (src[pos].isLetterOrDigit() || src[pos] == '_')) pos++
            val name = src.substring(start, pos)
            when (name) {
                "true" -> return Expr.Literal(Value.Bool(true))
                "false" -> return Expr.Literal(Value.Bool(false))
            }
            val function = ExprFunction.byName(name) ?: throw ExpressionException("unknown function '$name'", start)
            if (!accept("(")) throw ExpressionException("expected '(' after '$name'", pos)
            val arguments = mutableListOf<Expr>()
            if (!accept(")")) {
                do arguments += or() while (accept(","))
                if (!accept(")")) throw ExpressionException("missing ')' after arguments of '$name'", pos)
            }
            if (arguments.size !in function.arity) {
                throw ExpressionException("'$name' takes ${describe(function.arity)} arguments, got ${arguments.size}", start)
            }
            return Expr.Call(function, arguments)
        }

        private fun describe(range: IntRange) =
            if (range.last == Int.MAX_VALUE) "at least ${range.first}" else if (range.first == range.last) "${range.first}" else "$range"
    }
}
