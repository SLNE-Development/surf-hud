package dev.slne.surf.hud.core.expression

/** Resolves placeholders while an expression is evaluated and receives evaluation warnings. */
fun interface Scope {
    fun resolve(ref: PlaceholderRef): Value
    fun warn(expression: Expr, message: String) {}
}

/** A parsed expression. Evaluation never throws; type errors yield [Value.Missing] or `false`. */
sealed interface Expr {
    fun evaluate(scope: Scope): Value
    fun placeholders(): Set<PlaceholderRef>

    data class Literal(val value: Value) : Expr {
        override fun evaluate(scope: Scope) = value
        override fun placeholders() = emptySet<PlaceholderRef>()
    }

    data class Placeholder(val ref: PlaceholderRef) : Expr {
        override fun evaluate(scope: Scope) = scope.resolve(ref)
        override fun placeholders() = setOf(ref)
    }

    data class Not(val operand: Expr) : Expr {
        override fun evaluate(scope: Scope): Value = when (val value = operand.evaluate(scope)) {
            is Value.Bool -> Value.Bool(!value.value)
            Value.Missing -> Value.Bool(false)
            else -> Value.Bool(false).also { scope.warn(this, "'!' needs a boolean") }
        }
        override fun placeholders() = operand.placeholders()
    }

    data class Negate(val operand: Expr) : Expr {
        override fun evaluate(scope: Scope): Value =
            operand.evaluate(scope).number()?.let { Value.Num(-it) } ?: Value.Missing
        override fun placeholders() = operand.placeholders()
    }

    data class Binary(val op: BinaryOp, val left: Expr, val right: Expr) : Expr {
        override fun evaluate(scope: Scope): Value = when (op) {
            BinaryOp.AND -> Value.Bool(left.isTrue(scope) && right.isTrue(scope))
            BinaryOp.OR -> Value.Bool(left.isTrue(scope) || right.isTrue(scope))
            else -> op.apply(this, left.evaluate(scope), right.evaluate(scope), scope)
        }
        override fun placeholders() = left.placeholders() + right.placeholders()
    }

    data class Call(val function: ExprFunction, val arguments: List<Expr>) : Expr {
        override fun evaluate(scope: Scope): Value {
            val numbers = arguments.map { it.evaluate(scope).number() ?: return Value.Missing }
            return Value.Num(function.apply(numbers))
        }
        override fun placeholders() = arguments.flatMapTo(HashSet()) { it.placeholders() }
    }
}

enum class BinaryOp(val symbol: String) {
    PLUS("+"), MINUS("-"), TIMES("*"), DIV("/"),
    EQ("=="), NE("!="), LT("<"), LE("<="), GT(">"), GE(">="),
    AND("&&"), OR("||");

    fun apply(expression: Expr, left: Value, right: Value, scope: Scope): Value = when (this) {
        PLUS, MINUS, TIMES, DIV -> arithmetic(expression, left, right, scope)
        EQ, NE -> equality(left, right)
        LT, LE, GT, GE -> comparison(expression, left, right, scope)
        AND, OR -> error("handled by Expr.Binary")
    }

    private fun arithmetic(expression: Expr, left: Value, right: Value, scope: Scope): Value {
        if (left == Value.Missing || right == Value.Missing) return Value.Missing
        if (this == PLUS && (left.isTextual() || right.isTextual())) return Value.Str(left.text() + right.text())
        val a = left.number()
        val b = right.number()
        if (a == null || b == null) {
            scope.warn(expression, "'$symbol' needs numbers")
            return Value.Missing
        }
        return Value.Num(
            when (this) {
                PLUS -> a + b
                MINUS -> a - b
                TIMES -> a * b
                else -> if (b == 0.0) 0.0 else a / b
            }
        )
    }

    private fun equality(left: Value, right: Value): Value {
        if (left == Value.Missing || right == Value.Missing) return Value.Bool(false)
        val a = left.number()
        val b = right.number()
        val equal = if (a != null && b != null) a == b else left.text() == right.text()
        return Value.Bool(if (this == EQ) equal else !equal)
    }

    private fun comparison(expression: Expr, left: Value, right: Value, scope: Scope): Value {
        if (left == Value.Missing || right == Value.Missing) return Value.Bool(false)
        val a = left.number()
        val b = right.number()
        if (a == null || b == null) {
            scope.warn(expression, "'$symbol' needs numbers")
            return Value.Bool(false)
        }
        return Value.Bool(
            when (this) {
                LT -> a < b
                LE -> a <= b
                GT -> a > b
                else -> a >= b
            }
        )
    }
}

/** Functions callable in expressions; [arity] is the allowed argument count. */
enum class ExprFunction(val arity: IntRange, val apply: (List<Double>) -> Double) {
    MIN(1..Int.MAX_VALUE, { it.min() }),
    MAX(1..Int.MAX_VALUE, { it.max() }),
    ROUND(1..1, { Math.round(it[0]).toDouble() }),
    FLOOR(1..1, { kotlin.math.floor(it[0]) }),
    CEIL(1..1, { kotlin.math.ceil(it[0]) }),
    CLAMP(3..3, { it[0].coerceIn(it[1], maxOf(it[1], it[2])) });

    companion object {
        fun byName(name: String): ExprFunction? = entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

/** `true` only for a boolean `true`; anything else (including [Value.Missing]) is `false`. */
fun Expr.isTrue(scope: Scope): Boolean = when (val value = evaluate(scope)) {
    is Value.Bool -> value.value
    Value.Missing -> false
    else -> false.also { scope.warn(this, "condition is not a boolean") }
}

/** The numeric result, or `null` when the result is not a number. */
fun Expr.number(scope: Scope): Double? = evaluate(scope).number()
