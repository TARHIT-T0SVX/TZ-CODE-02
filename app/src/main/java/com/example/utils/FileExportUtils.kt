package com.example.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import com.example.data.model.ProjectFile
import java.io.File
import java.io.FileOutputStream

object FileExportUtils {

    private const val TAG = "FileExportUtils"
    private const val DIRECTORY_NAME = "TZeronCode"

    fun getFileProviderAuthority(context: Context): String {
        return "${context.packageName}.fileprovider"
    }

    /**
     * Creates a secure content URI using FileProvider for sharing or external opening.
     */
    fun createSecureFileUri(context: Context, file: File): Uri? {
        return try {
            FileProvider.getUriForFile(
                context,
                getFileProviderAuthority(context),
                file
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create secure FileProvider URI for file: ${file.absolutePath}", e)
            null
        }
    }

    /**
     * Creates a temporary secure file in cache and returns its FileProvider content URI with read permissions.
     */
    fun writeToSecureCache(context: Context, fileName: String, content: String): Uri? {
        return try {
            val safeName = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val cacheFile = File(context.cacheDir, safeName)
            cacheFile.writeText(content, Charsets.UTF_8)
            createSecureFileUri(context, cacheFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing to secure cache: $fileName", e)
            null
        }
    }

    /**
     * Creates a temporary secure zip in cache and returns its FileProvider content URI.
     */
    fun writeZipToSecureCache(context: Context, zipName: String, files: List<ProjectFile>): Uri? {
        return try {
            val safeName = if (zipName.endsWith(".zip", ignoreCase = true)) {
                zipName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            } else {
                "${zipName.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.zip"
            }
            val cacheFile = File(context.cacheDir, safeName)
            val zipBytes = ZipUtils.createZipArchive(files)
            cacheFile.writeBytes(zipBytes)
            createSecureFileUri(context, cacheFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed writing zip to secure cache: $zipName", e)
            null
        }
    }

    /**
     * Share a file or zip securely with external apps using FileProvider and explicit URI grants.
     */
    fun shareFileSecurely(context: Context, uri: Uri, mimeType: String, chooserTitle: String = "Share File") {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (e: Exception) {
            Log.e(TAG, "Error initiating secure share intent", e)
        }
    }

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
            Log.e(TAG, "Error saving single file $fileName", e)
            "Error: ${e.message}"
        }
    }

    fun saveZipArchive(context: Context, zipName: String, files: List<ProjectFile>): String {
        return try {
            val zipBytes = ZipUtils.createZipArchive(files)
            val cleanName = if (zipName.endsWith(".zip", ignoreCase = true)) zipName else "$zipName.zip"
            saveBytesToDownloads(context, cleanName, zipBytes, "application/zip")
        } catch (e: Exception) {
            Log.e(TAG, "Error saving zip archive $zipName", e)
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

        // Fallback for app-specific external storage or legacy downloads
        return try {
            val targetDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir, DIRECTORY_NAME)
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, fileName)
            FileOutputStream(targetFile).use { fos ->
                fos.write(bytes)
                fos.flush()
            }
            "Saved to App Storage/$DIRECTORY_NAME/$fileName"
        } catch (e: Exception) {
            Log.e(TAG, "Failed fallback saving to files directory", e)
            "Error saving file: ${e.message}"
        }
    }
}
