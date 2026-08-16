package com.example.ui.screens

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectRepository
import com.example.data.model.*
import com.example.utils.CodeFormatter
import com.example.utils.FileExportUtils
import com.example.utils.LocalHttpServer
import com.example.utils.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class MainNavTab {
    EDIT,
    PREVIEW
}

data class MainUiState(
    val projectName: String = "tzeron-project",
    val files: List<ProjectFile> = emptyList(),
    val openTabs: List<ProjectFile> = emptyList(),
    val activeFileId: String? = null,
    val isReadOnly: Boolean = false,
    val activeNavTab: MainNavTab = MainNavTab.EDIT,
    val previewSubMode: PreviewSubMode = PreviewSubMode.NORMAL,
    val isExplorerOpen: Boolean = false,
    val consoleLogs: List<ConsoleLogItem> = emptyList(),
    val isTunnelActive: Boolean = false,
    val tunnelUrl: String = "http://127.0.0.1:8080",
    val syntaxTheme: SyntaxTheme = SyntaxTheme.VS_CODE_DARK,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    // Interactive Visual IDE Staging
    val visualElements: List<DOMElementNode> = emptyList(),
    val selectedElementId: String? = null,
    val hasUnsavedVisualChanges: Boolean = false,
    // Modals visibility
    val showNewProjectModal: Boolean = false,
    val showOpenModal: Boolean = false,
    val showFormatModal: Boolean = false,
    val showDownloadModal: Boolean = false,
    val showTunnelModal: Boolean = false,
    val showSyntaxThemeModal: Boolean = false,
    val statusMessage: String? = null,
    val isLoading: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val repository: ProjectRepository = ProjectRepository(
        AppDatabase.getDatabase(application).projectDao()
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val undoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private var autoSaveJob: Job? = null

    // Background HTTP Server for Local and Public LAN preview
    private val httpServer = LocalHttpServer(
        getFiles = { _uiState.value.files },
        getBundledHtml = { getBundledHtml() }
    )

    init {
        loadOrInitWorkspace()
    }

    private fun loadOrInitWorkspace() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "Attempting to load saved workspace from database...")
                val savedProjects = repository.allProjects.firstOrNull()
                val latestProject = savedProjects?.firstOrNull()
                if (latestProject != null) {
                    val files = repository.deserializeFiles(latestProject.filesJson)
                    if (files.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            restoreWorkspace(latestProject.name, files)
                        }
                        Log.i(TAG, "Successfully restored workspace '${latestProject.name}' with ${files.size} files")
                        return@launch
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error loading workspace from repository, falling back to clean workspace", e)
            }

            withContext(Dispatchers.Main) {
                initMultiFileWorkspace(name = "tzeron-workspace")
            }
        }
    }

    fun initMultiFileWorkspace(name: String = "tzeron-workspace") {
        // Requirement: HTML, CSS, and JavaScript files must exist upon launch, but their contents must be completely empty.
        val htmlFile = ProjectFile(
            name = "index.html",
            path = "index.html",
            content = "",
            isFolder = false
        )

        val cssFile = ProjectFile(
            name = "styles.css",
            path = "styles.css",
            content = "",
            isFolder = false
        )

        val jsFile = ProjectFile(
            name = "script.js",
            path = "script.js",
            content = "",
            isFolder = false
        )

        val defaultFiles = listOf(htmlFile, cssFile, jsFile)

        undoStacks.clear()
        redoStacks.clear()
        defaultFiles.forEach { file ->
            undoStacks[file.id] = ArrayDeque(listOf(file.content))
            redoStacks[file.id] = ArrayDeque()
        }

        _uiState.update {
            it.copy(
                projectName = name,
                files = defaultFiles,
                openTabs = defaultFiles,
                activeFileId = htmlFile.id,
                isReadOnly = false,
                canUndo = false,
                canRedo = false,
                visualElements = emptyList(),
                selectedElementId = null,
                hasUnsavedVisualChanges = false,
                statusMessage = "Initialized blank workspace"
            )
        }

        // Cache initial state to Room
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveProject(name, defaultFiles)
        }
    }

    private fun restoreWorkspace(name: String, files: List<ProjectFile>) {
        undoStacks.clear()
        redoStacks.clear()
        files.forEach { file ->
            undoStacks[file.id] = ArrayDeque(listOf(file.content))
            redoStacks[file.id] = ArrayDeque()
        }

        val primaryFile = files.firstOrNull { it.extension == "html" || it.extension == "htm" } ?: files.firstOrNull()

        _uiState.update {
            it.copy(
                projectName = name,
                files = files,
                openTabs = files.take(5),
                activeFileId = primaryFile?.id,
                isReadOnly = false,
                canUndo = false,
                canRedo = false,
                visualElements = emptyList(),
                selectedElementId = null,
                hasUnsavedVisualChanges = false,
                statusMessage = "Loaded cached workspace ($name)"
            )
        }
    }

    fun selectTab(file: ProjectFile) {
        _uiState.update { current ->
            val updatedTabs = if (current.openTabs.any { it.id == file.id }) {
                current.openTabs
            } else {
                current.openTabs + file
            }
            val activeId = file.id
            val uStack = undoStacks[activeId]
            val rStack = redoStacks[activeId]
            current.copy(
                openTabs = updatedTabs,
                activeFileId = activeId,
                canUndo = (uStack?.size ?: 0) > 1,
                canRedo = (rStack?.size ?: 0) > 0
            )
        }
    }

    fun closeTab(file: ProjectFile) {
        _uiState.update { current ->
            val remainingTabs = current.openTabs.filter { it.id != file.id }
            val nextActiveId = if (current.activeFileId == file.id) {
                remainingTabs.lastOrNull()?.id
            } else {
                current.activeFileId
            }
            val uStack = undoStacks[nextActiveId]
            val rStack = redoStacks[nextActiveId]
            current.copy(
                openTabs = remainingTabs,
                activeFileId = nextActiveId,
                canUndo = (uStack?.size ?: 0) > 1,
                canRedo = (rStack?.size ?: 0) > 0
            )
        }
    }

    fun updateActiveFileContent(newContent: String, isUndoRedoAction: Boolean = false) {
        val current = _uiState.value
        val activeId = current.activeFileId ?: return

        if (!isUndoRedoAction) {
            val uStack = undoStacks.getOrPut(activeId) { ArrayDeque() }
            if (uStack.isEmpty() || uStack.last() != newContent) {
                uStack.addLast(newContent)
                if (uStack.size > 50) uStack.removeFirst()
                redoStacks[activeId]?.clear()
            }
        }

        val updatedFiles = current.files.map { file ->
            if (file.id == activeId) {
                file.copy(content = newContent, isModified = true)
            } else file
        }
        val updatedTabs = current.openTabs.map { tab ->
            if (tab.id == activeId) {
                tab.copy(content = newContent, isModified = true)
            } else tab
        }

        val uStack = undoStacks[activeId]
        val rStack = redoStacks[activeId]

        _uiState.update {
            it.copy(
                files = updatedFiles,
                openTabs = updatedTabs,
                canUndo = (uStack?.size ?: 0) > 1,
                canRedo = (rStack?.size ?: 0) > 0
            )
        }

        // Automatic instant debounced background persistence to Room Database
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(400)
            repository.saveProject(_uiState.value.projectName, _uiState.value.files)
        }
    }

    fun undo() {
        val current = _uiState.value
        val activeId = current.activeFileId ?: return
        val uStack = undoStacks[activeId] ?: return
        val rStack = redoStacks.getOrPut(activeId) { ArrayDeque() }

        if (uStack.size > 1) {
            val currentContent = uStack.removeLast()
            rStack.addLast(currentContent)
            val previousContent = uStack.last()
            updateActiveFileContent(previousContent, isUndoRedoAction = true)
        }
    }

    fun redo() {
        val current = _uiState.value
        val activeId = current.activeFileId ?: return
        val uStack = undoStacks.getOrPut(activeId) { ArrayDeque() }
        val rStack = redoStacks[activeId] ?: return

        if (rStack.isNotEmpty()) {
            val redoContent = rStack.removeLast()
            uStack.addLast(redoContent)
            updateActiveFileContent(redoContent, isUndoRedoAction = true)
        }
    }

    fun toggleReadOnly() {
        _uiState.update { it.copy(isReadOnly = !it.isReadOnly) }
    }

    fun setSyntaxTheme(theme: SyntaxTheme) {
        _uiState.update { it.copy(syntaxTheme = theme) }
    }

    fun setNavTab(tab: MainNavTab) {
        _uiState.update { it.copy(activeNavTab = tab) }
        if (tab == MainNavTab.PREVIEW) {
            syncVisualElementsFromActiveFile()
        }
    }

    fun setPreviewSubMode(mode: PreviewSubMode) {
        _uiState.update { it.copy(previewSubMode = mode) }
    }

    fun toggleExplorer(open: Boolean? = null) {
        _uiState.update { it.copy(isExplorerOpen = open ?: !it.isExplorerOpen) }
    }

    fun addNewFile(fileName: String) {
        val newFile = ProjectFile(
            name = fileName,
            path = fileName,
            content = "",
            isFolder = false
        )
        undoStacks[newFile.id] = ArrayDeque(listOf(""))
        redoStacks[newFile.id] = ArrayDeque()

        _uiState.update { current ->
            val updatedFiles = current.files + newFile
            val updatedTabs = current.openTabs + newFile
            current.copy(
                files = updatedFiles,
                openTabs = updatedTabs,
                activeFileId = newFile.id,
                canUndo = false,
                canRedo = false,
                statusMessage = "Created $fileName"
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveProject(_uiState.value.projectName, _uiState.value.files)
        }
    }

    fun importSingleFile(fileName: String, content: String) {
        val newFile = ProjectFile(
            name = fileName,
            path = fileName,
            content = content,
            isFolder = false
        )
        undoStacks[newFile.id] = ArrayDeque(listOf(content))
        redoStacks[newFile.id] = ArrayDeque()

        _uiState.update { current ->
            // Replace existing file with same path if present or append new file
            val existingIndex = current.files.indexOfFirst { it.path.equals(fileName, ignoreCase = true) || it.name.equals(fileName, ignoreCase = true) }
            val updatedFiles = if (existingIndex != -1) {
                current.files.mapIndexed { idx, f -> if (idx == existingIndex) newFile else f }
            } else {
                current.files + newFile
            }

            val updatedTabs = if (current.openTabs.any { it.name.equals(fileName, ignoreCase = true) }) {
                current.openTabs.map { if (it.name.equals(fileName, ignoreCase = true)) newFile else it }
            } else {
                current.openTabs + newFile
            }

            current.copy(
                files = updatedFiles,
                openTabs = updatedTabs,
                activeFileId = newFile.id,
                canUndo = false,
                canRedo = false,
                showOpenModal = false,
                statusMessage = "Imported $fileName"
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveProject(_uiState.value.projectName, _uiState.value.files)
        }
    }

    fun importFiles(newFiles: List<ProjectFile>) {
        if (newFiles.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            newFiles.forEach { file ->
                undoStacks[file.id] = ArrayDeque(listOf(file.content))
                redoStacks[file.id] = ArrayDeque()
            }
            val primaryFile = newFiles.firstOrNull { it.name.equals("index.html", ignoreCase = true) }
                ?: newFiles.firstOrNull { it.extension.equals("html", ignoreCase = true) }
                ?: newFiles.first()

            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    val mergedFiles = current.files.filter { cur -> newFiles.none { it.path == cur.path } } + newFiles
                    val mergedTabs = current.openTabs.filter { cur -> newFiles.none { it.path == cur.path } } + newFiles.take(5)
                    current.copy(
                        files = mergedFiles,
                        openTabs = mergedTabs,
                        activeFileId = primaryFile.id,
                        showOpenModal = false,
                        statusMessage = "Imported ${newFiles.size} files"
                    )
                }
            }
            repository.saveProject(_uiState.value.projectName, _uiState.value.files)
        }
    }

    fun importDirectoryWorkspace(name: String, importedFiles: List<ProjectFile>) {
        if (importedFiles.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            undoStacks.clear()
            redoStacks.clear()
            importedFiles.forEach { file ->
                undoStacks[file.id] = ArrayDeque(listOf(file.content))
                redoStacks[file.id] = ArrayDeque()
            }

            val primaryFile = importedFiles.firstOrNull { it.name.equals("index.html", ignoreCase = true) }
                ?: importedFiles.firstOrNull { it.extension.equals("html", ignoreCase = true) }
                ?: importedFiles.first()

            withContext(Dispatchers.Main) {
                _uiState.update {
                    it.copy(
                        projectName = name,
                        files = importedFiles,
                        openTabs = importedFiles.take(5),
                        activeFileId = primaryFile.id,
                        isReadOnly = false,
                        canUndo = false,
                        canRedo = false,
                        visualElements = emptyList(),
                        selectedElementId = null,
                        showOpenModal = false,
                        statusMessage = "Loaded folder $name (${importedFiles.size} files)"
                    )
                }
            }
            repository.saveProject(name, importedFiles)
        }
    }

    fun deleteFile(file: ProjectFile) {
        _uiState.update { current ->
            val updatedFiles = current.files.filter { it.id != file.id }
            val updatedTabs = current.openTabs.filter { it.id != file.id }
            val nextActiveId = if (current.activeFileId == file.id) updatedTabs.firstOrNull()?.id else current.activeFileId
            current.copy(
                files = updatedFiles,
                openTabs = updatedTabs,
                activeFileId = nextActiveId,
                statusMessage = "Deleted ${file.name}"
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveProject(_uiState.value.projectName, _uiState.value.files)
        }
    }

    /**
     * Resolves and bundles multi-file project (index.html, styles.css, script.js, svg assets, markdown, etc.) into standalone preview HTML
     */
    fun getBundledHtml(): String {
        val current = _uiState.value
        val files = current.files

        if (files.isEmpty()) {
            return "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>body { margin: 0; background: #0D0D0F; color: #888; font-family: monospace; padding: 20px; }</style></head><body>No files in project.</body></html>"
        }

        // 1. Locate primary entrypoint HTML file
        val activeFile = files.firstOrNull { it.id == current.activeFileId }
        val rootIndexHtml = files.firstOrNull { it.name.equals("index.html", ignoreCase = true) }
        val anyHtml = files.firstOrNull { it.extension.equals("html", ignoreCase = true) || it.extension.equals("htm", ignoreCase = true) }
        
        val htmlFile = when {
            activeFile != null && (activeFile.extension.equals("html", ignoreCase = true) || activeFile.extension.equals("htm", ignoreCase = true)) -> activeFile
            rootIndexHtml != null -> rootIndexHtml
            anyHtml != null -> anyHtml
            activeFile != null -> activeFile
            else -> files.first()
        }

        var html: String
        val isHtmlSource = htmlFile.extension.equals("html", ignoreCase = true) || htmlFile.extension.equals("htm", ignoreCase = true)

        if (isHtmlSource) {
            html = htmlFile.content
            if (html.isBlank()) {
                // If html file is empty, provide a minimal live canvas
                html = "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'></head><body></body></html>"
            }
        } else if (htmlFile.extension.equals("md", ignoreCase = true) || htmlFile.extension.equals("markdown", ignoreCase = true)) {
            // Render Markdown preview
            val escapedMd = htmlFile.content
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
            html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { margin: 0; padding: 20px; background: #0D0D0F; color: #ECECED; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; line-height: 1.6; }
                        pre { background: #18181D; padding: 12px; border-radius: 8px; overflow-x: auto; color: #79B8FF; }
                        h1, h2, h3 { color: #FFFFFF; border-bottom: 1px solid #282830; padding-bottom: 6px; }
                        a { color: #0088FF; }
                    </style>
                </head>
                <body>
                    <pre style="white-space: pre-wrap; font-family: monospace;">$escapedMd</pre>
                </body>
                </html>
            """.trimIndent()
        } else if (htmlFile.extension.equals("css", ignoreCase = true)) {
            // Render CSS preview demo
            html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                    ${htmlFile.content}
                    </style>
                </head>
                <body>
                    <div style="padding: 24px; font-family: sans-serif;">
                        <h1>CSS Preview Demo</h1>
                        <p>Styling applied from ${htmlFile.name}</p>
                        <button class="btn primary">Sample Button</button>
                        <div class="card" style="margin-top: 16px; padding: 16px; border: 1px solid #444; border-radius: 8px;">
                            Sample Card Box
                        </div>
                    </div>
                </body>
                </html>
            """.trimIndent()
        } else if (htmlFile.extension.equals("js", ignoreCase = true) || htmlFile.extension.equals("ts", ignoreCase = true)) {
            // Render JS preview with console output canvas
            html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>
                        body { margin: 0; padding: 20px; background: #0D0D0F; color: #ECECED; font-family: monospace; }
                        #output { background: #16161A; border: 1px solid #2C2C35; border-radius: 8px; padding: 16px; min-height: 120px; white-space: pre-wrap; }
                        h2 { color: #0088FF; margin-top: 0; font-size: 16px; }
                    </style>
                </head>
                <body>
                    <h2>Running: ${htmlFile.name}</h2>
                    <div id="output"></div>
                    <script>
                        const out = document.getElementById('output');
                        const append = (msg, col) => {
                            const d = document.createElement('div');
                            d.style.color = col || '#ECECED';
                            d.textContent = msg;
                            out.appendChild(d);
                        };
                        console.log = (...args) => { append(args.join(' '), '#99FFE4'); };
                        console.error = (...args) => { append('Error: ' + args.join(' '), '#FF6B6B'); };
                        console.warn = (...args) => { append('Warn: ' + args.join(' '), '#FFCC00'); };
                        try {
                            ${htmlFile.content}
                        } catch(e) {
                            console.error(e.message);
                        }
                    </script>
                </body>
                </html>
            """.trimIndent()
        } else {
            // Plain text or JSON
            val escaped = htmlFile.content.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <style>body { margin: 0; padding: 16px; background: #0D0D0F; color: #ECECED; font-family: monospace; white-space: pre-wrap; }</style>
                </head>
                <body>$escaped</body>
                </html>
            """.trimIndent()
        }

        if (isHtmlSource) {
            // 2. Comprehensive multi-file resolution for CSS
            files.filter { it.extension.equals("css", ignoreCase = true) && it.content.isNotBlank() }.forEach { cssFile ->
                val escapedName = Regex.escape(cssFile.name)
                val escapedPath = Regex.escape(cssFile.path)
                // Match <link rel="stylesheet" href="...styles.css"> variations (relative, absolute, ./, etc.)
                val linkRegex = Regex("""<link\b[^>]*href=["'](?:\./)?(?:${escapedPath}|${escapedName})["'][^>]*>""", RegexOption.IGNORE_CASE)
                val styleTag = "<style>\n/* [TZeron Inlined: ${cssFile.name}] */\n${cssFile.content}\n</style>"
                
                if (linkRegex.containsMatchIn(html)) {
                    html = html.replace(linkRegex, styleTag)
                } else if (cssFile.name.equals("style.css", ignoreCase = true) || cssFile.name.equals("styles.css", ignoreCase = true) || cssFile.name.equals("main.css", ignoreCase = true) || cssFile.name.equals("app.css", ignoreCase = true)) {
                    // Auto-inject standard style sheets if not explicitly linked
                    if (!html.contains(cssFile.content)) {
                        html = if (html.contains("</head>", ignoreCase = true)) {
                            html.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$styleTag\n</head>")
                        } else {
                            "$styleTag\n$html"
                        }
                    }
                }
            }

            // 3. Comprehensive multi-file resolution for JS / TS
            files.filter { (it.extension.equals("js", ignoreCase = true) || it.extension.equals("ts", ignoreCase = true)) && it.content.isNotBlank() }.forEach { jsFile ->
                val escapedName = Regex.escape(jsFile.name)
                val escapedPath = Regex.escape(jsFile.path)
                // Match <script src="...script.js"></script>
                val scriptRegex = Regex("""<script\b[^>]*src=["'](?:\./)?(?:${escapedPath}|${escapedName})["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE)
                val scriptTag = "<script>\n// [TZeron Inlined: ${jsFile.name}]\n${jsFile.content}\n</script>"
                
                if (scriptRegex.containsMatchIn(html)) {
                    html = html.replace(scriptRegex, scriptTag)
                } else if (jsFile.name.equals("script.js", ignoreCase = true) || jsFile.name.equals("main.js", ignoreCase = true) || jsFile.name.equals("app.js", ignoreCase = true) || jsFile.name.equals("index.js", ignoreCase = true)) {
                    // Auto-inject standard scripts if not explicitly linked
                    if (!html.contains(jsFile.content)) {
                        html = if (html.contains("</body>", ignoreCase = true)) {
                            html.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$scriptTag\n</body>")
                        } else {
                            "$html\n$scriptTag"
                        }
                    }
                }
            }

            // 4. Resolve inlined SVG or inline image files
            files.filter { it.extension.equals("svg", ignoreCase = true) && it.content.isNotBlank() }.forEach { svgFile ->
                val escapedName = Regex.escape(svgFile.name)
                val escapedPath = Regex.escape(svgFile.path)
                val encodedSvg = "data:image/svg+xml;utf8," + java.net.URLEncoder.encode(svgFile.content, "UTF-8")
                val imgRegex = Regex("""(<img\b[^>]*src=["'])(?:\./)?(?:${escapedPath}|${escapedName})(["'][^>]*>)""", RegexOption.IGNORE_CASE)
                html = html.replace(imgRegex) { m -> "${m.groupValues[1]}$encodedSvg${m.groupValues[2]}" }
            }
        }

        return html
    }

    // Code Transformations
    fun formatCurrentCode() {
        val current = _uiState.value
        val activeFile = current.files.firstOrNull { it.id == current.activeFileId } ?: return
        val formatted = CodeFormatter.formatCode(activeFile.content, activeFile.extension)
        updateActiveFileContent(formatted)
        _uiState.update { it.copy(statusMessage = "Formatted ${activeFile.name}", showFormatModal = false) }
    }

    fun combineWorkspaceFiles() {
        val current = _uiState.value
        val combinedHtml = CodeFormatter.combineFiles(current.files)
        val combinedFile = ProjectFile(
            name = "bundle.html",
            path = "bundle.html",
            content = combinedHtml,
            isFolder = false
        )
        _uiState.update { curr ->
            val filtered = curr.files.filter { it.name != "bundle.html" } + combinedFile
            val tabs = curr.openTabs.filter { it.name != "bundle.html" } + combinedFile
            curr.copy(
                files = filtered,
                openTabs = tabs,
                activeFileId = combinedFile.id,
                showFormatModal = false,
                statusMessage = "Combined workspace into unified bundle.html"
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveProject(_uiState.value.projectName, _uiState.value.files)
        }
    }

    /**
     * Requirement: Clicking "Split Code" extracts single-file HTML (with embedded <style> and <script>)
     * into distinct index.html, styles.css, and script.js files with proper references.
     */
    fun splitActiveHtml() {
        val current = _uiState.value
        val activeFile = current.files.firstOrNull { it.id == current.activeFileId }
            ?: current.files.firstOrNull { it.extension == "html" || it.extension == "htm" }
            ?: return

        val splitFiles = CodeFormatter.splitHtml(activeFile.content)
        undoStacks.clear()
        redoStacks.clear()
        splitFiles.forEach { f ->
            undoStacks[f.id] = ArrayDeque(listOf(f.content))
            redoStacks[f.id] = ArrayDeque()
        }

        val primaryFile = splitFiles.firstOrNull { it.name == "index.html" } ?: splitFiles.first()

        _uiState.update { curr ->
            curr.copy(
                files = splitFiles,
                openTabs = splitFiles,
                activeFileId = primaryFile.id,
                showFormatModal = false,
                statusMessage = "Split into index.html, styles.css, script.js"
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveProject(_uiState.value.projectName, splitFiles)
        }
    }

    fun importZipBytes(zipBytes: ByteArray) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val extractedFiles = ZipUtils.extractArchive(zipBytes)
                if (extractedFiles.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        importFiles(extractedFiles)
                        _uiState.update { it.copy(statusMessage = "Unpacked ${extractedFiles.size} files from archive") }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        _uiState.update { it.copy(statusMessage = "No valid code or text files found in archive") }
                    }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "Error importing zip bytes", e)
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(statusMessage = "Failed unpacking archive: ${e.message}") }
                }
            }
        }
    }

    // Export & Download System to Teezron Code folder
    fun exportCurrentFile(context: Context) {
        val current = _uiState.value
        val activeFile = current.files.firstOrNull { it.id == current.activeFileId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val msg = FileExportUtils.saveSingleFile(context, activeFile.name, activeFile.content)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(statusMessage = msg, showDownloadModal = false) }
            }
        }
    }

    fun exportCombinedHtml(context: Context) {
        val current = _uiState.value
        val bundledHtml = getBundledHtml()
        val fileName = "${current.projectName}.html"
        viewModelScope.launch(Dispatchers.IO) {
            val msg = FileExportUtils.saveSingleFile(context, fileName, bundledHtml)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(statusMessage = msg, showDownloadModal = false) }
            }
        }
    }

    fun exportWorkspaceZip(context: Context) {
        val current = _uiState.value
        val zipName = "${current.projectName}.zip"
        viewModelScope.launch(Dispatchers.IO) {
            val msg = FileExportUtils.saveZipArchive(context, zipName, current.files)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(statusMessage = msg, showDownloadModal = false) }
            }
        }
    }

    // Interactive Visual Design Staging
    fun selectVisualElement(id: String?) {
        _uiState.update { it.copy(selectedElementId = id) }
    }

    fun updateVisualElement(updated: DOMElementNode) {
        _uiState.update { current ->
            val list = current.visualElements.map { if (it.id == updated.id) updated else it }
            current.copy(visualElements = list, hasUnsavedVisualChanges = true)
        }
    }

    fun addVisualElement(newElem: DOMElementNode) {
        _uiState.update { current ->
            val list = current.visualElements + newElem
            current.copy(
                visualElements = list,
                selectedElementId = newElem.id,
                hasUnsavedVisualChanges = true
            )
        }
    }

    fun deleteVisualElement(id: String) {
        _uiState.update { current ->
            val list = current.visualElements.filter { it.id != id }
            current.copy(
                visualElements = list,
                selectedElementId = if (current.selectedElementId == id) null else current.selectedElementId,
                hasUnsavedVisualChanges = true
            )
        }
    }

    fun commitVisualChangesToCode() {
        val current = _uiState.value
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n")
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
        sb.append("  <title>T•ZERON App</title>\n")
        sb.append("  <style>\n")
        sb.append("    body { margin: 0; background: #0D0D0F; color: #ECECED; font-family: system-ui, sans-serif; position: relative; width: 100vw; height: 100vh; overflow: hidden; }\n")
        sb.append("  </style>\n</head>\n<body>\n")

        for (elem in current.visualElements) {
            val styleAttrs = elem.styles.entries.joinToString("; ") { "${it.key}: ${it.value}" }
            val inlineStyle = "position: absolute; left: ${elem.x}px; top: ${elem.y}px; width: ${elem.width}px; height: ${elem.height}px; border-radius: ${elem.borderRadius}px; opacity: ${elem.opacity}; transform: rotate(${elem.rotation}deg); $styleAttrs"
            sb.append("  <${elem.tagName} style=\"$inlineStyle\">${elem.textContent}</${elem.tagName}>\n")
        }

        sb.append("</body>\n</html>")

        val generatedHtml = sb.toString()
        val activeHtml = current.files.firstOrNull { it.extension == "html" || it.extension == "htm" }

        if (activeHtml != null) {
            val activeId = activeHtml.id
            val updatedFiles = current.files.map { if (it.id == activeId) it.copy(content = generatedHtml, isModified = true) else it }
            val updatedTabs = current.openTabs.map { if (it.id == activeId) it.copy(content = generatedHtml, isModified = true) else it }
            _uiState.update { it.copy(files = updatedFiles, openTabs = updatedTabs, hasUnsavedVisualChanges = false, statusMessage = "Saved visual changes to index.html") }
        } else {
            addNewFile("index.html")
            updateActiveFileContent(generatedHtml)
        }
    }

    fun discardVisualChanges() {
        syncVisualElementsFromActiveFile()
        _uiState.update {
            it.copy(
                hasUnsavedVisualChanges = false,
                statusMessage = "Discarded visual changes"
            )
        }
    }

    private fun syncVisualElementsFromActiveFile() {
        val current = _uiState.value
        val htmlFile = current.files.firstOrNull { it.extension == "html" || it.extension == "htm" }
        val content = htmlFile?.content ?: ""

        if (current.visualElements.isEmpty() && content.isNotBlank()) {
            val parsedElements = mutableListOf<DOMElementNode>()
            var yOffset = 40f

            Regex("""<h[1-6][^>]*>(.*?)</h[1-6]>""", RegexOption.IGNORE_CASE).findAll(content).forEach { m ->
                val cleanText = m.groupValues[1].replace(Regex("<.*?>"), "").trim()
                if (cleanText.isNotEmpty()) {
                    parsedElements.add(
                        DOMElementNode(
                            id = "h_${System.currentTimeMillis()}_${parsedElements.size}",
                            tagName = "h1",
                            textContent = cleanText,
                            x = 24f,
                            y = yOffset,
                            width = 272f,
                            height = 46f,
                            borderRadius = 8f,
                            styles = mapOf(
                                "color" to "#FFFFFF",
                                "font-size" to "22px",
                                "font-weight" to "bold",
                                "text-align" to "center"
                            )
                        )
                    )
                    yOffset += 56f
                }
            }

            Regex("""<p[^>]*>(.*?)</p>""", RegexOption.IGNORE_CASE).findAll(content).forEach { m ->
                val cleanText = m.groupValues[1].replace(Regex("<.*?>"), "").trim()
                if (cleanText.isNotEmpty()) {
                    parsedElements.add(
                        DOMElementNode(
                            id = "p_${System.currentTimeMillis()}_${parsedElements.size}",
                            tagName = "p",
                            textContent = cleanText,
                            x = 24f,
                            y = yOffset,
                            width = 272f,
                            height = 54f,
                            borderRadius = 6f,
                            styles = mapOf(
                                "color" to "#8E8D99",
                                "font-size" to "12px",
                                "text-align" to "center"
                            )
                        )
                    )
                    yOffset += 64f
                }
            }

            Regex("""<button[^>]*>(.*?)</button>""", RegexOption.IGNORE_CASE).findAll(content).forEach { m ->
                val cleanText = m.groupValues[1].replace(Regex("<.*?>"), "").trim()
                if (cleanText.isNotEmpty()) {
                    parsedElements.add(
                        DOMElementNode(
                            id = "btn_${System.currentTimeMillis()}_${parsedElements.size}",
                            tagName = "button",
                            textContent = cleanText,
                            x = 40f,
                            y = yOffset,
                            width = 240f,
                            height = 42f,
                            borderRadius = 10f,
                            styles = mapOf(
                                "background" to "#007ACC",
                                "color" to "#FFFFFF",
                                "font-size" to "13px",
                                "font-weight" to "bold",
                                "text-align" to "center"
                            )
                        )
                    )
                    yOffset += 54f
                }
            }

            if (parsedElements.isNotEmpty()) {
                _uiState.update { it.copy(visualElements = parsedElements, hasUnsavedVisualChanges = false) }
            }
        }
    }

    // Console Logging
    fun addConsoleLog(level: LogLevel, message: String) {
        val item = ConsoleLogItem(level = level, message = message)
        _uiState.update { it.copy(consoleLogs = it.consoleLogs + item) }
    }

    fun clearConsoleLogs() {
        _uiState.update { it.copy(consoleLogs = emptyList()) }
    }

    // Local / Public Background Hosting
    fun toggleTunnel(context: Context) {
        val isCurrentlyActive = _uiState.value.isTunnelActive
        if (!isCurrentlyActive) {
            val port = httpServer.start(viewModelScope)
            val ip = LocalHttpServer.getLocalIpAddress(context)
            val liveUrl = "http://$ip:$port"
            _uiState.update {
                it.copy(
                    isTunnelActive = true,
                    tunnelUrl = liveUrl,
                    statusMessage = "Live hosting active: $liveUrl"
                )
            }
        } else {
            httpServer.stop()
            _uiState.update {
                it.copy(
                    isTunnelActive = false,
                    statusMessage = "Live hosting stopped"
                )
            }
        }
    }

    fun ensureHttpServerStarted(context: Context): String {
        val port = httpServer.start(viewModelScope)
        val ip = LocalHttpServer.getLocalIpAddress(context)
        val liveUrl = "http://$ip:$port"
        _uiState.update {
            it.copy(
                isTunnelActive = true,
                tunnelUrl = liveUrl
            )
        }
        return liveUrl
    }

    // Modal Visibility Controls
    fun setModalState(
        newProject: Boolean = false,
        open: Boolean = false,
        format: Boolean = false,
        download: Boolean = false,
        tunnel: Boolean = false,
        syntaxTheme: Boolean = false
    ) {
        _uiState.update {
            it.copy(
                showNewProjectModal = newProject,
                showOpenModal = open,
                showFormatModal = format,
                showDownloadModal = download,
                showTunnelModal = tunnel,
                showSyntaxThemeModal = syntaxTheme
            )
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        httpServer.stop()
    }
}
