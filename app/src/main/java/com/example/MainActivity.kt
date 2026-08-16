package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.screens.MainScreen
import com.example.ui.screens.MainViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "TZeronStartup"
    }

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "MainActivity onCreate: Initializing T•ZERONE IDE primary entry point")

        // Set global uncaught exception handler for comprehensive crash diagnosis
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(TAG, "CRITICAL UNCAUGHT EXCEPTION in thread [${thread.name}]: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            enableEdgeToEdge()
            Log.d(TAG, "Edge-to-edge layout successfully enabled")
        } catch (e: Throwable) {
            Log.w(TAG, "Warning: Failed to enable edge-to-edge layout, falling back gracefully: ${e.message}")
        }

        try {
            setContent {
                MyApplicationTheme {
                    var startupError by remember { mutableStateOf<Throwable?>(null) }

                    if (startupError == null) {
                        CompositionLocalProvider {
                            MainScreen(
                                viewModel = mainViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        StartupErrorRecoveryScreen(
                            error = startupError!!,
                            onResetWorkspace = {
                                Log.i(TAG, "User requested workspace reset from error recovery screen")
                                mainViewModel.initMultiFileWorkspace("tzeron-workspace")
                                startupError = null
                            },
                            onReloadApp = {
                                Log.i(TAG, "User requested reload from error recovery screen")
                                startupError = null
                                recreate()
                            }
                        )
                    }
                }
            }
            Log.i(TAG, "Compose content successfully initialized")
        } catch (e: Throwable) {
            Log.e(TAG, "Fatal error setting Compose content", e)
            showFatalFallbackView(e)
        }
    }

    private fun showFatalFallbackView(error: Throwable) {
        setContentView(
            android.widget.TextView(this).apply {
                text = "T•ZERONE CODE encountered a startup error:\n\n${error.stackTraceToString()}"
                setTextColor(android.graphics.Color.WHITE)
                setBackgroundColor(android.graphics.Color.parseColor("#0D0D0F"))
                setPadding(48, 96, 48, 48)
                setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12f)
            }
        )
    }
}

@Composable
fun StartupErrorRecoveryScreen(
    error: Throwable,
    onResetWorkspace: () -> Unit,
    onReloadApp: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0D0D0F)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "T•ZERONE CODE",
                color = Color(0xFF007ACC),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Safe Recovery Mode",
                color = Color(0xFFECECED),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "An unexpected error occurred during execution. You can safely restore or reset your workspace below.",
                color = Color(0xFF8E8D99),
                fontSize = 12.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Diagnostic Log Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(max = 240.dp)
                    .background(Color(0xFF16161B), shape = RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .verticalScroll(scrollState)
            ) {
                Text(
                    text = error.stackTraceToString(),
                    color = Color(0xFFFF6B6B),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Error Log", error.stackTraceToString()))
                        Toast.makeText(context, "Diagnostic log copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26262E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Copy Log", color = Color(0xFFECECED), fontSize = 12.sp)
                }

                Button(
                    onClick = onResetWorkspace,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007ACC)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Reset Workspace", color = Color.White, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onReloadApp,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Reload App", color = Color(0xFF007ACC), fontSize = 12.sp)
            }
        }
    }
}


