package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ConsoleLogItem
import com.example.data.model.LogLevel
import com.example.ui.theme.*

@Composable
fun DevConsoleView(
    logs: List<ConsoleLogItem>,
    onClearLogs: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf<LogLevel?>(null) }
    val filteredLogs = remember(logs, selectedFilter) {
        if (selectedFilter == null) logs else logs.filter { it.level == selectedFilter }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .testTag("dev_console_panel"),
        color = TZeronSurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, TZeronBorder)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Console Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(TZeronSurfaceElevated)
                    .border(0.5.dp, TZeronBorderSubtle)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = TZeronIcons.Terminal,
                        contentDescription = "Console",
                        tint = TZeronTextPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CONSOLE",
                        color = TZeronTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(12.dp))

                    // Minimal Filter Pills
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterPill("ALL", selectedFilter == null) { selectedFilter = null }
                        FilterPill("ERRORS", selectedFilter == LogLevel.ERROR, isError = true) { selectedFilter = LogLevel.ERROR }
                        FilterPill("WARNS", selectedFilter == LogLevel.WARN) { selectedFilter = LogLevel.WARN }
                        FilterPill("LOGS", selectedFilter == LogLevel.LOG) { selectedFilter = LogLevel.LOG }
                    }
                }

                Row {
                    IconButton(onClick = onClearLogs, modifier = Modifier.size(28.dp).testTag("clear_console_btn")) {
                        Icon(imageVector = TZeronIcons.Delete, contentDescription = "Clear", tint = TZeronTextMuted, modifier = Modifier.size(14.dp))
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(28.dp).testTag("close_console_btn")) {
                        Icon(imageVector = TZeronIcons.Close, contentDescription = "Close", tint = TZeronTextSecondary, modifier = Modifier.size(14.dp))
                    }
                }
            }

            // Logs stream
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TZeronBgDark),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No console output or runtime errors.",
                        color = TZeronTextMuted,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(TZeronBgDark)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        val isError = log.level == LogLevel.ERROR
                        val textColor = if (isError) Color(0xFFFCA5A5) else TZeronTextPrimary
                        val iconColor = if (isError) TZeronError else TZeronTextSecondary

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .background(
                                    if (isError) Color(0x1AEF4444) else Color.Transparent,
                                    shape = MaterialTheme.shapes.extraSmall
                                )
                                .padding(horizontal = 6.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Icon(
                                imageVector = when (log.level) {
                                    LogLevel.ERROR -> TZeronIcons.Error
                                    LogLevel.WARN -> TZeronIcons.Warning
                                    LogLevel.INFO -> TZeronIcons.Info
                                    LogLevel.LOG -> TZeronIcons.ChevronRight
                                },
                                contentDescription = null,
                                tint = iconColor,
                                modifier = Modifier
                                    .size(13.dp)
                                    .padding(top = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = log.message,
                                color = textColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill(
    label: String,
    isSelected: Boolean,
    isError: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = if (isSelected) TZeronSurfaceElevated else TZeronSurfaceCard,
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(
            0.5.dp,
            if (isSelected) (if (isError) TZeronError else TZeronTextPrimary) else TZeronBorderSubtle
        )
    ) {
        Text(
            text = label,
            color = if (isSelected) (if (isError) TZeronError else TZeronTextPrimary) else TZeronTextMuted,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
