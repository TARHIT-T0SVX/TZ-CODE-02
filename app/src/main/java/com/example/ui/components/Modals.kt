package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.data.model.SyntaxTheme
import com.example.ui.theme.*

@Composable
fun NewProjectDialog(
    onDismiss: () -> Unit,
    onCreateEmpty: (String) -> Unit
) {
    var projectName by remember { mutableStateOf("my-project") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TZeronIcons.NewFile,
                    contentDescription = null,
                    tint = TZeronTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "NEW WORKSPACE",
                    color = TZeronTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Initializes a clean multi-file workspace (index.html, styles.css, script.js).",
                    color = TZeronTextSecondary,
                    fontSize = 12.sp
                )
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name", fontSize = 12.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TZeronAccentBlue,
                        unfocusedBorderColor = TZeronBorder,
                        focusedTextColor = TZeronTextPrimary,
                        unfocusedTextColor = TZeronTextPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_project_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (projectName.isNotBlank()) {
                        onCreateEmpty(projectName.trim())
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TZeronAccentBlue),
                modifier = Modifier.testTag("confirm_new_project_btn")
            ) {
                Text("CREATE WORKSPACE", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("CANCEL", color = TZeronTextMuted, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = TZeronSurface
    )
}

@Composable
fun OpenProjectModal(
    onDismiss: () -> Unit,
    onImportFile: () -> Unit,
    onImportFolder: () -> Unit,
    onImportArchive: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("FILE", "DIRECTORY", "ARCHIVE")

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TZeronIcons.Open,
                    contentDescription = null,
                    tint = TZeronTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "IMPORT WORKSPACE",
                    color = TZeronTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Tab Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TZeronBgDark, shape = RoundedCornerShape(14.dp))
                        .border(0.5.dp, TZeronBorderSubtle, shape = RoundedCornerShape(14.dp))
                        .padding(3.dp)
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { selectedTab = index }
                                .background(
                                    if (selectedTab == index) TZeronSurfaceElevated else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                color = if (selectedTab == index) TZeronTextPrimary else TZeronTextMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                when (selectedTab) {
                    0 -> {
                        // File Import
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Import individual code files (.html, .css, .js, .json, .ts, .svg, .txt, .pdf).",
                                color = TZeronTextSecondary,
                                fontSize = 12.sp
                            )
                            Button(
                                onClick = onImportFile,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_file_trigger_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = TZeronSurfaceElevated)
                            ) {
                                Icon(imageVector = TZeronIcons.File, contentDescription = null, tint = TZeronTextPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SELECT LOCAL FILE", color = TZeronTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    1 -> {
                        // Directory / Tree Import
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Import full project directory structure using native folder picker.",
                                color = TZeronTextSecondary,
                                fontSize = 12.sp
                            )
                            Button(
                                onClick = onImportFolder,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_folder_trigger_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = TZeronSurfaceElevated)
                            ) {
                                Icon(imageVector = TZeronIcons.FolderOpen, contentDescription = null, tint = TZeronTextPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("SELECT DIRECTORY", color = TZeronTextPrimary, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    2 -> {
                        // Archive Import (.zip, .rar, .7z)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Decompress and unpack workspace from .zip, .rar, or .7z archives.",
                                color = TZeronTextSecondary,
                                fontSize = 12.sp
                            )
                            Button(
                                onClick = onImportArchive,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_archive_trigger_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = TZeronSurfaceElevated)
                            ) {
                                Icon(imageVector = TZeronIcons.Archive, contentDescription = null, tint = TZeronTextPrimary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("UNPACK ARCHIVE (.ZIP / .RAR / .7Z)", color = TZeronTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("CLOSE", color = TZeronTextMuted, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = TZeronSurface
    )
}

@Composable
fun FormatTransformModal(
    onDismiss: () -> Unit,
    onFormat: () -> Unit,
    onCombine: () -> Unit,
    onSplit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TZeronIcons.Format,
                    contentDescription = null,
                    tint = TZeronTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "TRANSFORM CODE",
                    color = TZeronTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Format Tool
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onFormat()
                            onDismiss()
                        }
                        .testTag("tool_format_btn"),
                    color = TZeronSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = TZeronIcons.Format, contentDescription = null, tint = TZeronTextPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Auto-Beautify / Format", color = TZeronTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Standardizes indentation, spacing, and tags.", color = TZeronTextSecondary, fontSize = 10.sp)
                        }
                    }
                }

                // Split Code Tool (Requirement: extract HTML/CSS/JS with linked dependencies)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSplit()
                            onDismiss()
                        }
                        .testTag("tool_split_btn"),
                    color = TZeronSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = TZeronIcons.Split, contentDescription = null, tint = TZeronAccentBlue)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Split Code (Extract HTML/CSS/JS)", color = TZeronTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Separates inline <style> and <script> into styles.css & script.js.", color = TZeronTextSecondary, fontSize = 10.sp)
                        }
                    }
                }

                // Combine Tool
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onCombine()
                            onDismiss()
                        }
                        .testTag("tool_combine_btn"),
                    color = TZeronSurfaceElevated,
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = TZeronIcons.Combine, contentDescription = null, tint = TZeronTextPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Combine (Single File HTML)", color = TZeronTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Inlines external CSS/JS directly into <style> and <script>.", color = TZeronTextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("CANCEL", color = TZeronTextMuted, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = TZeronSurface
    )
}

