package com.example.utils

import android.util.Log
import com.example.data.model.ProjectFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipUtils {
    private const val TAG = "ZipUtils"
    private const val MAX_SINGLE_FILE_SIZE = 15 * 1024 * 1024 // 15 MB safety limit per file
    private const val MAX_TOTAL_FILES = 250 // Safety limit on total entries unpacked

    fun createZipArchive(files: List<ProjectFile>): ByteArray {
        val baos = ByteArrayOutputStream()
        try {
            ZipOutputStream(baos).use { zos ->
                for (file in files) {
                    if (file.isFolder) continue
                    val safePath = sanitizePath(if (file.path.isNotBlank()) file.path else file.name)
                    val entry = ZipEntry(safePath)
                    zos.putNextEntry(entry)
                    zos.write(file.content.toByteArray(Charsets.UTF_8))
                    zos.closeEntry()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error creating zip archive", e)
        }
        return baos.toByteArray()
    }

    fun extractArchiveFromStream(inputStream: InputStream, archiveName: String = "archive.zip"): List<ProjectFile> {
        val result = mutableListOf<ProjectFile>()
        val bytes = try {
            inputStream.readBytes()
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading input stream for $archiveName", e)
            return emptyList()
        }
        return extractArchive(bytes, archiveName)
    }

    fun extractArchive(archiveBytes: ByteArray, archiveName: String = "archive.zip"): List<ProjectFile> {
        if (archiveBytes.isEmpty()) {
            Log.w(TAG, "Archive bytes are empty for $archiveName")
            return emptyList()
        }

        val result = mutableListOf<ProjectFile>()

        // Try extraction with standard UTF-8 charset
        var success = tryExtractWithCharset(archiveBytes, Charsets.UTF_8, result)

        // If UTF-8 failed due to malformed charset or failed header, try ISO-8859-1 fallback
        if (!success || result.isEmpty()) {
            val fallbackCharset = Charset.forName("ISO-8859-1")
            val fallbackResult = mutableListOf<ProjectFile>()
            val fallbackSuccess = tryExtractWithCharset(archiveBytes, fallbackCharset, fallbackResult)
            if (fallbackSuccess && fallbackResult.isNotEmpty()) {
                result.clear()
                result.addAll(fallbackResult)
                success = true
            }
        }

        // Final fallback: if file was not a standard zip (e.g. plain text / code uploaded as zip)
        if (result.isEmpty()) {
            try {
                val textContent = String(archiveBytes, Charsets.UTF_8)
                val cleanName = if (archiveName.contains(".")) {
                    archiveName.substringBeforeLast(".") + ".txt"
                } else {
                    "imported_data.txt"
                }
                result.add(
                    ProjectFile(
                        name = cleanName,
                        path = cleanName,
                        content = textContent,
                        isFolder = false
                    )
                )
                Log.i(TAG, "Fallback unpacked plain-text file: $cleanName")
            } catch (e: Exception) {
                Log.e(TAG, "Complete extraction failure on $archiveName", e)
            }
        }

        return result
    }

    private fun tryExtractWithCharset(
        archiveBytes: ByteArray,
        charset: Charset,
        outList: MutableList<ProjectFile>
    ): Boolean {
        return try {
            val bais = ByteArrayInputStream(archiveBytes)
            ZipInputStream(bais, charset).use { zis ->
                var entry: ZipEntry? = null
                var entryCount = 0

                while (true) {
                    try {
                        entry = zis.nextEntry
                    } catch (e: Exception) {
                        Log.w(TAG, "Malformed entry name or stream read error with charset ${charset.name()}: ${e.message}")
                        break
                    }
                    if (entry == null) break
                    if (++entryCount > MAX_TOTAL_FILES) {
                        Log.w(TAG, "Reached maximum allowed files in archive ($MAX_TOTAL_FILES)")
                        break
                    }

                    val rawName = entry.name
                    val isHiddenOrMac = rawName.startsWith("__MACOSX") ||
                            rawName.startsWith(".") ||
                            rawName.contains("/.") ||
                            rawName.contains("__MACOSX/")

                    if (!entry.isDirectory && !isHiddenOrMac) {
                        val safePath = sanitizePath(rawName)
                        val fileName = if (safePath.contains("/")) safePath.substringAfterLast("/") else safePath

                        val baos = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        var count: Int
                        var totalRead = 0L

                        while (zis.read(buffer).also { count = it } != -1) {
                            baos.write(buffer, 0, count)
                            totalRead += count
                            if (totalRead > MAX_SINGLE_FILE_SIZE) {
                                Log.w(TAG, "File $safePath exceeded maximum size limit")
                                break
                            }
                        }

                        val content = try {
                            baos.toString(Charsets.UTF_8.name())
                        } catch (_: Exception) {
                            baos.toString(charset.name())
                        }

                        outList.add(
                            ProjectFile(
                                name = fileName,
                                path = safePath,
                                content = content,
                                isFolder = false
                            )
                        )
                    }

                    try {
                        zis.closeEntry()
                    } catch (_: Exception) {}
                }
            }
            true
        } catch (e: Throwable) {
            Log.w(TAG, "Zip extraction failed with charset ${charset.name()}: ${e.message}")
            false
        }
    }

    private fun sanitizePath(path: String): String {
        return path
            .replace("\\", "/")
            .replace("../", "")
            .replace("./", "")
            .trimStart('/')
            .ifEmpty { "file_${System.currentTimeMillis()}" }
    }

    fun extractZipArchive(zipBytes: ByteArray): List<ProjectFile> {
        return extractArchive(zipBytes, "project.zip")
    }
}


