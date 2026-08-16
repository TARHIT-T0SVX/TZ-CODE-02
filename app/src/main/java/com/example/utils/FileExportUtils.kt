package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.data.model.ProjectFile
import java.io.File
import java.io.FileOutputStream

object FileExportUtils {

    private const val DIRECTORY_NAME = "Teezron Code"

    fun saveSingleFile(context: Context, fileName: String, content: String): String {
        return try {
            val bytes = content.toByteArray(Charsets.UTF_8)
            val mimeType = when {
                fileName.endsWith(".html", ignoreCase = true) -> "text/html"
                fileName.endsWith(".css", ignoreCase = true) -> "text/css"
                fileName.endsWith(".js", ignoreCase = true) -> "application/javascript"
                fileName.endsWith(".json", ignoreCase = true) -> "application/json"
                else -> "text/plain"
            }
            saveBytesToDownloads(context, fileName, bytes, mimeType)
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message}"
        }
    }

    fun saveZipArchive(context: Context, zipName: String, files: List<ProjectFile>): String {
        return try {
            val zipBytes = ZipUtils.createZipArchive(files)
            val cleanName = if (zipName.endsWith(".zip", ignoreCase = true)) zipName else "$zipName.zip"
            saveBytesToDownloads(context, cleanName, zipBytes, "application/zip")
        } catch (e: Exception) {
            e.printStackTrace()
            "Error: ${e.message}"
        }
    }

    private fun saveBytesToDownloads(context: Context, fileName: String, bytes: ByteArray, mimeType: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$DIRECTORY_NAME")
            }

            val uri: Uri? = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outStream ->
                    outStream.write(bytes)
                    outStream.flush()
                }
                return "Saved to Downloads/$DIRECTORY_NAME/$fileName"
            }
        }

        // Fallback for legacy Android or filesystem access
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(downloadsDir, DIRECTORY_NAME)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        val targetFile = File(targetDir, fileName)
        FileOutputStream(targetFile).use { fos ->
            fos.write(bytes)
            fos.flush()
        }
        return "Saved to Downloads/$DIRECTORY_NAME/$fileName"
    }
}
