package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProjectFile
import com.example.ui.theme.*

@Composable
fun FileExplorerDrawer(
    projectName: String,
    files: List<ProjectFile>,
    activeFileId: String?,
    onSelectFile: (ProjectFile) -> Unit,
    onAddFile: (String) -> Unit,
    onDeleteFile: (ProjectFile) -> Unit,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    Surface(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .testTag("file_explorer_drawer"),
        color = TZeronSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, TZeronBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .background(TZeronSurfaceElevated)
                    .border(0.5.dp, TZeronBorderSubtle)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TZeronIcons.FolderOpen,
                        contentDescription = "Project explorer",
                        tint = TZeronTextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EXPLORER",
                        color = TZeronTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp
                    )
                }

                Row {
                    IconButton(
                        onClick = { showNewFileDialog = true },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("explorer_add_file_btn")
                    ) {
                        Icon(
                            imageVector = TZeronIcons.NewFile,
                            contentDescription = "New File",
                            tint = TZeronTextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onCloseDrawer,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("explorer_close_btn")
                    ) {
                        Icon(
                            imageVector = TZeronIcons.Close,
                            contentDescription = "Close Explorer",
                            tint = TZeronTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Project Root Folder Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TZeronSurfaceCard)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = TZeronIcons.ChevronDown,
                    contentDescription = null,
                    tint = TZeronTextMuted,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = projectName.uppercase(),
                    color = TZeronTextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            // File Tree List
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(files, key = { it.id }) { file ->
                    val isSelected = file.id == activeFileId

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isSelected) TZeronSurfaceElevated else Color.Transparent)
                            .border(
                                0.5.dp,
                                if (isSelected) TZeronBorder else Color.Transparent
                            )
                            .clickable { onSelectFile(file) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .testTag("explorer_file_${file.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // File extension badge
                            Box(
                                modifier = Modifier
                                    .background(TZeronSurfaceCard, shape = MaterialTheme.shapes.extraSmall)
                                    .border(0.5.dp, TZeronBorderSubtle, shape = MaterialTheme.shapes.extraSmall)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = file.extension.uppercase().ifEmpty { "TXT" },
                                    color = if (isSelected) TZeronTextPrimary else TZeronTextSecondary,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = file.name,
                                color = if (isSelected) TZeronTextPrimary else TZeronTextSecondary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }

                        if (files.size > 1) {
                            IconButton(
                                onClick = { onDeleteFile(file) },
                                modifier = Modifier.size(24.dp).testTag("delete_file_${file.name}")
                            ) {
                                Icon(
                                    imageVector = TZeronIcons.Delete,
                                    contentDescription = "Delete file",
                                    tint = TZeronTextMuted,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = {
                showNewFileDialog = false
                newFileName = ""
            },
            containerColor = TZeronSurface,
            titleContentColor = TZeronTextPrimary,
            textContentColor = TZeronTextSecondary,
            title = {
                Text("New File", fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            },
            text = {
                Column {
                    Text("Enter filename (e.g. style.css, script.js, page.html):", fontSize = 12.sp, color = TZeronTextSecondary)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFileName,
                        onValueChange = { newFileName = it },
                        singleLine = true,
                        placeholder = { Text("filename.html", color = TZeronTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TZeronTextPrimary,
                            unfocusedBorderColor = TZeronBorder,
                            focusedTextColor = TZeronTextPrimary,
                            unfocusedTextColor = TZeronTextPrimary
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("new_file_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFileName.isNotBlank()) {
                            onAddFile(newFileName.trim())
                            showNewFileDialog = false
                            newFileName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TZeronSurfaceElevated),
                    modifier = Modifier.testTag("confirm_create_file_btn")
                ) {
                    Text("Create", color = TZeronTextPrimary, fontFamily = FontFamily.Monospace)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showNewFileDialog = false
                    newFileName = ""
                }) {
                    Text("Cancel", color = TZeronTextMuted, fontFamily = FontFamily.Monospace)
                }
            }
        )
    }
}
