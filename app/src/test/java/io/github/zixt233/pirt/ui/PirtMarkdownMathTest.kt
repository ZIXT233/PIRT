package io.github.zixt233.pirt.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PirtMarkdownMathTest {
    @Test
    fun splitsInlineAndDisplayMath() {
        val spans = splitMarkdownMath("energy ${'$'}E = mc^2${'$'} then ${'$'}${'$'}\\int_0^1 x dx${'$'}${'$'} done")

        assertEquals(listOf(false, true, false, true, false), spans.map(MathSpan::formula))
        assertEquals("E = mc^2", spans[1].text)
        assertFalse(spans[1].display)
        assertEquals("\\int_0^1 x dx", spans[3].text)
        assertTrue(spans[3].display)
    }

    @Test
    fun keepsEscapedAndUnclosedDollarsAsText() {
        val spans = splitMarkdownMath("price \\${'$'}5 and unfinished ${'$'}x")

        assertEquals(1, spans.size)
        assertFalse(spans.single().formula)
    }
}
