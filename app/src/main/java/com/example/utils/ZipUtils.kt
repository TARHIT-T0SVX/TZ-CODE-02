package com.example.utils

import com.example.data.model.ProjectFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {

    fun createZipArchive(files: List<ProjectFile>): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            for (file in files) {
                if (file.isFolder) continue
                val entry = ZipEntry(file.path.ifEmpty { file.name })
                zos.putNextEntry(entry)
                zos.write(file.content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    fun extractArchive(archiveBytes: ByteArray, archiveName: String = "archive.zip"): List<ProjectFile> {
        val result = mutableListOf<ProjectFile>()
        try {
            val bais = ByteArrayInputStream(archiveBytes)
            ZipInputStream(bais).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryName = entry.name
                    if (!entry.isDirectory && !entryName.startsWith("__MACOSX") && !entryName.startsWith(".")) {
                        val baos = ByteArrayOutputStream()
                        val buffer = ByteArray(2048)
                        var count: Int
                        while (zis.read(buffer).also { count = it } != -1) {
                            baos.write(buffer, 0, count)
                        }
                        val content = baos.toString(Charsets.UTF_8.name())
                        val fileName = if (entryName.contains("/")) entryName.substringAfterLast("/") else entryName
                        result.add(
                            ProjectFile(
                                name = fileName,
                                path = entryName,
                                content = content,
                                isFolder = false
                            )
                        )
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (e: Exception) {
            // Fallback for raw text file or single compressed file
            if (result.isEmpty()) {
                val fallbackText = try {
                    String(archiveBytes, Charsets.UTF_8)
                } catch (ex: Exception) {
                    "// Extracted binary or uncompressed data\n"
                }
                val cleanName = archiveName.substringBeforeLast(".") + ".txt"
                result.add(
                    ProjectFile(
                        name = cleanName,
                        path = cleanName,
                        content = fallbackText
                    )
                )
            }
        }
        return result
    }

    fun extractZipArchive(zipBytes: ByteArray): List<ProjectFile> {
        return extractArchive(zipBytes, "project.zip")
    }
}

