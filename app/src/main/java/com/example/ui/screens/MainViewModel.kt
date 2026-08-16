package com.example.ui.screens

import android.app.Application
import android.content.Context
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
                val savedProjects = repository.allProjects.firstOrNull()
                val latestProject = savedProjects?.firstOrNull()
                if (latestProject != null) {
                    val files = repository.deserializeFiles(latestProject.filesJson)
                    if (files.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            restoreWorkspace(latestProject.name, files)
                        }
                        return@launch
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
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

    fun importFiles(newFiles: List<ProjectFile>) {
        if (newFiles.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            newFiles.forEach { file ->
                undoStacks[file.id] = ArrayDeque(listOf(file.content))
                redoStacks[file.id] = ArrayDeque()
            }
            withContext(Dispatchers.Main) {
                _uiState.update { current ->
                    val updated = current.files + newFiles.filter { nf -> current.files.none { it.path == nf.path } }
                    val tabs = current.openTabs + newFiles.take(3).filter { nf -> current.openTabs.none { it.path == nf.path } }
                    val firstId = newFiles.firstOrNull()?.id ?: current.activeFileId
                    current.copy(
                        files = updated,
                        openTabs = tabs,
                        activeFileId = firstId,
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
     * Resolves and bundles multi-file project (index.html, styles.css, script.js, svg assets) into standalone preview HTML
     */
    fun getBundledHtml(): String {
        val current = _uiState.value
        val htmlFile = current.files.firstOrNull { it.extension == "html" || it.extension == "htm" }
            ?: current.files.firstOrNull()

        // Requirement: The preview panel must start completely blank with no initial rendered content.
        if (htmlFile == null || (htmlFile.content.isBlank() && current.files.all { it.content.isBlank() })) {
            return "<!DOCTYPE html><html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>body { margin: 0; background: #0D0D0F; color: #888; font-family: monospace; }</style></head><body></body></html>"
        }

        var html = htmlFile.content

        // Inline external CSS links that match project files
        current.files.filter { it.extension == "css" && it.content.isNotBlank() }.forEach { cssFile ->
            val linkRegex = Regex("""<link[^>]*href=["'](?:\./)?${Regex.escape(cssFile.name)}["'][^>]*>""", RegexOption.IGNORE_CASE)
            val styleTag = "<style>\n/* Inlined from ${cssFile.name} */\n${cssFile.content}\n</style>"
            if (linkRegex.containsMatchIn(html)) {
                html = html.replace(linkRegex, styleTag)
            } else if (!html.contains(cssFile.content) && cssFile.name == "styles.css") {
                html = if (html.contains("</head>", ignoreCase = true)) {
                    html.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$styleTag\n</head>")
                } else {
                    "$styleTag\n$html"
                }
            }
        }

        // Inline external JS scripts that match project files
        current.files.filter { (it.extension == "js" || it.extension == "ts") && it.content.isNotBlank() }.forEach { jsFile ->
            val scriptRegex = Regex("""<script[^>]*src=["'](?:\./)?${Regex.escape(jsFile.name)}["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE)
            val scriptTag = "<script>\n// Inlined from ${jsFile.name}\n${jsFile.content}\n</script>"
            if (scriptRegex.containsMatchIn(html)) {
                html = html.replace(scriptRegex, scriptTag)
            } else if (!html.contains(jsFile.content) && jsFile.name == "script.js") {
                html = if (html.contains("</body>", ignoreCase = true)) {
                    html.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$scriptTag\n</body>")
                } else {
                    "$html\n$scriptTag"
                }
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
            val extractedFiles = ZipUtils.extractArchive(zipBytes)
            if (extractedFiles.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    importFiles(extractedFiles)
                    _uiState.update { it.copy(statusMessage = "Unpacked ${extractedFiles.size} files from archive") }
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
