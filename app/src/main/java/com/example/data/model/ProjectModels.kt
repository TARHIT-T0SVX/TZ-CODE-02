package com.example.data.model

data class ProjectFile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val path: String, // e.g. "index.html", "styles.css", "script.js"
    val content: String = "",
    val isFolder: Boolean = false,
    val isModified: Boolean = false
) {
    val extension: String
        get() = if (name.contains(".")) name.substringAfterLast(".").lowercase() else ""
}

data class DOMElementNode(
    val id: String,
    val tagName: String,
    val elementId: String = "",
    val classNames: String = "",
    val textContent: String = "",
    val innerHtml: String = "",
    val x: Float = 50f,
    val y: Float = 50f,
    val width: Float = 200f,
    val height: Float = 100f,
    val rotation: Float = 0f,
    val opacity: Float = 1.0f,
    val borderRadius: Float = 8f,
    val animationPreset: String = "none",
    val fontFamily: String = "system-ui",
    val lineHeight: Float = 1.4f,
    val letterSpacing: Float = 0f,
    val styles: Map<String, String> = emptyMap(),
    val attributes: Map<String, String> = emptyMap(),
    val isLocked: Boolean = false,
    val isVisible: Boolean = true
)

enum class ViewportMode(val title: String, val widthDp: Int, val heightDp: Int) {
    MOBILE("Mobile (390×844)", 390, 844),
    TABLET("Tablet (768×1024)", 768, 1024),
    DESKTOP("Desktop (1280×800)", 1280, 800),
    FULLSCREEN("Fluid Responsive", -1, -1)
}

enum class PreviewSubMode {
    NORMAL,
    INTERACTIVE_EDITOR
}

enum class SyntaxTheme(val displayName: String) {
    VS_CODE_DARK("VS Code Dark"),
    ONE_DARK_PRO("One Dark Pro"),
    MONOKAI_PRO("Monokai Pro"),
    DRACULA("Dracula")
}

enum class LogLevel {
    INFO,
    LOG,
    WARN,
    ERROR
}

data class ConsoleLogItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val level: LogLevel = LogLevel.LOG,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
)

