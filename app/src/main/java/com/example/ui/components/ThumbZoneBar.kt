package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThumbZoneBar(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    isReadOnly: Boolean,
    onToggleReadOnly: () -> Unit,
    onCopyCode: () -> Unit,
    onOneTapPaste: () -> Unit,
    modifier: Modifier = Modifier
) {
    // When keyboard is visible (isImeVisible), position buttons 6dp directly above the keyboard edge.
    // When keyboard is hidden, rest comfortably (20dp bottom padding) above the bottom navigation bar.
    val isImeVisible = WindowInsets.isImeVisible
    val bottomOffset = if (isImeVisible) 6.dp else 20.dp

    Row(
        modifier = modifier
            .fillMaxWidth()
            .imePadding()
            .padding(start = 14.dp, end = 14.dp, bottom = bottomOffset)
            .testTag("thumb_zone_bar"),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Side: Undo and Redo buttons for ergonomic left thumb access
        Surface(
            color = TZeronSurface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, TZeronBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnimatedThumbButton(
                    icon = TZeronIcons.Undo,
                    contentDescription = "Undo",
                    isActive = canUndo,
                    isEnabled = canUndo,
                    activeColor = TZeronSurfaceElevated,
                    inactiveColor = TZeronSurfaceCard,
                    activeTint = TZeronTextPrimary,
                    inactiveTint = TZeronTextMuted.copy(alpha = 0.35f),
                    borderColor = if (canUndo) TZeronBorder else TZeronBorderSubtle,
                    testTag = "undo_button",
                    onClick = onUndo
                )

                AnimatedThumbButton(
                    icon = TZeronIcons.Redo,
                    contentDescription = "Redo",
                    isActive = canRedo,
                    isEnabled = canRedo,
                    activeColor = TZeronSurfaceElevated,
                    inactiveColor = TZeronSurfaceCard,
                    activeTint = TZeronTextPrimary,
                    inactiveTint = TZeronTextMuted.copy(alpha = 0.35f),
                    borderColor = if (canRedo) TZeronBorder else TZeronBorderSubtle,
                    testTag = "redo_button",
                    onClick = onRedo
                )
            }
        }

        // Right Side: EDIT, COPY, PASTE (Swapped: Edit moved to Paste's location on left, Paste moved to Edit's location on right)
        Surface(
            color = TZeronSurface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(26.dp),
            shadowElevation = 8.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, TZeronBorder)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Edit Mode Toggle Button (Positioned at the left spot)
                AnimatedThumbButton(
                    icon = TZeronIcons.Edit,
                    contentDescription = "Edit Mode Toggle",
                    isActive = !isReadOnly,
                    isEnabled = true,
                    activeColor = TZeronSurfaceElevated,
                    inactiveColor = TZeronSurfaceCard,
                    activeTint = TZeronAccentBlue,
                    inactiveTint = TZeronTextMuted,
                    borderColor = if (!isReadOnly) TZeronAccentBlue else TZeronBorderSubtle,
                    testTag = "edit_button",
                    onClick = onToggleReadOnly
                )

                // 2. Copy Code Button (Middle)
                AnimatedThumbButton(
                    icon = TZeronIcons.Copy,
                    contentDescription = "Copy Code to Clipboard",
                    isActive = false,
                    isEnabled = true,
                    activeColor = TZeronSurfaceElevated,
                    inactiveColor = TZeronSurfaceCard,
                    activeTint = TZeronTextPrimary,
                    inactiveTint = TZeronTextSecondary,
                    borderColor = TZeronBorderSubtle,
                    testTag = "copy_code_button",
                    onClick = onCopyCode
                )

                // 3. One-Tap Paste Button (Positioned at the right spot)
                AnimatedThumbButton(
                    icon = TZeronIcons.Paste,
                    contentDescription = "Paste from Clipboard",
                    isActive = !isReadOnly,
                    isEnabled = !isReadOnly,
                    activeColor = TZeronSurfaceElevated,
                    inactiveColor = TZeronSurfaceCard,
                    activeTint = TZeronTextPrimary,
                    inactiveTint = TZeronTextMuted.copy(alpha = 0.35f),
                    borderColor = if (!isReadOnly) TZeronBorder else TZeronBorderSubtle,
                    testTag = "one_tap_paste_button",
                    onClick = onOneTapPaste
                )
            }
        }
    }
}

@Composable
private fun AnimatedThumbButton(
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    isEnabled: Boolean = true,
    activeColor: Color,
    inactiveColor: Color,
    activeTint: Color,
    inactiveTint: Color,
    borderColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && isEnabled) 0.86f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "thumb_btn_scale"
    )

    Surface(
        modifier = Modifier
            .size(42.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled
            ) { onClick() }
            .testTag(testTag),
        color = if (isActive && isEnabled) activeColor else inactiveColor,
        shape = RoundedCornerShape(21.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isEnabled) {
                    if (isActive) activeTint else inactiveTint
                } else {
                    inactiveTint
                },
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
