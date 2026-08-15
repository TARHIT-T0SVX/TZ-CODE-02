package com.example.utils

import com.example.data.model.ProjectFile

object CodeFormatter {

    fun formatCode(content: String, extension: String): String {
        return when (extension.lowercase()) {
            "html", "htm" -> formatHtml(content)
            "css" -> formatCss(content)
            "js", "ts", "json" -> formatJs(content)
            else -> content.trim()
        }
    }

    fun formatHtml(html: String): String {
        if (html.isBlank()) return ""
        val sb = StringBuilder()
        var indent = 0
        val tokens = html.replace(">", ">\n").replace("<", "\n<").lines()

        for (rawLine in tokens) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            if (line.startsWith("</") || line.startsWith("-->")) {
                indent = maxOf(0, indent - 1)
            }

            sb.append("  ".repeat(indent)).append(line).append("\n")

            if (line.startsWith("<") &&
                !line.startsWith("</") &&
                !line.startsWith("<!") &&
                !line.startsWith("<?") &&
                !line.endsWith("/>") &&
                !isVoidTag(line)
            ) {
                indent++
            }
        }
        return sb.toString().trimEnd()
    }

    private fun isVoidTag(line: String): Boolean {
        val voidTags = setOf("area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "param", "source", "track", "wbr")
        val match = Regex("""^<([a-zA-Z0-9]+)""").find(line)
        val tag = match?.groupValues?.get(1)?.lowercase() ?: return false
        return voidTags.contains(tag)
    }

    fun formatCss(css: String): String {
        if (css.isBlank()) return ""
        val clean = css.replace(Regex("""\s*\{\s*"""), " {\n  ")
            .replace(Regex("""\s*;\s*"""), ";\n  ")
            .replace(Regex("""\s*\}\s*"""), "\n}\n\n")
            .replace(Regex("""\s*:\s*"""), ": ")
        return clean.lines().joinToString("\n") { it.trimEnd() }.trim()
    }

    fun formatJs(js: String): String {
        if (js.isBlank()) return ""
        val lines = js.lines()
        val sb = StringBuilder()
        var indent = 0
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                sb.append("\n")
                continue
            }
            if (line.startsWith("}") || line.startsWith("]") || line.startsWith(")")) {
                indent = maxOf(0, indent - 1)
            }
            sb.append("  ".repeat(indent)).append(line).append("\n")
            val opens = line.count { it == '{' || it == '[' }
            val closes = line.count { it == '}' || it == ']' }
            indent = maxOf(0, indent + (opens - closes))
        }
        return sb.toString().trimEnd()
    }

    /**
     * Combines multiple files (HTML + external CSS + external JS) into a single standalone HTML document.
     */
    fun combineFiles(files: List<ProjectFile>): String {
        val htmlFile = files.firstOrNull { it.extension == "html" || it.extension == "htm" }
        val rawHtml = htmlFile?.content ?: "<!DOCTYPE html>\n<html>\n<head>\n</head>\n<body>\n</body>\n</html>"

        val cssFiles = files.filter { it.extension == "css" }
        val jsFiles = files.filter { it.extension == "js" || it.extension == "ts" }

        val combinedCss = cssFiles.joinToString("\n\n") { "/* ${it.name} */\n" + it.content }
        val combinedJs = jsFiles.joinToString("\n\n") { "// ${it.name}\n" + it.content }

        var result = rawHtml

        // Remove existing <link rel="stylesheet"> that reference local files
        result = result.replace(Regex("""<link\s+[^>]*rel=["']stylesheet["'][^>]*>""", RegexOption.IGNORE_CASE), "")
        // Remove existing local <script src="..."> tags
        result = result.replace(Regex("""<script\s+[^>]*src=["'](?!http)[^"']+["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE), "")

        // Inject <style> inside <head> or at top
        if (combinedCss.isNotBlank()) {
            val styleBlock = "\n<style>\n$combinedCss\n</style>\n"
            result = if (result.contains("</head>", ignoreCase = true)) {
                result.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$styleBlock</head>")
            } else {
                styleBlock + result
            }
        }

        // Inject <script> before </body> or at bottom
        if (combinedJs.isNotBlank()) {
            val scriptBlock = "\n<script>\n$combinedJs\n</script>\n"
            result = if (result.contains("</body>", ignoreCase = true)) {
                result.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$scriptBlock</body>")
            } else {
                result + scriptBlock
            }
        }

        return formatHtml(result)
    }

    /**
     * Splits a monolithic HTML file with inline <style> and <script> into separate files.
     */
    fun splitHtml(htmlContent: String): List<ProjectFile> {
        val resultFiles = mutableListOf<ProjectFile>()

        val styleRegex = Regex("""<style[^>]*>([\s\S]*?)</style>""", RegexOption.IGNORE_CASE)
        val scriptRegex = Regex("""<script(?![^>]*src=)[^>]*>([\s\S]*?)</script>""", RegexOption.IGNORE_CASE)

        val extractedStyles = mutableListOf<String>()
        var cleanedHtml = styleRegex.replace(htmlContent) { matchResult ->
            extractedStyles.add(matchResult.groupValues[1].trim())
            ""
        }

        val extractedScripts = mutableListOf<String>()
        cleanedHtml = scriptRegex.replace(cleanedHtml) { matchResult ->
            extractedScripts.add(matchResult.groupValues[1].trim())
            ""
        }

        val cssContent = extractedStyles.joinToString("\n\n")
        val jsContent = extractedScripts.joinToString("\n\n")

        // Add link and script tags to cleaned HTML if we extracted any styles/scripts
        if (cssContent.isNotBlank()) {
            val linkTag = "\n  <link rel=\"stylesheet\" href=\"style.css\">\n"
            cleanedHtml = if (cleanedHtml.contains("</head>", ignoreCase = true)) {
                cleanedHtml.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$linkTag</head>")
            } else {
                linkTag + cleanedHtml
            }
        }

        if (jsContent.isNotBlank()) {
            val scriptTag = "\n  <script src=\"script.js\"></script>\n"
            cleanedHtml = if (cleanedHtml.contains("</body>", ignoreCase = true)) {
                cleanedHtml.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$scriptTag</body>")
            } else {
                cleanedHtml + scriptTag
            }
        }

        resultFiles.add(ProjectFile(name = "index.html", path = "index.html", content = formatHtml(cleanedHtml)))

        if (cssContent.isNotBlank()) {
            resultFiles.add(ProjectFile(name = "style.css", path = "style.css", content = formatCss(cssContent)))
        }
        if (jsContent.isNotBlank()) {
            resultFiles.add(ProjectFile(name = "script.js", path = "script.js", content = formatJs(jsContent)))
        }

        return resultFiles
    }
}
