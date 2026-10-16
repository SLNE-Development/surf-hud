package dev.slne.surf.hud.core.expression

import dev.slne.surf.hud.core.expression.TextTemplate.Segment
import kotlin.test.*

class TextTemplateTest {
    @Test fun `splits literals placeholders and expressions`() {
        val template = TextTemplate.parse("<gold>[money]</gold> {[health] / 2 | 0.0}%")
        val segments = template.segments
        assertEquals(Segment.Literal("<gold>"), segments[0])
        assertEquals(Segment.Placeholder(PlaceholderRef("money")), segments[1])
        assertEquals(Segment.Literal("</gold> "), segments[2])
        val expression = segments[3] as Segment.Expression
        assertEquals("0.0", expression.format)
        assertEquals(Segment.Literal("%"), segments[4])
    }
    @Test fun `double bar is an operator not a format`() {
        val expression = TextTemplate.parse("{true || false}").segments.single() as Segment.Expression
        assertNull(expression.format)
    }
    @Test fun `escaped brackets stay literal`() {
        assertEquals(listOf(Segment.Literal("[x] {y}")), TextTemplate.parse("\\[x] \\{y}").segments)
    }
    @Test fun `bracket that is not a placeholder stays literal`() {
        assertEquals(listOf(Segment.Literal("a [b c")), TextTemplate.parse("a [b c").segments)
    }
    @Test fun `unclosed expression is an error`() {
        assertFailsWith<ExpressionException> { TextTemplate.parse("{1 + 2") }
    }
    @Test fun `invalid format is an error`() {
        assertFailsWith<ExpressionException> { TextTemplate.parse("{1 | 0.0.0'}") }
    }
    @Test fun `collects placeholders from expressions too`() {
        assertEquals(
            setOf(PlaceholderRef("a"), PlaceholderRef("b")),
            TextTemplate.parse("[a] {[b] + 1}").placeholders(),
        )
    }
}
