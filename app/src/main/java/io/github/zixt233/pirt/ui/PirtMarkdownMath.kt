package io.github.zixt233.pirt.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.zixt233.pirt.i18n.LocalAppLanguage
import io.github.zixt233.pirt.i18n.text
import com.mikepenz.markdown.compose.components.CurrentComponentsBridge
import com.mikepenz.markdown.compose.components.MarkdownComponents
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import io.ratex.compose.RaTeX
import org.intellij.markdown.MarkdownElementTypes.HTML_BLOCK

internal data class MathSpan(val text: String, val formula: Boolean, val display: Boolean = false)

internal fun splitMarkdownMath(value: String): List<MathSpan> {
    val result = mutableListOf<MathSpan>()
    var plainStart = 0
    var index = 0
    while (index < value.length) {
        if (value[index] != '$' || (index > 0 && value[index - 1] == '\\')) {
            index++
            continue
        }
        val display = index + 1 < value.length && value[index + 1] == '$'
        val delimiterLength = if (display) 2 else 1
        val end = findClosingMathDelimiter(value, index + delimiterLength, delimiterLength)
        if (end < 0) {
            index += delimiterLength
            continue
        }
        if (index > plainStart) result += MathSpan(value.substring(plainStart, index), formula = false)
        val formula = value.substring(index + delimiterLength, end).trim()
        if (formula.isNotEmpty()) result += MathSpan(formula, formula = true, display = display)
        index = end + delimiterLength
        plainStart = index
    }
    if (plainStart < value.length) result += MathSpan(value.substring(plainStart), formula = false)
    return result.ifEmpty { listOf(MathSpan(value, formula = false)) }
}

private fun findClosingMathDelimiter(value: String, start: Int, length: Int): Int {
    var index = start
    while (index <= value.length - length) {
        if (value[index] == '$' && (index == 0 || value[index - 1] != '\\')) {
            if (length == 1 && (index + 1 >= value.length || value[index + 1] != '$')) return index
            if (length == 2 && index + 1 < value.length && value[index + 1] == '$') return index
        }
        index++
    }
    return -1
}

@Composable
internal fun rememberPirtMarkdownComponents(): MarkdownComponents = remember {
    markdownComponents(
        paragraph = { model ->
            val source = model.content.substring(model.node.startOffset, model.node.endOffset)
            val spans = splitMarkdownMath(source)
            if (spans.none(MathSpan::formula)) {
                CurrentComponentsBridge.paragraph(model)
            } else if (spans.any { it.formula && it.display }) {
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    spans.forEach { span ->
                        if (span.formula) {
                            RaTeX(
                                latex = span.text,
                                fontSize = if (span.display) 22.sp else 18.sp,
                                displayMode = span.display,
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                            )
                        } else if (span.text.isNotBlank()) {
                            Text(span.text.trim(), style = model.typography.paragraph)
                        }
                    }
                }
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    spans.forEach { span ->
                        if (span.formula) {
                            RaTeX(latex = span.text, fontSize = 17.sp, displayMode = false)
                        } else if (span.text.isNotEmpty()) {
                            Text(span.text, style = model.typography.paragraph, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        },
        table = { model ->
            MarkdownTable(
                content = model.content,
                node = model.node,
                style = model.typography.table,
                headerBlock = { content, header, width, style ->
                    MarkdownTableHeader(
                        content = content,
                        header = header,
                        tableWidth = width,
                        style = style,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                    )
                },
                rowBlock = { content, row, width, style ->
                    MarkdownTableRow(
                        content = content,
                        header = row,
                        tableWidth = width,
                        style = style,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                    )
                },
            )
        },
        custom = { type, _ ->
            if (type == HTML_BLOCK) {
                Text(
                    text = LocalAppLanguage.current.text("暂不支持 HTML 混排", "Mixed HTML content is not supported"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
}
