package dev.slne.surf.hud.core.expression

import kotlin.test.*

class ExpressionTest {
    private val values = mapOf(
        PlaceholderRef("health") to Value.Num(15.0),
        PlaceholderRef("max_health") to Value.Num(20.0),
        PlaceholderRef("name") to Value.Str("Notch"),
        PlaceholderRef("papi", "balance") to Value.Str("1500"),
        PlaceholderRef("flying") to Value.Bool(true),
    )
    private val warnings = mutableListOf<String>()
    private val scope = object : Scope {
        override fun resolve(ref: PlaceholderRef) = values[ref] ?: Value.Missing
        override fun warn(expression: Expr, message: String) { warnings += message }
    }

    private fun eval(source: String) = ExpressionParser.parse(source).evaluate(scope)

    @Test fun `arithmetic respects precedence`() = assertEquals(Value.Num(14.0), eval("2 + 3 * 4"))
    @Test fun `parentheses group`() = assertEquals(Value.Num(20.0), eval("(2 + 3) * 4"))
    @Test fun `placeholders resolve`() = assertEquals(Value.Num(0.75), eval("[health] / [max_health]"))
    @Test fun `placeholder arguments parse`() = assertEquals(Value.Num(1501.0), eval("[papi:balance] + 1"))
    @Test fun `numeric strings compare as numbers`() = assertEquals(Value.Bool(true), eval("[papi:balance] > 999"))
    @Test fun `plus concatenates text`() = assertEquals(Value.Str("Hi Notch"), eval("'Hi ' + [name]"))
    @Test fun `comparison and logic`() = assertEquals(Value.Bool(true), eval("[health] < [max_health] && ![flying] || true"))
    @Test fun `functions`() {
        assertEquals(Value.Num(3.0), eval("min(5, 3, 4)"))
        assertEquals(Value.Num(5.0), eval("max(5, 3)"))
        assertEquals(Value.Num(2.0), eval("round(1.6)"))
        assertEquals(Value.Num(1.0), eval("floor(1.6)"))
        assertEquals(Value.Num(2.0), eval("ceil(1.2)"))
        assertEquals(Value.Num(10.0), eval("clamp(42, 0, 10)"))
    }
    @Test fun `division by zero is zero`() = assertEquals(Value.Num(0.0), eval("5 / 0"))
    @Test fun `missing placeholders propagate`() {
        assertEquals(Value.Missing, eval("[unknown] + 1"))
        assertEquals(Value.Bool(false), eval("[unknown] > 1"))
    }
    @Test fun `type mismatch in comparison is false and warns`() {
        assertEquals(Value.Bool(false), eval("[name] > 3"))
        assertEquals(1, warnings.size)
    }
    @Test fun `non boolean condition is false`() {
        assertFalse(ExpressionParser.parse("[health]").isTrue(scope))
    }
    @Test fun `collects placeholders`() {
        assertEquals(
            setOf(PlaceholderRef("health"), PlaceholderRef("papi", "balance")),
            ExpressionParser.parse("max([health], [papi:balance])").placeholders(),
        )
    }
    @Test fun `unknown function is an error`() {
        val error = assertFailsWith<ExpressionException> { ExpressionParser.parse("mx(1, 2)") }
        assertContains(error.message!!, "unknown function 'mx'")
    }
    @Test fun `wrong arity is an error`() {
        assertFailsWith<ExpressionException> { ExpressionParser.parse("clamp(1, 2)") }
    }
    @Test fun `trailing input is an error`() {
        assertFailsWith<ExpressionException> { ExpressionParser.parse("1 2") }
    }
    @Test fun `unclosed placeholder is an error`() {
        assertFailsWith<ExpressionException> { ExpressionParser.parse("[health") }
    }
}
