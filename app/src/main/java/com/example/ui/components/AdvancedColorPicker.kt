package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun AdvancedColorPickerModal(
    initialHex: String,
    title: String = "COLOR PICKER",
    onDismiss: () -> Unit,
    onColorSelected: (String) -> Unit
) {
    var hsv by remember(initialHex) {
        val argb = try {
            val clean = initialHex.trim().removePrefix("#")
            when (clean.length) {
                6 -> android.graphics.Color.parseColor("#$clean")
                8 -> android.graphics.Color.parseColor("#$clean")
                3 -> {
                    val r = clean[0].toString().repeat(2)
                    val g = clean[1].toString().repeat(2)
                    val b = clean[2].toString().repeat(2)
                    android.graphics.Color.parseColor("#$r$g$b")
                }
                else -> android.graphics.Color.parseColor("#007ACC")
            }
        } catch (_: Exception) {
            android.graphics.Color.parseColor("#007ACC")
        }

        val hsvArray = FloatArray(3)
        android.graphics.Color.colorToHSV(argb, hsvArray)
        val alpha = android.graphics.Color.alpha(argb) / 255f
        mutableStateOf(HSVColor(hsvArray[0], hsvArray[1], hsvArray[2], alpha))
    }

    var hexInput by remember(hsv) {
        mutableStateOf(hsvToHex(hsv))
    }

    var isEyedropperActive by remember { mutableStateOf(false) }

    val presetSwatches = remember {
        listOf(
            "#007ACC", "#3B82F6", "#60A5FA", "#06B6D4", "#10B981", "#22C55E",
            "#F59E0B", "#F97316", "#EF4444", "#EC4899", "#8B5CF6", "#A855F7",
            "#0D0D0F", "#141317", "#1E1D22", "#26252B", "#35343F", "#4E4D5A",
            "#9D9CA7", "#ECECED", "#FFFFFF", "#FF3366", "#00FFCC", "#FFE600"
        )
    }

    val currentColor = remember(hsv) {
        val hsvArray = floatArrayOf(hsv.hue, hsv.saturation, hsv.value)
        val argb = android.graphics.Color.HSVToColor((hsv.alpha * 255).roundToInt(), hsvArray)
        Color(argb)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TZeronIcons.Palette,
                        contentDescription = null,
                        tint = TZeronAccentBlue,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        color = TZeronTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Eyedropper tool toggle
                Surface(
                    modifier = Modifier
                        .clickable { isEyedropperActive = !isEyedropperActive }
                        .testTag("eyedropper_tool_btn"),
                    color = if (isEyedropperActive) TZeronAccentBlue else TZeronSurfaceElevated,
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        0.5.dp,
                        if (isEyedropperActive) TZeronAccentBlue else TZeronBorderSubtle
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = TZeronIcons.Inspect,
                            contentDescription = "Eyedropper Sampler",
                            tint = if (isEyedropperActive) Color.White else TZeronTextSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "DROPPER",
                            color = if (isEyedropperActive) Color.White else TZeronTextSecondary,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Color Preview & Hex Display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Preview Tile with Alpha Checkered Look
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, TZeronBorder, RoundedCornerShape(10.dp))
                            .background(currentColor)
                    )

                    // Hex Text Field
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { input ->
                            val clean = input.uppercase().take(9)
                            hexInput = clean
                            try {
                                val parseStr = if (clean.startsWith("#")) clean else "#$clean"
                                val argb = android.graphics.Color.parseColor(parseStr)
                                val hsvArr = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb, hsvArr)
                                val alphaVal = android.graphics.Color.alpha(argb) / 255f
                                hsv = HSVColor(hsvArr[0], hsvArr[1], hsvArr[2], alphaVal)
                            } catch (_: Exception) {}
                        },
                        label = { Text("HEX CODE", fontSize = 9.sp, fontFamily = FontFamily.Monospace) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TZeronAccentBlue,
                            unfocusedBorderColor = TZeronBorder,
                            focusedTextColor = TZeronTextPrimary,
                            unfocusedTextColor = TZeronTextPrimary,
                            focusedContainerColor = TZeronSurfaceElevated,
                            unfocusedContainerColor = TZeronSurfaceCard
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    )
                }

                // 1. Hue Slider (0°..360°)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("HUE", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("${hsv.hue.roundToInt()}°", color = TZeronTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }

                    val hueRainbow = remember {
                        Brush.horizontalGradient(
                            listOf(
                                Color.Red, Color.Yellow, Color.Green,
                                Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(hueRainbow)
                    )

                    Slider(
                        value = hsv.hue,
                        onValueChange = { hsv = hsv.copy(hue = it) },
                        valueRange = 0f..360f,
                        modifier = Modifier.fillMaxWidth().height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        )
                    )
                }

                // 2. Saturation Slider (0%..100%)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("SATURATION", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("${(hsv.saturation * 100).roundToInt()}%", color = TZeronTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }

                    Slider(
                        value = hsv.saturation,
                        onValueChange = { hsv = hsv.copy(saturation = it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = TZeronAccentBlue,
                            activeTrackColor = TZeronAccentBlue,
                            inactiveTrackColor = TZeronSurfaceElevated
                        )
                    )
                }

                // 3. Brightness / Value Slider (0%..100%)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("LUMINANCE / BRIGHTNESS", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("${(hsv.value * 100).roundToInt()}%", color = TZeronTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }

                    Slider(
                        value = hsv.value,
                        onValueChange = { hsv = hsv.copy(value = it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = TZeronAccentBlue,
                            activeTrackColor = TZeronAccentBlue,
                            inactiveTrackColor = TZeronSurfaceElevated
                        )
                    )
                }

                // 4. Opacity / Alpha Slider (0%..100%)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("OPACITY (ALPHA)", color = TZeronTextMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Text("${(hsv.alpha * 100).roundToInt()}%", color = TZeronTextSecondary, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }

                    Slider(
                        value = hsv.alpha,
                        onValueChange = { hsv = hsv.copy(alpha = it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().height(20.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = TZeronAccentBlue,
                            activeTrackColor = TZeronAccentBlue,
                            inactiveTrackColor = TZeronSurfaceElevated
                        )
                    )
                }

                // Preset Swatches Grid
                Text(
                    text = if (isEyedropperActive) "EYEDROPPER PRESETS (TAP TO SAMPLE)" else "PALETTE SWATCHES",
                    color = if (isEyedropperActive) TZeronAccentBlue else TZeronTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    items(presetSwatches) { hex ->
                        val swatchColor = try {
                            Color(android.graphics.Color.parseColor(hex))
                        } catch (_: Exception) {
                            Color.Gray
                        }

                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(swatchColor)
                                .border(
                                    1.dp,
                                    if (hex.equals(hexInput, ignoreCase = true)) Color.White else TZeronBorderSubtle,
                                    CircleShape
                                )
                                .clickable {
                                    try {
                                        val argb = android.graphics.Color.parseColor(hex)
                                        val hsvArr = FloatArray(3)
                                        android.graphics.Color.colorToHSV(argb, hsvArr)
                                        hsv = HSVColor(hsvArr[0], hsvArr[1], hsvArr[2], 1f)
                                        hexInput = hex
                                    } catch (_: Exception) {}
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onColorSelected(hsvToHex(hsv))
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = TZeronAccentBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("APPLY COLOR", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("CANCEL", color = TZeronTextMuted, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = TZeronSurface
    )
}

data class HSVColor(
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val alpha: Float = 1f
)

private fun hsvToHex(hsv: HSVColor): String {
    val hsvArray = floatArrayOf(hsv.hue, hsv.saturation, hsv.value)
    val argb = android.graphics.Color.HSVToColor((hsv.alpha * 255).roundToInt(), hsvArray)
    return if (hsv.alpha < 0.999f) {
        String.format("#%08X", argb)
    } else {
        String.format("#%06X", 0xFFFFFF and argb)
    }
}
