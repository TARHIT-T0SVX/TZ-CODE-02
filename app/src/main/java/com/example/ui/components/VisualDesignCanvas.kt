package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DOMElementNode
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun VisualDesignCanvas(
    elements: List<DOMElementNode>,
    selectedElementId: String?,
    hasUnsavedChanges: Boolean,
    onSelectElement: (String?) -> Unit,
    onUpdateElement: (DOMElementNode) -> Unit,
    onAddElement: (DOMElementNode) -> Unit,
    onDeleteElement: (String) -> Unit,
    onCommitSaveToCode: () -> Unit,
    onDiscardChanges: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isLayersPanelOpen by remember { mutableStateOf(false) }
    var activeInspectorTab by remember { mutableIntStateOf(0) } // 0: Text/Content, 1: Transform & Size, 2: Style & Colors, 3: Layout & Radius
    var isAspectRatioLocked by remember { mutableStateOf(false) }
    var colorPickerTarget by remember { mutableStateOf<String?>(null) } // "background" or "color" or null

    val selectedElement = remember(elements, selectedElementId) {
        elements.firstOrNull { it.id == selectedElementId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TZeronBgDark)
            .testTag("visual_design_canvas")
    ) {
        // Visual Canvas Header Toolbar (Standardized tab & action styling)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(TZeronSurface)
                .border(0.5.dp, TZeronBorder)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Layer Panel Toggle Button
                Surface(
                    modifier = Modifier
                        .clickable { isLayersPanelOpen = !isLayersPanelOpen }
                        .testTag("toggle_layer_tree_btn"),
                    color = if (isLayersPanelOpen) TZeronSurfaceElevated else TZeronSurfaceCard,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (isLayersPanelOpen) TZeronAccentBlue else TZeronBorderSubtle
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = TZeronIcons.Layers,
                            contentDescription = "Layers",
                            tint = if (isLayersPanelOpen) TZeronAccentBlue else TZeronTextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "LAYERS (${elements.size})",
                            color = if (isLayersPanelOpen) TZeronAccentBlue else TZeronTextSecondary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Quick Insert: Container Card Box
                Surface(
                    modifier = Modifier
                        .clickable {
                            val nextOffset = (elements.size * 16f) % 120f
                            val newElem = DOMElementNode(
                                id = "box_${System.currentTimeMillis()}",
                                tagName = "div",
                                textContent = "Card Box",
                                x = 30f + nextOffset,
                                y = 30f + nextOffset,
                                width = 180f,
                                height = 90f,
                                borderRadius = 14f,
                                styles = mapOf(
                                    "background" to "#1E1D22",
                                    "border" to "1px solid #35343F",
                                    "color" to "#ECECED",
                                    "font-size" to "14px"
                                )
                            )
                            onAddElement(newElem)
                        }
                        .testTag("insert_box_btn"),
                    color = TZeronSurfaceCard,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = TZeronIcons.Shapes,
                            contentDescription = "Insert Box",
                            tint = TZeronTextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ BOX", color = TZeronTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }

                // Quick Insert: Text Heading
                Surface(
                    modifier = Modifier
                        .clickable {
                            val nextOffset = (elements.size * 16f) % 120f
                            val newElem = DOMElementNode(
                                id = "text_${System.currentTimeMillis()}",
                                tagName = "h2",
                                textContent = "Live Heading",
                                x = 24f + nextOffset,
                                y = 24f + nextOffset,
                                width = 220f,
                                height = 44f,
                                borderRadius = 6f,
                                styles = mapOf(
                                    "color" to "#FFFFFF",
                                    "font-size" to "22px",
                                    "font-weight" to "bold"
                                )
                            )
                            onAddElement(newElem)
                        }
                        .testTag("insert_text_btn"),
                    color = TZeronSurfaceCard,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = TZeronIcons.Typography,
                            contentDescription = "Insert Text",
                            tint = TZeronTextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+ TEXT", color = TZeronTextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            // Sync Actions: Commit Save to Code / Discard
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (hasUnsavedChanges) {
                    TextButton(
                        onClick = onDiscardChanges,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("discard_visual_changes_btn")
                    ) {
                        Text("DISCARD", color = TZeronTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }

                    Button(
                        onClick = onCommitSaveToCode,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TZeronAccentBlue),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("commit_save_to_code_btn")
                    ) {
                        Icon(imageVector = TZeronIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("SAVE TO CODE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                } else {
                    Text(
                        text = "SYNCHRONIZED",
                        color = TZeronTextSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }

        // Main Stage Area & Layer Tree Side Panel
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxSize()) {
                // 1. Interactive Drag/Drop & Exact-Mirroring Canvas
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(TZeronBgDark)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                onSelectElement(null)
                            }
                        }
                ) {
                    // Subtle Grid Background Lines
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val step = 32.dp.toPx()
                        for (x in 0..(size.width / step).toInt()) {
                            drawLine(
                                color = Color(0x0AFFFFFF),
                                start = Offset(x * step, 0f),
                                end = Offset(x * step, size.height),
                                strokeWidth = 0.5f
                            )
                        }
                        for (y in 0..(size.height / step).toInt()) {
                            drawLine(
                                color = Color(0x0AFFFFFF),
                                start = Offset(0f, y * step),
                                end = Offset(size.width, y * step),
                                strokeWidth = 0.5f
                            )
                        }
                    }

                    // Render Elements with Exact CSS Mirroring
                    elements.forEach { elem ->
                        if (!elem.isVisible) return@forEach
                        val isSelected = elem.id == selectedElementId

                        // Exact CSS styling extraction
                        val bgStyle = elem.styles["background"] ?: elem.styles["background-color"] ?: "#1E1D22"
                        val elemBg = parseExactColor(bgStyle)
                        val textColor = parseExactColor(elem.styles["color"] ?: "#ECECED")
                        val fontSize = (elem.styles["font-size"]?.replace("px", "")?.toFloatOrNull() ?: 14f).sp
                        val isBold = elem.styles["font-weight"] == "bold" || elem.styles["font-weight"] == "700" || elem.styles["font-weight"] == "800"
                        val isItalic = elem.styles["font-style"] == "italic"
                        val elemRadius = elem.borderRadius.dp

                        val textAlign = when (elem.styles["text-align"]) {
                            "center" -> TextAlign.Center
                            "right" -> TextAlign.Right
                            else -> TextAlign.Left
                        }

                        val fontFam = when (elem.fontFamily) {
                            "monospace" -> FontFamily.Monospace
                            "serif" -> FontFamily.Serif
                            else -> FontFamily.SansSerif
                        }

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(elem.x.roundToInt(), elem.y.roundToInt()) }
                                .size(elem.width.dp, elem.height.dp)
                                .rotate(elem.rotation)
                                .alpha(elem.opacity)
                                .pointerInput(elem.id) {
                                    detectDragGestures(
                                        onDragStart = { onSelectElement(elem.id) }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        val updated = elem.copy(
                                            x = maxOf(0f, elem.x + dragAmount.x),
                                            y = maxOf(0f, elem.y + dragAmount.y)
                                        )
                                        onUpdateElement(updated)
                                    }
                                }
                                .pointerInput(elem.id) {
                                    detectTapGestures {
                                        onSelectElement(elem.id)
                                    }
                                }
                                .clip(RoundedCornerShape(elemRadius))
                                .background(elemBg)
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) TZeronAccentBlue else TZeronBorder,
                                    shape = RoundedCornerShape(elemRadius)
                                )
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("canvas_elem_${elem.id}"),
                            contentAlignment = when (textAlign) {
                                TextAlign.Center -> Alignment.Center
                                TextAlign.Right -> Alignment.CenterEnd
                                else -> Alignment.CenterStart
                            }
                        ) {
                            Text(
                                text = elem.textContent.ifEmpty { "<${elem.tagName}>" },
                                color = textColor,
                                fontSize = fontSize,
                                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                                fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal,
                                fontFamily = fontFam,
                                textAlign = textAlign
                            )

                            // Interactive Resize & Rotate Handle when Selected
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .size(16.dp)
                                        .background(TZeronAccentBlue, shape = RoundedCornerShape(3.dp))
                                        .border(1.dp, Color.White, shape = RoundedCornerShape(3.dp))
                                        .pointerInput(elem.id) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val newW = maxOf(50f, elem.width + dragAmount.x / 1.5f)
                                                val newH = if (isAspectRatioLocked) {
                                                    newW * (elem.height / elem.width)
                                                } else {
                                                    maxOf(24f, elem.height + dragAmount.y / 1.5f)
                                                }
                                                val updated = elem.copy(width = newW, height = newH)
                                                onUpdateElement(updated)
                                            }
                                        }
                                        .testTag("resize_handle_${elem.id}")
                                )
                            }
                        }
                    }
                }

                // 2. Layer Tree Inspector Drawer Panel
                AnimatedVisibility(
                    visible = isLayersPanelOpen,
                    enter = slideInHorizontally { it } + fadeIn(),
                    exit = slideOutHorizontally { it } + fadeOut()
                ) {
                    Surface(
                        modifier = Modifier
                            .width(240.dp)
                            .fillMaxHeight()
                            .testTag("photoshop_layer_panel"),
                        color = TZeronSurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, TZeronBorder)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(TZeronSurfaceElevated)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "DOM LAYERS",
                                    color = TZeronTextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${elements.size} items",
                                    color = TZeronTextMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                items(elements, key = { it.id }) { elem ->
                                    val isElemSelected = elem.id == selectedElementId
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isElemSelected) TZeronSurfaceElevated else Color.Transparent)
                                            .clickable { onSelectElement(elem.id) }
                                            .border(
                                                width = if (isElemSelected) 1.dp else 0.dp,
                                                color = if (isElemSelected) TZeronAccentBlue else Color.Transparent
                                            )
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (elem.tagName.startsWith("h") || elem.tagName == "p" || elem.tagName == "span")
                                                    TZeronIcons.Typography else TZeronIcons.Shapes,
                                                contentDescription = null,
                                                tint = if (isElemSelected) TZeronAccentBlue else TZeronTextSecondary,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Column {
                                                Text(
                                                    text = "<${elem.tagName}>",
                                                    color = if (isElemSelected) TZeronAccentBlue else TZeronTextMuted,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = elem.textContent.take(16).ifEmpty { "Element" },
                                                    color = if (isElemSelected) TZeronTextPrimary else TZeronTextSecondary,
                                                    fontSize = 11.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    onUpdateElement(elem.copy(isVisible = !elem.isVisible))
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (elem.isVisible) TZeronIcons.Eye else TZeronIcons.Offline,
                                                    contentDescription = "Toggle Visibility",
                                                    tint = if (elem.isVisible) TZeronTextSecondary else TZeronTextMuted,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }

                                            IconButton(
                                                onClick = { onDeleteElement(elem.id) },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = TZeronIcons.Delete,
                                                    contentDescription = "Delete Layer",
                                                    tint = TZeronError,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Consolidated Bottom Property & Text Inspector Sheet
        if (selectedElement != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .testTag("element_property_inspector"),
                color = TZeronSurface.copy(alpha = 0.98f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                shadowElevation = 10.dp,
                border = androidx.compose.foundation.BorderStroke(1.dp, TZeronBorder)
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header & Tool tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "EDIT: <${selectedElement.tagName.uppercase()}>",
                            color = TZeronTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            InspectorTabButton("TEXT", activeInspectorTab == 0) { activeInspectorTab = 0 }
                            InspectorTabButton("TRANSFORM", activeInspectorTab == 1) { activeInspectorTab = 1 }
                            InspectorTabButton("COLOR", activeInspectorTab == 2) { activeInspectorTab = 2 }
                            InspectorTabButton("LAYOUT", activeInspectorTab == 3) { activeInspectorTab = 3 }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    when (activeInspectorTab) {
                        // 0. Enhanced Text Editing & Formatting Pop-up
                        0 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Text Input Box
                                OutlinedTextField(
                                    value = selectedElement.textContent,
                                    onValueChange = { newText ->
                                        onUpdateElement(selectedElement.copy(textContent = newText))
                                    },
                                    label = { Text("Text Content", fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                                    singleLine = false,
                                    maxLines = 2,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = TZeronAccentBlue,
                                        unfocusedBorderColor = TZeronBorder,
                                        focusedTextColor = TZeronTextPrimary,
                                        unfocusedTextColor = TZeronTextPrimary,
                                        focusedContainerColor = TZeronSurfaceElevated,
                                        unfocusedContainerColor = TZeronSurfaceCard
                                    ),
                                    modifier = Modifier.fillMaxWidth().height(52.dp)
                                )

                                // Formatting Row: Bold, Italic, Uppercase, Text Alignment, Text Color Trigger
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Formatting Toggles
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val isBold = selectedElement.styles["font-weight"] == "bold" || selectedElement.styles["font-weight"] == "700"
                                        InspectorToggleButton(label = "B", isSelected = isBold) {
                                            val newStyles = selectedElement.styles.toMutableMap()
                                            newStyles["font-weight"] = if (isBold) "normal" else "bold"
                                            onUpdateElement(selectedElement.copy(styles = newStyles))
                                        }

                                        val isItalic = selectedElement.styles["font-style"] == "italic"
                                        InspectorToggleButton(label = "I", isSelected = isItalic) {
                                            val newStyles = selectedElement.styles.toMutableMap()
                                            newStyles["font-style"] = if (isItalic) "normal" else "italic"
                                            onUpdateElement(selectedElement.copy(styles = newStyles))
                                        }

                                        InspectorToggleButton(
                                            label = "TT",
                                            isSelected = selectedElement.textContent.isNotEmpty() && selectedElement.textContent == selectedElement.textContent.uppercase()
                                        ) {
                                            val updated = if (selectedElement.textContent == selectedElement.textContent.uppercase()) {
                                                selectedElement.textContent.lowercase().replaceFirstChar { it.uppercase() }
                                            } else {
                                                selectedElement.textContent.uppercase()
                                            }
                                            onUpdateElement(selectedElement.copy(textContent = updated))
                                        }
                                    }

                                    // Text Alignments
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        val currentAlign = selectedElement.styles["text-align"] ?: "left"
                                        InspectorToggleButton(label = "L", isSelected = currentAlign == "left") {
                                            val newStyles = selectedElement.styles.toMutableMap()
                                            newStyles["text-align"] = "left"
                                            onUpdateElement(selectedElement.copy(styles = newStyles))
                                        }
                                        InspectorToggleButton(label = "C", isSelected = currentAlign == "center") {
                                            val newStyles = selectedElement.styles.toMutableMap()
                                            newStyles["text-align"] = "center"
                                            onUpdateElement(selectedElement.copy(styles = newStyles))
                                        }
                                        InspectorToggleButton(label = "R", isSelected = currentAlign == "right") {
                                            val newStyles = selectedElement.styles.toMutableMap()
                                            newStyles["text-align"] = "right"
                                            onUpdateElement(selectedElement.copy(styles = newStyles))
                                        }
                                    }

                                    // Text Color Picker Launch Button
                                    Surface(
                                        modifier = Modifier
                                            .clickable { colorPickerTarget = "color" }
                                            .testTag("text_color_picker_btn"),
                                        color = TZeronSurfaceElevated,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorderSubtle)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(12.dp)
                                                    .clip(CircleShape)
                                                    .background(parseExactColor(selectedElement.styles["color"] ?: "#ECECED"))
                                                    .border(0.5.dp, Color.White, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("TEXT COLOR", color = TZeronTextPrimary, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        }
                                    }
                                }

                                // Font Size Slider (8px..64px)
                                val currentFontSize = (selectedElement.styles["font-size"]?.replace("px", "")?.toFloatOrNull() ?: 14f)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("SIZE:", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Slider(
                                        value = currentFontSize,
                                        onValueChange = { sizeVal ->
                                            val newStyles = selectedElement.styles.toMutableMap()
                                            newStyles["font-size"] = "${sizeVal.roundToInt()}px"
                                            onUpdateElement(selectedElement.copy(styles = newStyles))
                                        },
                                        valueRange = 8f..64f,
                                        modifier = Modifier.weight(1f).height(24.dp)
                                    )
                                    Text("${currentFontSize.roundToInt()}px", color = TZeronTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        // 1. Transform & Precision Movement (Sliders for X, Y, W, H, Rotation)
                        1 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                // X Position Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("POS X:", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Slider(
                                        value = selectedElement.x,
                                        onValueChange = { onUpdateElement(selectedElement.copy(x = it)) },
                                        valueRange = 0f..400f,
                                        modifier = Modifier.weight(1f).height(20.dp)
                                    )
                                    Text("${selectedElement.x.roundToInt()}px", color = TZeronTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }

                                // Y Position Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("POS Y:", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Slider(
                                        value = selectedElement.y,
                                        onValueChange = { onUpdateElement(selectedElement.copy(y = it)) },
                                        valueRange = 0f..600f,
                                        modifier = Modifier.weight(1f).height(20.dp)
                                    )
                                    Text("${selectedElement.y.roundToInt()}px", color = TZeronTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }

                                // Width & Height Sliders
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("WIDTH:", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Slider(
                                        value = selectedElement.width,
                                        onValueChange = { w ->
                                            val h = if (isAspectRatioLocked) w * (selectedElement.height / selectedElement.width) else selectedElement.height
                                            onUpdateElement(selectedElement.copy(width = w, height = h))
                                        },
                                        valueRange = 40f..380f,
                                        modifier = Modifier.weight(1f).height(20.dp)
                                    )
                                    Text("${selectedElement.width.roundToInt()}px", color = TZeronTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }

                                // Rotation Slider (-180°..180°)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("ROTATE:", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Slider(
                                        value = selectedElement.rotation,
                                        onValueChange = { onUpdateElement(selectedElement.copy(rotation = it)) },
                                        valueRange = -180f..180f,
                                        modifier = Modifier.weight(1f).height(20.dp)
                                    )
                                    Text("${selectedElement.rotation.roundToInt()}°", color = TZeronTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }

                                // Aspect Ratio Lock & Snap Presets
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        InspectorToggleButton("0°", selectedElement.rotation == 0f) {
                                            onUpdateElement(selectedElement.copy(rotation = 0f))
                                        }
                                        InspectorToggleButton("45°", selectedElement.rotation == 45f) {
                                            onUpdateElement(selectedElement.copy(rotation = 45f))
                                        }
                                        InspectorToggleButton("90°", selectedElement.rotation == 90f) {
                                            onUpdateElement(selectedElement.copy(rotation = 90f))
                                        }
                                    }

                                    InspectorToggleButton(
                                        label = if (isAspectRatioLocked) "🔒 LOCKED 1:1" else "🔓 FREE RESIZE",
                                        isSelected = isAspectRatioLocked
                                    ) {
                                        isAspectRatioLocked = !isAspectRatioLocked
                                    }
                                }
                            }
                        }

                        // 2. Comprehensive Style & Advanced Color Controls
                        2 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Background Color Launch Button
                                    Button(
                                        onClick = { colorPickerTarget = "background" },
                                        colors = ButtonDefaults.buttonColors(containerColor = TZeronSurfaceElevated),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(parseExactColor(selectedElement.styles["background"] ?: "#1E1D22"))
                                                .border(1.dp, Color.White, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("BG COLOR PICKER", color = TZeronTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    }

                                    // Text Color Launch Button
                                    Button(
                                        onClick = { colorPickerTarget = "color" },
                                        colors = ButtonDefaults.buttonColors(containerColor = TZeronSurfaceElevated),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(14.dp)
                                                .clip(CircleShape)
                                                .background(parseExactColor(selectedElement.styles["color"] ?: "#ECECED"))
                                                .border(1.dp, Color.White, CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("TEXT COLOR PICKER", color = TZeronTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                    }
                                }

                                // Opacity Slider (10%..100%)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("OPACITY:", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Slider(
                                        value = selectedElement.opacity,
                                        onValueChange = { onUpdateElement(selectedElement.copy(opacity = it)) },
                                        valueRange = 0.05f..1.0f,
                                        modifier = Modifier.weight(1f).height(20.dp)
                                    )
                                    Text("${(selectedElement.opacity * 100).roundToInt()}%", color = TZeronTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }

                        // 3. Layout, Radius & Quick Actions
                        3 -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // Border Radius Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("RADIUS:", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    Slider(
                                        value = selectedElement.borderRadius,
                                        onValueChange = { onUpdateElement(selectedElement.copy(borderRadius = it)) },
                                        valueRange = 0f..40f,
                                        modifier = Modifier.weight(1f).height(20.dp)
                                    )
                                    Text("${selectedElement.borderRadius.roundToInt()}px", color = TZeronTextPrimary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                                }

                                // Quick Align & Delete
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        InspectorToggleButton("CENTER X", false) {
                                            onUpdateElement(selectedElement.copy(x = 60f))
                                        }
                                        InspectorToggleButton("CENTER Y", false) {
                                            onUpdateElement(selectedElement.copy(y = 120f))
                                        }
                                    }

                                    Button(
                                        onClick = { onDeleteElement(selectedElement.id) },
                                        colors = ButtonDefaults.buttonColors(containerColor = TZeronSurfaceCard),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Icon(imageVector = TZeronIcons.Delete, contentDescription = null, tint = TZeronError, modifier = Modifier.size(12.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("DELETE", color = TZeronError, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Advanced Pop-up Color Picker Modal
    if (colorPickerTarget != null && selectedElement != null) {
        val isBg = colorPickerTarget == "background"
        val initialColor = if (isBg) {
            selectedElement.styles["background"] ?: selectedElement.styles["background-color"] ?: "#1E1D22"
        } else {
            selectedElement.styles["color"] ?: "#ECECED"
        }

        AdvancedColorPickerModal(
            initialHex = initialColor,
            title = if (isBg) "ELEMENT BACKGROUND COLOR" else "ELEMENT TEXT COLOR",
            onDismiss = { colorPickerTarget = null },
            onColorSelected = { selectedHex ->
                val newStyles = selectedElement.styles.toMutableMap()
                if (isBg) {
                    newStyles["background"] = selectedHex
                } else {
                    newStyles["color"] = selectedHex
                }
                onUpdateElement(selectedElement.copy(styles = newStyles))
            }
        )
    }
}

@Composable
private fun InspectorTabButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (isSelected) TZeronSurfaceElevated else TZeronSurfaceCard,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isSelected) TZeronAccentBlue else TZeronBorderSubtle
        )
    ) {
        Text(
            text = label,
            color = if (isSelected) TZeronAccentBlue else TZeronTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun InspectorToggleButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (isSelected) TZeronSurfaceElevated else TZeronSurfaceCard,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isSelected) TZeronAccentBlue else TZeronBorderSubtle
        )
    ) {
        Text(
            text = label,
            color = if (isSelected) TZeronAccentBlue else TZeronTextSecondary,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
        )
    }
}

private fun parseExactColor(hex: String): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        when (clean.length) {
            6 -> Color(android.graphics.Color.parseColor("#$clean"))
            8 -> Color(android.graphics.Color.parseColor("#$clean"))
            3 -> {
                val r = clean[0].toString().repeat(2)
                val g = clean[1].toString().repeat(2)
                val b = clean[2].toString().repeat(2)
                Color(android.graphics.Color.parseColor("#$r$g$b"))
            }
            else -> Color(0xFF1E1D22)
        }
    } catch (_: Exception) {
        Color(0xFF1E1D22)
    }
}
