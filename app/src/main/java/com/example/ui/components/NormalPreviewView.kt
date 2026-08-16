package com.example.ui.components

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.*
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.ConsoleLogItem
import com.example.data.model.LogLevel
import com.example.data.model.ViewportMode
import com.example.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun NormalPreviewView(
    bundledHtml: String,
    logs: List<ConsoleLogItem>,
    isTunnelActive: Boolean,
    onAddLog: (LogLevel, String) -> Unit,
    onClearLogs: () -> Unit,
    onOpenTunnelModal: () -> Unit,
    onOpenExternalBrowser: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var viewportMode by remember { mutableStateOf(ViewportMode.FULLSCREEN) }
    var isDeviceMenuExpanded by remember { mutableStateOf(false) }
    var isConsoleOpen by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var rendererCrashRecoveryKey by remember { mutableIntStateOf(0) }
    val errorCount = remember(logs) { logs.count { it.level == LogLevel.ERROR } }

    val injectedHtml = remember(bundledHtml) {
        val consoleHookScript = """
            <style>
            /* Default dark canvas to prevent initial white screen flash */
            html, body {
                background-color: #0D0D0F;
                color: #ECECED;
            }
            </style>
            <script>
            (function() {
                var oldLog = console.log;
                var oldWarn = console.warn;
                var oldErr = console.error;
                console.log = function() {
                    var args = Array.prototype.slice.call(arguments).join(' ');
                    if (window.AndroidConsole) { window.AndroidConsole.log(args); }
                    oldLog.apply(console, arguments);
                };
                console.warn = function() {
                    var args = Array.prototype.slice.call(arguments).join(' ');
                    if (window.AndroidConsole) { window.AndroidConsole.warn(args); }
                    oldWarn.apply(console, arguments);
                };
                console.error = function() {
                    var args = Array.prototype.slice.call(arguments).join(' ');
                    if (window.AndroidConsole) { window.AndroidConsole.error(args); }
                    oldErr.apply(console, arguments);
                };
                window.onerror = function(msg, url, line, col, error) {
                    if (window.AndroidConsole) {
                        window.AndroidConsole.error("Runtime Error: " + msg + " at line " + line);
                    }
                };
            })();
            </script>
        """.trimIndent()

        if (bundledHtml.contains("<head>", ignoreCase = true)) {
            bundledHtml.replaceFirst(Regex("<head>", RegexOption.IGNORE_CASE), "<head>\n$consoleHookScript")
        } else {
            consoleHookScript + "\n" + bundledHtml
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TZeronBgDark)
            .testTag("normal_preview_screen")
    ) {
        // Preview Header Toolbar with Standardized Editor-Tab Styling
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(TZeronSurface)
                .border(0.5.dp, TZeronBorder)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Consolidated Device View Pop-up Menu Button
            Box {
                Surface(
                    modifier = Modifier
                        .clickable { isDeviceMenuExpanded = true }
                        .testTag("device_view_popup_btn"),
                    color = TZeronSurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, TZeronBorderSubtle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val deviceIcon = when (viewportMode) {
                            ViewportMode.FULLSCREEN -> TZeronIcons.Fullscreen
                            ViewportMode.MOBILE -> TZeronIcons.Mobile
                            ViewportMode.TABLET -> TZeronIcons.Tablet
                            ViewportMode.DESKTOP -> TZeronIcons.Desktop
                        }
                        val deviceLabel = when (viewportMode) {
                            ViewportMode.FULLSCREEN -> "Fluid"
                            ViewportMode.MOBILE -> "Phone (360px)"
                            ViewportMode.TABLET -> "Tablet (520px)"
                            ViewportMode.DESKTOP -> "Desktop (16:9)"
                        }

                        Icon(
                            imageVector = deviceIcon,
                            contentDescription = "Device View",
                            tint = TZeronAccentBlue,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = deviceLabel,
                            color = TZeronTextPrimary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = TZeronIcons.ChevronDown,
                            contentDescription = null,
                            tint = TZeronTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Dropdown Menu for Responsive Viewport Selection
                DropdownMenu(
                    expanded = isDeviceMenuExpanded,
                    onDismissRequest = { isDeviceMenuExpanded = false },
                    modifier = Modifier
                        .background(TZeronSurfaceElevated)
                        .border(0.5.dp, TZeronBorder, RoundedCornerShape(10.dp))
                ) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = TZeronIcons.Fullscreen, contentDescription = null, tint = TZeronTextPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Fluid Responsive (100%)", color = TZeronTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        },
                        onClick = {
                            viewportMode = ViewportMode.FULLSCREEN
                            isDeviceMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = TZeronIcons.Mobile, contentDescription = null, tint = TZeronTextPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mobile Phone (360px)", color = TZeronTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        },
                        onClick = {
                            viewportMode = ViewportMode.MOBILE
                            isDeviceMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = TZeronIcons.Tablet, contentDescription = null, tint = TZeronTextPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Tablet Device (520px)", color = TZeronTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        },
                        onClick = {
                            viewportMode = ViewportMode.TABLET
                            isDeviceMenuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = TZeronIcons.Desktop, contentDescription = null, tint = TZeronTextPrimary, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Desktop (16:9 Aspect Ratio)", color = TZeronTextPrimary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        },
                        onClick = {
                            viewportMode = ViewportMode.DESKTOP
                            isDeviceMenuExpanded = false
                        }
                    )
                }
            }

            // Actions: Local (Browser), Public (Tunnel), Terminal, Reset
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                // 1. LOCAL Action Button (Opens in local device browser)
                PreviewActionButton(
                    icon = TZeronIcons.Desktop,
                    contentDescription = "Open in Local Browser",
                    label = "LOCAL",
                    onClick = onOpenExternalBrowser,
                    testTag = "preview_local_browser_btn"
                )

                // 2. PUBLIC / LIVE Tunnel Button (Shares over LAN/Public network)
                PreviewActionButton(
                    icon = TZeronIcons.Public,
                    contentDescription = "Public LAN Hosting",
                    label = if (isTunnelActive) "LIVE" else "PUBLIC",
                    isActive = isTunnelActive,
                    onClick = { onOpenTunnelModal() },
                    testTag = "preview_tunnel_btn"
                )

                // 3. TERMINAL Button (Positioned directly to the left of the Reset button)
                PreviewActionButton(
                    icon = TZeronIcons.Terminal,
                    contentDescription = "Console Terminal",
                    label = if (errorCount > 0) "TERMINAL ($errorCount)" else "TERMINAL",
                    isActive = isConsoleOpen,
                    isError = errorCount > 0,
                    onClick = { isConsoleOpen = !isConsoleOpen },
                    testTag = "toggle_console_drawer_btn"
                )

                // 4. RESET Button (Relocated to the top-right corner of Preview page)
                PreviewActionButton(
                    icon = TZeronIcons.Refresh,
                    contentDescription = "Reset Preview",
                    label = "",
                    onClick = { webViewInstance?.reload() },
                    testTag = "preview_reload_btn"
                )
            }
        }

        // Viewport Frame Canvas (Directly below header with zero latency)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(TZeronBgDark),
            contentAlignment = Alignment.Center
        ) {
            val frameModifier = when (viewportMode) {
                ViewportMode.FULLSCREEN -> Modifier.fillMaxSize()
                ViewportMode.MOBILE -> Modifier
                    .width(360.dp)
                    .fillMaxHeight(0.96f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, TZeronBorder, shape = RoundedCornerShape(16.dp))
                ViewportMode.TABLET -> Modifier
                    .width(520.dp)
                    .fillMaxHeight(0.96f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(1.dp, TZeronBorder, shape = RoundedCornerShape(16.dp))
                // Enforced Fixed 16:9 Aspect Ratio for Desktop Mode
                ViewportMode.DESKTOP -> Modifier
                    .fillMaxWidth(0.96f)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, TZeronBorder, shape = RoundedCornerShape(12.dp))
            }

            Box(
                modifier = frameModifier
                    .background(TZeronBgDark)
            ) {
                key(rendererCrashRecoveryKey) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            WebView(context).apply {
                                setBackgroundColor(android.graphics.Color.parseColor("#0D0D0F"))

                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.allowContentAccess = true
                                settings.allowFileAccess = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                settings.databaseEnabled = true
                                settings.setSupportZoom(true)
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false

                                addJavascriptInterface(object {
                                    @JavascriptInterface
                                    fun log(msg: String) {
                                        post { onAddLog(LogLevel.LOG, msg) }
                                    }
                                    @JavascriptInterface
                                    fun warn(msg: String) {
                                        post { onAddLog(LogLevel.WARN, msg) }
                                    }
                                    @JavascriptInterface
                                    fun error(msg: String) {
                                        post { onAddLog(LogLevel.ERROR, msg) }
                                    }
                                }, "AndroidConsole")

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        view?.setBackgroundColor(android.graphics.Color.parseColor("#0D0D0F"))
                                        super.onPageStarted(view, url, favicon)
                                    }
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        view?.setBackgroundColor(android.graphics.Color.parseColor("#0D0D0F"))
                                        super.onPageFinished(view, url)
                                    }
                                    override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                        // Intercept renderer crash to prevent Android from terminating the host process
                                        try {
                                            view?.let {
                                                (it.parent as? android.view.ViewGroup)?.removeView(it)
                                                it.destroy()
                                            }
                                        } catch (_: Exception) {}
                                        // Re-instantiate preview gracefully
                                        rendererCrashRecoveryKey += 1
                                        return true
                                    }
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                        consoleMessage?.let {
                                            val level = when (it.messageLevel()) {
                                                ConsoleMessage.MessageLevel.ERROR -> LogLevel.ERROR
                                                ConsoleMessage.MessageLevel.WARNING -> LogLevel.WARN
                                                else -> LogLevel.LOG
                                            }
                                            onAddLog(level, it.message())
                                        }
                                        return super.onConsoleMessage(consoleMessage)
                                    }
                                }

                                tag = injectedHtml
                                loadDataWithBaseURL("https://tzeron.local/", injectedHtml, "text/html", "UTF-8", null)
                                webViewInstance = this
                            }
                        },
                        update = { view ->
                            if (view.tag != injectedHtml) {
                                view.tag = injectedHtml
                                view.loadDataWithBaseURL("https://tzeron.local/", injectedHtml, "text/html", "UTF-8", null)
                            }
                        }
                    )
                }
            }
        }

        // Animated Bottom Dev Console Drawer
        AnimatedVisibility(visible = isConsoleOpen) {
            DevConsoleView(
                logs = logs,
                onClearLogs = onClearLogs,
                onClose = { isConsoleOpen = false }
            )
        }
    }
}