@Composable
fun DownloadExportModal(
    activeFileName: String,
    files: List<ProjectFile>,
    onDismiss: () -> Unit,
    onDownloadSpecificFile: (ProjectFile) -> Unit,
    onDownloadCombinedHtml: () -> Unit,
    onDownloadZipArchive: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TZeronIcons.Download,
                    contentDescription = null,
                    tint = TZeronTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DOWNLOAD & EXPORT",
                    color = TZeronTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("INDIVIDUAL FILES:", color = TZeronTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                items(files) { file ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDownloadSpecificFile(file)
                                onDismiss()
                            }
                            .testTag("export_file_${file.name}"),
                        color = TZeronSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorder)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = TZeronIcons.File, contentDescription = null, tint = TZeronAccentBlue, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Export '${file.name}'", color = TZeronTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Save ${file.extension.uppercase()} file to Downloads.", color = TZeronTextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("PACKAGES & BUNDLES:", color = TZeronTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                item {
                    // Complete .ZIP Archive
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDownloadZipArchive()
                                onDismiss()
                            }
                            .testTag("export_zip_archive_btn"),
                        color = TZeronSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorder)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = TZeronIcons.Archive, contentDescription = null, tint = TZeronTextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Export Full .ZIP Archive", color = TZeronTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Packages all files in directory tree.", color = TZeronTextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }

                item {
                    // Combined HTML
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDownloadCombinedHtml()
                                onDismiss()
                            }
                            .testTag("export_combined_html_btn"),
                        color = TZeronSurfaceElevated,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorder)
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = TZeronIcons.Combine, contentDescription = null, tint = TZeronTextPrimary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("Export Single Bundled HTML", color = TZeronTextPrimary, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text("Zero-dependency standalone HTML file.", color = TZeronTextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("CANCEL", color = TZeronTextMuted, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = TZeronSurface
    )
}

@Composable
fun SyntaxThemeModal(
    currentTheme: SyntaxTheme,
    onSelectTheme: (SyntaxTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = TZeronIcons.Palette, contentDescription = null, tint = TZeronTextPrimary, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("SYNTAX THEME", color = TZeronTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SyntaxTheme.values().forEach { theme ->
                    val isSelected = theme == currentTheme
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectTheme(theme)
                                onDismiss()
                            },
                        color = if (isSelected) TZeronSurfaceElevated else TZeronSurfaceCard,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.5.dp,
                            if (isSelected) TZeronAccentBlue else TZeronBorderSubtle
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = theme.displayName,
                                color = if (isSelected) TZeronAccentBlue else TZeronTextPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                            if (isSelected) {
                                Icon(imageVector = TZeronIcons.Check, contentDescription = null, tint = TZeronAccentBlue, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CLOSE", color = TZeronTextMuted, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = TZeronSurface
    )
}

@Composable
fun PublicTunnelModal(
    tunnelUrl: String,
    isActive: Boolean,
    onToggleTunnel: () -> Unit,
    onCopyUrl: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = TZeronIcons.Public,
                    contentDescription = null,
                    tint = TZeronTextPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PUBLIC TUNNEL & LIVE SHARE",
                    color = TZeronTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Share your live multi-file project over the network for testing on other devices.",
                    color = TZeronTextSecondary,
                    fontSize = 12.sp
                )

                // Status Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TZeronSurfaceElevated, shape = RoundedCornerShape(14.dp))
                        .border(0.5.dp, TZeronBorder, shape = RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(if (isActive) TZeronAccentBlue else TZeronTextMuted, shape = RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isActive) "TUNNEL ACTIVE (ONLINE)" else "TUNNEL OFFLINE",
                            color = if (isActive) TZeronAccentBlue else TZeronTextMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Switch(
                        checked = isActive,
                        onCheckedChange = { onToggleTunnel() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = TZeronAccentBlue,
                            checkedTrackColor = TZeronSurfaceCard,
                            uncheckedThumbColor = TZeronTextMuted,
                            uncheckedTrackColor = TZeronBgDark
                        )
                    )
                }

                if (isActive) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("LIVE ENDPOINT:", color = TZeronTextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onCopyUrl() },
                            color = TZeronBgDark,
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TZeronBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = tunnelUrl,
                                    color = TZeronTextPrimary,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = TZeronIcons.Copy,
                                    contentDescription = "Copy URL",
                                    tint = TZeronAccentBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("DONE", color = TZeronTextPrimary, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = TZeronSurface
    )
}
