package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SyntaxTheme
import com.example.ui.theme.*

@Composable
fun CodeEditorView(
    fileContent: String,
    fileName: String,
    extension: String,
    isReadOnly: Boolean,
    onContentChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var textFieldValue by remember(fileName) {
        mutableStateOf(TextFieldValue(fileContent))
    }

    LaunchedEffect(fileName, fileContent) {
        if (textFieldValue.text != fileContent) {
            textFieldValue = textFieldValue.copy(text = fileContent)
        }
    }

    val placeholderText = when (extension.lowercase()) {
        "html", "htm" -> "<!-- HTML structure here -->"
        "css" -> "/* CSS stylesheet rules here */"
        "js", "ts" -> "// JavaScript logic here"
        "json" -> "{\n  \"key\": \"value\"\n}"
        "svg" -> "<svg xmlns=\"http://www.w3.org/2000/svg\" ...></svg>"
        "md" -> "# Markdown notes"
        else -> "Enter code or file content here..."
    }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val lineCount = remember(textFieldValue.text) {
        maxOf(1, textFieldValue.text.count { it == '\n' } + 1)
    }
    val gutterText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    // Custom blue selection color palette
    val customTextSelectionColors = remember {
        TextSelectionColors(
            handleColor = EditorHandleColor,
            backgroundColor = EditorSelectionBg
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TZeronBgDark)
            .testTag("code_editor_container")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll)
        ) {
            // Line Number Gutter with Full-Height baseline
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .defaultMinSize(minHeight = 800.dp)
                    .background(TZeronSurfaceCard)
                    .padding(vertical = 12.dp, horizontal = 6.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                Text(
                    text = gutterText,
                    color = TZeronTextMuted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                )
            }

            // Fixed vertical gutter border line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .defaultMinSize(minHeight = 800.dp)
                    .background(TZeronBorderSubtle)
            )

            // Editor Text Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 800.dp)
                    .horizontalScroll(horizontalScroll)
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                if (textFieldValue.text.isEmpty()) {
                    Text(
                        text = placeholderText,
                        color = TZeronTextMuted.copy(alpha = 0.5f),
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                }

                CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = {
                            if (!isReadOnly) {
                                textFieldValue = it
                                onContentChange(it.text)
                            }
                        },
                        readOnly = isReadOnly,
                        textStyle = TextStyle(
                            color = TZeronTextPrimary,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(if (isReadOnly) Color.Transparent else EditorHandleColor),
                        visualTransformation = SyntaxHighlightVisualTransformation(extension),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("code_text_field")
                    )
                }
            }
        }

        // Read-only indicator banner
        if (isReadOnly) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(TZeronSurface, shape = MaterialTheme.shapes.extraSmall)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TZeronIcons.Lock,
                        contentDescription = "Read-only mode active",
                        tint = TZeronTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "READ-ONLY",
                        color = TZeronTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