@Composable
private fun PreviewActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    label: String,
    isActive: Boolean = false,
    isError: Boolean = false,
    onClick: () -> Unit,
    testTag: String
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "preview_action_btn_scale"
    )

    val bgColor = when {
        isError -> Color(0x33EF4444)
        isActive -> TZeronSurfaceElevated
        else -> TZeronSurfaceElevated
    }

    val borderColor = when {
        isError -> TZeronError
        isActive -> TZeronAccentBlue
        else -> TZeronBorderSubtle
    }

    val iconTint = when {
        isError -> TZeronError
        isActive -> TZeronAccentBlue
        else -> TZeronAccentBlue
    }

    val textColor = when {
        isError -> TZeronError
        isActive -> TZeronTextPrimary
        else -> TZeronTextPrimary
    }

    val isSquare = label.isEmpty()

    Surface(
        modifier = Modifier
            .scale(scale)
            .height(28.dp)
            .then(if (isSquare) Modifier.width(28.dp) else Modifier)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .testTag(testTag),
        color = bgColor,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, borderColor)
    ) {
        Box(
            modifier = Modifier.then(
                if (isSquare) Modifier.fillMaxSize()
                else Modifier.padding(horizontal = 8.dp)
            ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(13.dp)
                )
                if (label.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        color = textColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
