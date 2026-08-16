package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.PreviewSubMode
import com.example.data.model.ProjectFile
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // System Clipboard helper
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    // File Import Launcher
    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    var fileName = "imported_file.txt"
                    try {
                        context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                fileName = cursor.getString(nameIndex) ?: fileName
                            }
                        }
                    } catch (_: Exception) {}

                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val bytes = stream.readBytes()
                        val content = try {
                            String(bytes, Charsets.UTF_8)
                        } catch (_: Exception) {
                            String(bytes, java.nio.charset.Charset.forName("ISO-8859-1"))
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            viewModel.importSingleFile(fileName, content)
                            viewModel.setModalState(open = false)
                            Toast.makeText(context, "Loaded $fileName", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("MainScreen", "Failed reading file from uri: $it", e)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Native Storage Directory (Folder) Import Launcher via SAF OpenDocumentTree
    val directoryImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { uri ->
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    try {
                        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                    } catch (_: Exception) {}

                    val rootDoc = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri)
                    if (rootDoc != null && rootDoc.isDirectory) {
                        val folderName = rootDoc.name ?: "Imported Workspace"
                        val importedList = mutableListOf<ProjectFile>()

                        fun traverseDirectory(doc: androidx.documentfile.provider.DocumentFile, relativePrefix: String) {
                            val children = doc.listFiles()
                            for (child in children) {
                                val childName = child.name ?: continue
                                // Skip hidden or build cache directories
                                if (childName.startsWith(".") || childName == "node_modules" || childName == "dist") continue
                                val relPath = if (relativePrefix.isEmpty()) childName else "$relativePrefix/$childName"
                                if (child.isDirectory) {
                                    traverseDirectory(child, relPath)
                                } else if (child.isFile) {
                                    try {
                                        context.contentResolver.openInputStream(child.uri)?.use { stream ->
                                            val bytes = stream.readBytes()
                                            val content = try {
                                                String(bytes, Charsets.UTF_8)
                                            } catch (_: Exception) {
                                                String(bytes, java.nio.charset.Charset.forName("ISO-8859-1"))
                                            }
                                            importedList.add(
                                                ProjectFile(
                                                    name = childName,
                                                    path = relPath,
                                                    content = content,
                                                    isFolder = false
                                                )
                                            )
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }

                        traverseDirectory(rootDoc, "")

                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (importedList.isNotEmpty()) {
                                viewModel.importDirectoryWorkspace(folderName, importedList)
                                viewModel.setModalState(open = false)
                                Toast.makeText(context, "Loaded directory '$folderName' (${importedList.size} files)", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Selected directory has no readable files", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("MainScreen", "Failed to import directory", e)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Failed to import directory: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Archive / Multiple Files Import Launcher
    val archiveImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    var archiveName = "project.zip"
                    try {
                        context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                archiveName = cursor.getString(nameIndex) ?: archiveName
                            }
                        }
                    } catch (_: Exception) {}

                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val extracted = ZipUtils.extractArchiveFromStream(stream, archiveName)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            if (extracted.isNotEmpty()) {
                                viewModel.importFiles(extracted)
                                viewModel.setModalState(open = false)
                                Toast.makeText(context, "Unpacked ${extracted.size} files from $archiveName", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No valid files found in archive", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Throwable) {
                    android.util.Log.e("MainScreen", "Failed to extract archive", e)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        Toast.makeText(context, "Failed to extract archive: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    // Status snackbar observer
    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusMessage()
        }
    }

    val activeFile = remember(uiState.files, uiState.activeFileId) {
        uiState.files.firstOrNull { it.id == uiState.activeFileId }
    }

    val bundledHtml = remember(uiState.files, uiState.activeFileId) {
        viewModel.getBundledHtml()
    }

    // External Browser Launcher Helper
    val openExternalBrowser = {
        try {
            val html = viewModel.getBundledHtml()
            val uri = com.example.utils.FileExportUtils.writeToSecureCache(
                context,
                "tzeron_live_preview.html",
                html
            )
            if (uri != null) {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "text/html")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "Could not open external browser", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("MainScreen", "Failed to launch external browser preview", e)
            Toast.makeText(context, "Could not launch external browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val isImeVisible = WindowInsets.isImeVisible

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(TZeronBgDark),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // Permanent Global Top Header Section: Left Branding & Right-aligned Action Bar
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .testTag("permanent_top_header"),
                color = TZeronSurface,
                border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left Section: Branding Logo & Main Brand Title
                    Row(
                        modifier = Modifier.clickable { viewModel.toggleExplorer() },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(34.dp),
                            color = TZeronSurfaceElevated,
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorder)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                androidx.compose.foundation.Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_teezron_logo),
                                    contentDescription = "T•ZERONE CODE Logo",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Text(
                            text = "T•ZERONE CODE",
                            color = TZeronTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Right-aligned Action Bar: New, Open/Import, Format & Transform, Download (Far Top-Right)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        HeaderActionButton(
                            icon = TZeronIcons.NewFile,
                            contentDescription = "New Workspace",
                            testTag = "top_action_new",
                            onClick = { viewModel.setModalState(newProject = true) }
                        )

                        HeaderActionButton(
                            icon = TZeronIcons.Open,
                            contentDescription = "Import Workspace",
                            testTag = "top_action_open",
                            onClick = { viewModel.setModalState(open = true) }
                        )

                        HeaderActionButton(
                            icon = TZeronIcons.Format,
                            contentDescription = "Format & Transform",
                            testTag = "top_action_format",
                            onClick = { viewModel.setModalState(format = true) }
                        )

                        HeaderActionButton(
                            icon = TZeronIcons.Download,
                            contentDescription = "Download & Export",
                            testTag = "top_action_download",
                            onClick = { viewModel.setModalState(download = true) }
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    containerColor = TZeronSurfaceElevated,
                    contentColor = TZeronTextPrimary,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .border(1.dp, TZeronBorder, shape = RoundedCornerShape(12.dp))
                ) {
                    Text(
                        text = data.visuals.message,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = TZeronTextPrimary
                    )
                }
            }
        },
        bottomBar = {
            // Persistent Fixed Bottom Navigation Bar (Permanently visible across all views)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_nav_bar"),
                color = TZeronSurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, TZeronBorder)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NavBottomTabItem(
                        icon = TZeronIcons.Code,
                        label = "CODE EDITOR",
                        isActive = uiState.activeNavTab == MainNavTab.EDIT,
                        onClick = { viewModel.setNavTab(MainNavTab.EDIT) },
                        testTag = "nav_edit_mode"
                    )

                    NavBottomTabItem(
                        icon = TZeronIcons.Eye,
                        label = "PREVIEW",
                        isActive = uiState.activeNavTab == MainNavTab.PREVIEW,
                        onClick = { viewModel.setNavTab(MainNavTab.PREVIEW) },
                        testTag = "nav_preview_mode"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(TZeronBgDark)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Sub-View Routing: Edit Mode vs Preview Mode (Zero-Latency Multi-Pane Architecture)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // 1. CODE EDITOR PANE
                    if (uiState.activeNavTab == MainNavTab.EDIT) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            EditorTabBar(
                                openFiles = uiState.openTabs,
                                activeFileId = uiState.activeFileId,
                                onSelectTab = { viewModel.selectTab(it) },
                                onCloseTab = { viewModel.closeTab(it) },
                                onAddTab = { viewModel.addNewFile("new_file_${System.currentTimeMillis()}.html") },
                                onToggleExplorer = { viewModel.toggleExplorer() }
                            )

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                if (activeFile != null) {
                                    CodeEditorView(
                                        fileContent = activeFile.content,
                                        fileName = activeFile.name,
                                        extension = activeFile.extension,
                                        isReadOnly = uiState.isReadOnly,
                                        onContentChange = { viewModel.updateActiveFileContent(it) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize().background(TZeronBgDark),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = TZeronIcons.Code,
                                                contentDescription = null,
                                                tint = TZeronTextMuted,
                                                modifier = Modifier.size(48.dp)
                                            )
                                            Spacer(modifier = Modifier.height(12.dp))
                                            Text(
                                                text = "No file open. Tap '+' to create a file.",
                                                color = TZeronTextMuted,
                                                fontSize = 12.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                // Ergonomic Elevated Thumb-Zone Bar (Undo/Redo on Left; Paste, Copy, Edit on Right)
                                ThumbZoneBar(
                                    canUndo = uiState.canUndo,
                                    canRedo = uiState.canRedo,
                                    onUndo = { viewModel.undo() },
                                    onRedo = { viewModel.redo() },
                                    isReadOnly = uiState.isReadOnly,
                                    onToggleReadOnly = { viewModel.toggleReadOnly() },
                                    onCopyCode = {
                                        val content = activeFile?.content ?: ""
                                        clipboardManager.setPrimaryClip(ClipData.newPlainText("T•ZERON Code", content))
                                        Toast.makeText(context, "Copied ${activeFile?.name ?: "code"} to clipboard", Toast.LENGTH_SHORT).show()
                                    },
                                    onOneTapPaste = {
                                        if (!uiState.isReadOnly) {
                                            val clip = clipboardManager.primaryClip
                                            if (clip != null && clip.itemCount > 0) {
                                                val pastedText = clip.getItemAt(0).text?.toString() ?: ""
                                                val currentContent = activeFile?.content ?: ""
                                                val updated = if (currentContent.isEmpty()) pastedText else "$currentContent\n$pastedText"
                                                viewModel.updateActiveFileContent(updated)
                                                Toast.makeText(context, "Pasted code from clipboard", Toast.LENGTH_SHORT).show()
                                            } else {
                                                Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "Cannot paste in read-only mode", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    modifier = Modifier.align(Alignment.BottomCenter)
                                )
                            }
                        }
                    }

                    // 2. PREVIEW PANE (Instant Zero-Latency Rendering)
                    if (uiState.activeNavTab == MainNavTab.PREVIEW) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .background(TZeronSurface)
                                    .border(0.5.dp, TZeronBorder)
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PreviewEngineTabButton(
                                    title = "Web Preview",
                                    icon = TZeronIcons.Eye,
                                    isSelected = uiState.previewSubMode == PreviewSubMode.NORMAL,
                                    onClick = { viewModel.setPreviewSubMode(PreviewSubMode.NORMAL) },
                                    testTag = "tab_web_preview"
                                )

                                PreviewEngineTabButton(
                                    title = "Edit Canvas",
                                    icon = TZeronIcons.Design,
                                    isSelected = uiState.previewSubMode == PreviewSubMode.INTERACTIVE_EDITOR,
                                    onClick = { viewModel.setPreviewSubMode(PreviewSubMode.INTERACTIVE_EDITOR) },
                                    testTag = "tab_edit_canvas"
                                )
                            }

                            when (uiState.previewSubMode) {
                                PreviewSubMode.NORMAL -> {
                                    NormalPreviewView(
                                        bundledHtml = bundledHtml,
                                        logs = uiState.consoleLogs,
                                        isTunnelActive = uiState.isTunnelActive,
                                        onAddLog = { lvl, msg -> viewModel.addConsoleLog(lvl, msg) },
                                        onClearLogs = { viewModel.clearConsoleLogs() },
                                        onOpenTunnelModal = { viewModel.setModalState(tunnel = true) },
                                        onOpenExternalBrowser = openExternalBrowser,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                PreviewSubMode.INTERACTIVE_EDITOR -> {
                                    VisualDesignCanvas(
                                        elements = uiState.visualElements,
                                        selectedElementId = uiState.selectedElementId,
                                        hasUnsavedChanges = uiState.hasUnsavedVisualChanges,
                                        onSelectElement = { viewModel.selectVisualElement(it) },
                                        onUpdateElement = { viewModel.updateVisualElement(it) },
                                        onAddElement = { viewModel.addVisualElement(it) },
                                        onDeleteElement = { viewModel.deleteVisualElement(it) },
                                        onCommitSaveToCode = { viewModel.commitVisualChangesToCode() },
                                        onDiscardChanges = { viewModel.discardVisualChanges() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // VS Code-style File Explorer Side Panel Overlay
            AnimatedVisibility(
                visible = uiState.isExplorerOpen,
                enter = slideInHorizontally { -it } + fadeIn(),
                exit = slideOutHorizontally { -it } + fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                FileExplorerDrawer(
                    projectName = uiState.projectName,
                    files = uiState.files,
                    activeFileId = uiState.activeFileId,
                    onSelectFile = {
                        viewModel.selectTab(it)
                        viewModel.toggleExplorer(false)
                    },
                    onAddFile = { viewModel.addNewFile(it) },
                    onDeleteFile = { viewModel.deleteFile(it) },
                    onCloseDrawer = { viewModel.toggleExplorer(false) }
                )
            }
        }

        // Dialogs & Modals
        if (uiState.showNewProjectModal) {
            NewProjectDialog(
                onDismiss = { viewModel.setModalState(newProject = false) },
                onCreateEmpty = {
                    viewModel.initMultiFileWorkspace(it)
                    viewModel.setModalState(newProject = false)
                }
            )
        }

        if (uiState.showOpenModal) {
            OpenProjectModal(
                onDismiss = { viewModel.setModalState(open = false) },
                onImportFile = { fileImportLauncher.launch("*/*") },
                onImportFolder = { directoryImportLauncher.launch(null) },
                onImportArchive = { archiveImportLauncher.launch("*/*") }
            )
        }

        if (uiState.showFormatModal) {
            FormatTransformModal(
                onDismiss = { viewModel.setModalState(format = false) },
                onFormat = { viewModel.formatCurrentCode() },
                onCombine = { viewModel.combineWorkspaceFiles() },
                onSplit = { viewModel.splitActiveHtml() }
            )
        }

        if (uiState.showDownloadModal) {
            DownloadExportModal(
                activeFileName = activeFile?.name ?: "index.html",
                files = uiState.files,
                onDismiss = { viewModel.setModalState(download = false) },
                onDownloadSpecificFile = { file ->
                    viewModel.exportCurrentFile(context)
                },
                onDownloadCombinedHtml = {
                    viewModel.exportCombinedHtml(context)
                },
                onDownloadZipArchive = {
                    viewModel.exportWorkspaceZip(context)
                }
            )
        }

        if (uiState.showTunnelModal) {
            PublicTunnelModal(
                tunnelUrl = uiState.tunnelUrl,
                isActive = uiState.isTunnelActive,
                onToggleTunnel = { viewModel.toggleTunnel(context) },
                onCopyUrl = {
                    clipboardManager.setPrimaryClip(ClipData.newPlainText("Tunnel URL", uiState.tunnelUrl))
                    Toast.makeText(context, "Copied Live Network Link", Toast.LENGTH_SHORT).show()
                },
                onDismiss = { viewModel.setModalState(tunnel = false) }
            )
        }
    }
}

@Composable
private fun HeaderActionButton(
    icon: ImageVector,
    contentDescription: String,
    testTag: String,
    isActive: Boolean = false,
    isEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && isEnabled) 0.85f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "header_btn_scale"
    )

    Surface(
        modifier = Modifier
            .size(34.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = isEnabled
            ) { onClick() }
            .testTag(testTag),
        color = if (isActive) TZeronAccentBlue else if (isEnabled) TZeronSurfaceElevated else TZeronSurfaceCard,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isActive) TZeronAccentBlue else TZeronBorderSubtle
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (isActive) Color.White else if (isEnabled) TZeronTextPrimary else TZeronTextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun NavBottomTabItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "nav_tab_scale"
    )

    Surface(
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .testTag(testTag),
        color = if (isActive) TZeronSurfaceElevated else Color.Transparent,
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isActive) TZeronBorder else Color.Transparent
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) TZeronAccentBlue else TZeronTextMuted,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = if (isActive) TZeronTextPrimary else TZeronTextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun PreviewEngineTabButton(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    testTag: String = ""
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "preview_tab_scale"
    )

    Surface(
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() }
            .testTag(testTag),
        color = if (isSelected) TZeronSurfaceElevated else TZeronSurfaceCard,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.dp else 0.5.dp,
            color = if (isSelected) TZeronBorder else TZeronBorderSubtle
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) TZeronAccentBlue else TZeronTextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = title,
                color = if (isSelected) TZeronTextPrimary else TZeronTextSecondary,
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}