class SyntaxHighlightVisualTransformation(
    private val extension: String
) : VisualTransformation {

    // Standard Universal VS Code Default Syntax Highlighting
    private val tagColor = VSCodeTag          // #4EC9B0
    private val attrColor = VSCodeAttr        // #9CDCFE
    private val stringColor = VSCodeString    // #CE9178
    private val keywordColor = VSCodeKeyword  // #C586C0
    private val commentColor = VSCodeComment  // #6A9955
    private val funcColor = VSCodeFunction    // #DCDCAA
    private val numColor = VSCodeNumber       // #B5CEA8

    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        if (raw.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val builder = AnnotatedString.Builder(raw)

        try {
            when (extension.lowercase()) {
                "html", "htm", "svg", "xml" -> highlightHtml(raw, builder)
                "css", "scss", "less" -> highlightCss(raw, builder)
                "js", "ts", "jsx", "tsx", "json" -> highlightJs(raw, builder)
                "md", "markdown" -> highlightMarkdown(raw, builder)
                else -> highlightGeneric(raw, builder)
            }
            return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
        } catch (_: Exception) {
            return TransformedText(text, OffsetMapping.Identity)
        }
    }

    private fun safeAddSpan(builder: AnnotatedString.Builder, style: SpanStyle, start: Int, end: Int, maxLen: Int) {
        val s = start.coerceIn(0, maxLen)
        val e = end.coerceIn(0, maxLen)
        if (s < e) {
            builder.addStyle(style, s, e)
        }
    }

    private fun highlightHtml(text: String, builder: AnnotatedString.Builder) {
        val len = text.length
        Regex("""</?([a-zA-Z0-9\-]+)""").findAll(text).forEach { m ->
            val group = m.groups[1]
            if (group != null) {
                safeAddSpan(builder, SpanStyle(color = tagColor, fontWeight = FontWeight.SemiBold), group.range.first, group.range.last + 1, len)
            }
        }
        Regex("""\s+([a-zA-Z0-9\-]+)(?==)""").findAll(text).forEach { m ->
            val group = m.groups[1]
            if (group != null) {
                safeAddSpan(builder, SpanStyle(color = attrColor), group.range.first, group.range.last + 1, len)
            }
        }
        Regex(""""[^"]*"|'[^']*'""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = stringColor), m.range.first, m.range.last + 1, len)
        }
        Regex("""<!--[\s\S]*?-->""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), m.range.first, m.range.last + 1, len)
        }
    }

    private fun highlightCss(text: String, builder: AnnotatedString.Builder) {
        val len = text.length
        Regex("""(^|\n|\})\s*([^{\n]+)\s*\{""").findAll(text).forEach { m ->
            val group = m.groups[2]
            if (group != null) {
                safeAddSpan(builder, SpanStyle(color = tagColor, fontWeight = FontWeight.Bold), group.range.first, group.range.last + 1, len)
            }
        }
        Regex("""([a-zA-Z\-]+)\s*:""").findAll(text).forEach { m ->
            val group = m.groups[1]
            if (group != null) {
                safeAddSpan(builder, SpanStyle(color = attrColor), group.range.first, group.range.last + 1, len)
            }
        }
        Regex(""":\s*([^;\}]+)""").findAll(text).forEach { m ->
            val group = m.groups[1]
            if (group != null) {
                safeAddSpan(builder, SpanStyle(color = stringColor), group.range.first, group.range.last + 1, len)
            }
        }
        Regex("""\b(\d+(\.\d+)?)(px|rem|em|%|vh|vw|s|ms|deg)?\b""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = numColor), m.range.first, m.range.last + 1, len)
        }
        Regex("""/\*[\s\S]*?\*/""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), m.range.first, m.range.last + 1, len)
        }
    }

    private fun highlightJs(text: String, builder: AnnotatedString.Builder) {
        val len = text.length
        val keywords = setOf(
            "const", "let", "var", "function", "return", "if", "else", "for", "while",
            "switch", "case", "break", "import", "export", "default", "from", "class",
            "async", "await", "try", "catch", "finally", "new", "this", "typeof", "null",
            "undefined", "true", "false", "extends", "super", "yield", "in", "of"
        )
        Regex("""\b([a-zA-Z_$][a-zA-Z0-9_$]*)\b""").findAll(text).forEach { m ->
            if (keywords.contains(m.value)) {
                safeAddSpan(builder, SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1, len)
            }
        }
        Regex("""\b([a-zA-Z0-9_$]+)\s*(?=\()""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = funcColor), m.range.first, m.range.last + 1, len)
        }
        Regex(""""[^"\\]*(?:\\.[^"\\]*)*"|'[^'\\]*(?:\\.[^'\\]*)*'|`[^`\\]*(?:\\.[^`\\]*)*`""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = stringColor), m.range.first, m.range.last + 1, len)
        }
        Regex("""\b(\d+(\.\d+)?)\b""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = numColor), m.range.first, m.range.last + 1, len)
        }
        Regex("""//.*|/\*[\s\S]*?\*/""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), m.range.first, m.range.last + 1, len)
        }
    }

    private fun highlightMarkdown(text: String, builder: AnnotatedString.Builder) {
        val len = text.length
        Regex("""^#{1,6}\s+.*""", RegexOption.MULTILINE).findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = tagColor, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1, len)
        }
        Regex("""\[([^\]]+)\]\(([^)]+)\)""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = funcColor), m.range.first, m.range.last + 1, len)
        }
        Regex("""`([^`]+)`""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = stringColor), m.range.first, m.range.last + 1, len)
        }
    }

    private fun highlightGeneric(text: String, builder: AnnotatedString.Builder) {
        val len = text.length
        Regex(""""[^"]*"|'[^']*'""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = stringColor), m.range.first, m.range.last + 1, len)
        }
        Regex("""\b(\d+)\b""").findAll(text).forEach { m ->
            safeAddSpan(builder, SpanStyle(color = numColor), m.range.first, m.range.last + 1, len)
        }
    }
}
