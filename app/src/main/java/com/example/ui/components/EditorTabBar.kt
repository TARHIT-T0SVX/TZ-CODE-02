package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProjectFile
import com.example.ui.theme.*

@Composable
fun EditorTabBar(
    openFiles: List<ProjectFile>,
    activeFileId: String?,
    onSelectTab: (ProjectFile) -> Unit,
    onCloseTab: (ProjectFile) -> Unit,
    onAddTab: () -> Unit,
    onToggleExplorer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(TZeronSurface)
            .border(0.5.dp, TZeronBorder)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Workspace Folder Drawer Toggle Button
        val folderInteraction = remember { MutableInteractionSource() }
        val isFolderPressed by folderInteraction.collectIsPressedAsState()
        val folderScale by animateFloatAsState(
            targetValue = if (isFolderPressed) 0.86f else 1.0f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            ),
            label = "folder_btn_scale"
        )

        Surface(
            modifier = Modifier
                .padding(end = 6.dp)
                .size(30.dp)
                .scale(folderScale)
                .clickable(
                    interactionSource = folderInteraction,
                    indication = null
                ) { onToggleExplorer() }
                .testTag("workshop_folder_explorer_btn"),
            color = TZeronSurfaceElevated,
            shape = RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorderSubtle)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = TZeronIcons.FolderOpen,
                    contentDescription = "Project File Explorer",
                    tint = TZeronAccentBlue,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // Horizontal scrolling tabs
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically
        ) {
            openFiles.forEach { file ->
                val isActive = file.id == activeFileId
                EditorTabItem(
                    file = file,
                    isActive = isActive,
                    onSelect = { onSelectTab(file) },
                    onClose = { onCloseTab(file) }
                )
            }

            // Add Tab Button
            val addInteraction = remember { MutableInteractionSource() }
            val isAddPressed by addInteraction.collectIsPressedAsState()
            val addScale by animateFloatAsState(
                targetValue = if (isAddPressed) 0.86f else 1.0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "add_tab_scale"
            )

            Surface(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(30.dp)
                    .scale(addScale)
                    .clickable(
                        interactionSource = addInteraction,
                        indication = null
                    ) { onAddTab() }
                    .testTag("add_tab_button"),
                color = TZeronSurfaceElevated,
                shape = RoundedCornerShape(10.dp),
                border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorderSubtle)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = TZeronIcons.Add,
                        contentDescription = "Add new file tab",
                        tint = TZeronTextSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EditorTabItem(
    file: ProjectFile,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tab_scale"
    )

    Surface(
        modifier = Modifier
            .padding(horizontal = 3.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onSelect() }
            .testTag("tab_${file.name}"),
        color = if (isActive) TZeronSurfaceElevated else TZeronSurfaceCard,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isActive) 1.dp else 0.5.dp,
            color = if (isActive) TZeronBorder else TZeronBorderSubtle
        )
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Extension Tag Badge
            Box(
                modifier = Modifier
                    .background(
                        if (isActive) TZeronSurface else Color(0xFF141317),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    text = file.extension.uppercase().ifEmpty { "TXT" },
                    color = if (isActive) TZeronTextPrimary else TZeronTextMuted,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = file.name,
                color = if (isActive) TZeronTextPrimary else TZeronTextSecondary,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = FontFamily.Monospace
            )

            if (file.isModified) {
                Spacer(modifier = Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(TZeronAccentBlue, shape = RoundedCornerShape(3.dp))
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(24.dp)
                    .testTag("close_tab_${file.name}")
            ) {
                Icon(
                    imageVector = TZeronIcons.Close,
                    contentDescription = "Close tab ${file.name}",
                    tint = TZeronTextMuted,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}
