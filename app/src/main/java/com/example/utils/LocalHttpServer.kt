package com.example.utils

import android.content.Context
import android.net.wifi.WifiManager
import android.text.format.Formatter
import com.example.data.model.ProjectFile
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

class LocalHttpServer(
    private val getFiles: () -> List<ProjectFile>,
    private val getBundledHtml: () -> String
) {
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    var serverPort: Int = 8080
        private set
    var isRunning: Boolean = false
        private set

    fun start(scope: CoroutineScope, preferredPort: Int = 8080): Int {
        if (isRunning) return serverPort

        var port = preferredPort
        var bound = false
        while (port < preferredPort + 20 && !bound) {
            try {
                serverSocket = ServerSocket(port)
                bound = true
                serverPort = port
            } catch (_: Exception) {
                port++
            }
        }

        if (!bound) {
            serverSocket = ServerSocket(0)
            serverPort = serverSocket?.localPort ?: 8080
        }

        isRunning = true

        serverJob = scope.launch(Dispatchers.IO) {
            while (isActive && isRunning) {
                try {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                } catch (e: SocketException) {
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        return serverPort
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        serverJob = null
    }

    private fun handleClient(socket: Socket) {
        socket.use { s ->
            try {
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return

                val rawPath = parts[1].substringBefore("?")
                val path = if (rawPath == "/" || rawPath.isEmpty()) "index.html" else rawPath.removePrefix("/")

                val files = getFiles()
                val targetFile = files.firstOrNull { it.path.equals(path, ignoreCase = true) || it.name.equals(path, ignoreCase = true) }

                val (bodyBytes, contentType) = when {
                    rawPath == "/" || rawPath == "/index.html" -> {
                        val bundled = getBundledHtml()
                        Pair(bundled.toByteArray(Charsets.UTF_8), "text/html; charset=utf-8")
                    }
                    targetFile != null -> {
                        val cType = when (targetFile.extension.lowercase()) {
                            "html", "htm" -> "text/html; charset=utf-8"
                            "css" -> "text/css; charset=utf-8"
                            "js", "ts" -> "application/javascript; charset=utf-8"
                            "json" -> "application/json; charset=utf-8"
                            "svg" -> "image/svg+xml"
                            "txt" -> "text/plain; charset=utf-8"
                            else -> "text/plain; charset=utf-8"
                        }
                        Pair(targetFile.content.toByteArray(Charsets.UTF_8), cType)
                    }
                    else -> {
                        val notFound = "<html><body style='background:#0D0D0F;color:#fff;'><h1>404 Not Found</h1></body></html>"
                        Pair(notFound.toByteArray(Charsets.UTF_8), "text/html; charset=utf-8")
                    }
                }

                val out: OutputStream = s.getOutputStream()
                val responseHeader = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: $contentType\r\n")
                    append("Content-Length: ${bodyBytes.size}\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(responseHeader.toByteArray(Charsets.UTF_8))
                out.write(bodyBytes)
                out.flush()
            } catch (_: Exception) {}
        }
    }

    companion object {
        fun getLocalIpAddress(context: Context): String {
            try {
                // Try getting Wi-Fi IP
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                if (wifiManager != null && wifiManager.isWifiEnabled) {
                    @Suppress("DEPRECATION")
                    val ip = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
                    if (ip.isNotEmpty() && ip != "0.0.0.0") return ip
                }

                // Fallback to Network Interfaces
                val interfaces = NetworkInterface.getNetworkInterfaces()
                while (interfaces.hasMoreElements()) {
                    val networkInterface = interfaces.nextElement()
                    val addresses = networkInterface.inetAddresses
                    while (addresses.hasMoreElements()) {
                        val address = addresses.nextElement()
                        if (!address.isLoopbackAddress && address is Inet4Address) {
                            return address.hostAddress ?: "127.0.0.1"
                        }
                    }
                }
            } catch (_: Exception) {}
            return "127.0.0.1"
        }
    }
}
