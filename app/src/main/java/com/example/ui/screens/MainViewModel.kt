package com.example.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.ProjectRepository
import com.example.data.model.*
import com.example.utils.CodeFormatter
import com.example.utils.ZipUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MainNavTab {
    EDIT,
    PREVIEW
}

data class MainUiState(
    val projectName: String = "tzeron-project",
    val files: List<ProjectFile> = emptyList(),
    val openTabs: List<ProjectFile> = emptyList(),
    val activeFileId: String? = null,
    val isReadOnly: Boolean = true, // Read-only by default to prevent unwanted keyboard pops
    val activeNavTab: MainNavTab = MainNavTab.EDIT,
    val previewSubMode: PreviewSubMode = PreviewSubMode.NORMAL,
    val isExplorerOpen: Boolean = false,
    val consoleLogs: List<ConsoleLogItem> = emptyList(),
    val isTunnelActive: Boolean = false,
    val tunnelUrl: String = "https://t-zeron.live/tunnel/tz-7x92kp",
    val syntaxTheme: SyntaxTheme = SyntaxTheme.VS_CODE_DARK,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    // Interactive Visual IDE Staging
    val visualElements: List<DOMElementNode> = emptyList(),
    val selectedElementId: String? = null,
    val hasUnsavedVisualChanges: Boolean = false,
    // Modals visibility
    val showNewProjectModal: Boolean = false,
    val showOpenModal: Boolean = false,
    val showFormatModal: Boolean = false,
    val showDownloadModal: Boolean = false,
    val showTunnelModal: Boolean = false,
    val showSyntaxThemeModal: Boolean = false,
    val statusMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ProjectRepository = ProjectRepository(
        AppDatabase.getDatabase(application).projectDao()
    )

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // Undo / Redo history stacks per fileId
    private val undoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private var autoSaveJob: Job? = null

    init {
        // Initial setup: multi-file architecture with index.html, styles.css, and script.js
        initMultiFileWorkspace()
    }

    fun initMultiFileWorkspace(name: String = "tzeron-workspace") {
        val htmlFile = ProjectFile(
            name = "index.html",
            path = "index.html",
            content = """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>T•ZERONE | Next-Gen Web Experience</title>
  <link rel="stylesheet" href="styles.css">
</head>
<body data-theme="dark">
  <!-- Header / Navigation -->
  <header class="navbar">
    <div class="nav-container">
      <div class="logo">
        <span class="logo-icon">⚡</span>
        <span class="logo-text">T•ZERONE</span>
      </div>
      <nav class="nav-links">
        <a href="#overview" class="nav-link active">Overview</a>
        <a href="#demo" class="nav-link">120 FPS Engine</a>
        <a href="#features" class="nav-link">Features</a>
        <a href="#interactive" class="nav-link">Playground</a>
      </nav>
      <div class="nav-actions">
        <div class="fps-badge" id="fpsDisplay">120 FPS</div>
        <button id="themeToggleBtn" class="theme-btn" title="Toggle Theme">🌓</button>
      </div>
    </div>
  </header>

  <!-- Main Content -->
  <main class="main-content">
    <!-- Hero Section -->
    <section id="overview" class="hero-section">
      <div class="hero-badge">🚀 MOBILE-OPTIMIZED ARCHITECTURE</div>
      <h1 class="hero-title">Ultra-Fast Web Engineering <span class="gradient-text">At 120 FPS</span></h1>
      <p class="hero-subtitle">
        A high-performance workspace combining zero-latency multi-file coding, dynamic DOM styling, and real-time GPU hardware acceleration.
      </p>
      <div class="hero-cta-group">
        <a href="#demo" class="btn btn-primary">Try 120 FPS Demo</a>
        <a href="#interactive" class="btn btn-secondary">Open Playground</a>
      </div>

      <!-- Quick Metrics -->
      <div class="metrics-grid">
        <div class="metric-card">
          <div class="metric-value">120 Hz</div>
          <div class="metric-label">Refresh Rate Support</div>
        </div>
        <div class="metric-card">
          <div class="metric-value">&lt; 8.3ms</div>
          <div class="metric-label">Frame Budget</div>
        </div>
        <div class="metric-card">
          <div class="metric-value">0 ms</div>
          <div class="metric-label">Preview Latency</div>
        </div>
        <div class="metric-card">
          <div class="metric-value">100%</div>
          <div class="metric-label">Offline Ready</div>
        </div>
      </div>
    </section>

    <!-- 120 FPS Interactive Physics & Particle Canvas -->
    <section id="demo" class="section">
      <div class="section-header">
        <h2 class="section-title">High-Frequency Particle Canvas</h2>
        <p class="section-desc">Hardware-accelerated rendering loop dynamically syncing with display VSYNC.</p>
      </div>
      <div class="canvas-card">
        <div class="canvas-toolbar">
          <div class="canvas-status">
            <span class="status-dot"></span>
            <span id="canvasStats">Active Particles: 60 | Avg Frame Time: 8.3ms</span>
          </div>
          <div class="canvas-controls">
            <button id="addParticlesBtn" class="btn-sm">Add +20</button>
            <button id="clearParticlesBtn" class="btn-sm">Reset</button>
          </div>
        </div>
        <canvas id="particleCanvas" width="800" height="360"></canvas>
      </div>
    </section>

    <!-- Interactive Component Playground -->
    <section id="interactive" class="section">
      <div class="section-header">
        <h2 class="section-title">Interactive Component Studio</h2>
        <p class="section-desc">Test reactive state management, DOM manipulation, and dynamic event dispatchers.</p>
      </div>
      <div class="playground-grid">
        <!-- Interactive Counter Widget -->
        <div class="card widget-card">
          <div class="card-header">
            <h3>⚡ Reactive State Counter</h3>
          </div>
          <div class="counter-display" id="counterValue">0</div>
          <div class="counter-actions">
            <button id="counterDec" class="btn btn-sm btn-danger">-5</button>
            <button id="counterDecOne" class="btn btn-sm btn-secondary">-1</button>
            <button id="counterReset" class="btn btn-sm btn-ghost">Reset</button>
            <button id="counterIncOne" class="btn btn-sm btn-secondary">+1</button>
            <button id="counterInc" class="btn btn-sm btn-primary">+5</button>
          </div>
        </div>

        <!-- Dynamic Color Palette Generator -->
        <div class="card widget-card">
          <div class="card-header">
            <h3>🎨 Palette Generator</h3>
          </div>
          <div class="palette-display" id="paletteBoxes">
            <div class="swatch" style="background: #007ACC;" data-color="#007ACC"><span>#007ACC</span></div>
            <div class="swatch" style="background: #3B82F6;" data-color="#3B82F6"><span>#3B82F6</span></div>
            <div class="swatch" style="background: #10B981;" data-color="#10B981"><span>#10B981</span></div>
            <div class="swatch" style="background: #F59E0B;" data-color="#F59E0B"><span>#F59E0B</span></div>
            <div class="swatch" style="background: #EC4899;" data-color="#EC4899"><span>#EC4899</span></div>
          </div>
          <button id="generatePaletteBtn" class="btn btn-primary btn-full">Generate New Harmonies</button>
        </div>

        <!-- Dynamic Task Manager -->
        <div class="card widget-card full-width">
          <div class="card-header">
            <h3>📋 Quick Task Sandbox</h3>
          </div>
          <div class="todo-input-group">
            <input type="text" id="taskInput" placeholder="Add a new task or idea..." class="input-text">
            <button id="addTaskBtn" class="btn btn-primary">Add Task</button>
          </div>
          <ul id="taskList" class="task-list">
            <li class="task-item completed">
              <span class="task-check">✓</span>
              <span class="task-text">Initialize 120 FPS high-refresh render loop</span>
              <button class="task-del">✕</button>
            </li>
            <li class="task-item">
              <span class="task-check">○</span>
              <span class="task-text">Test multi-file project bundler and exporter</span>
              <button class="task-del">✕</button>
            </li>
          </ul>
        </div>
      </div>
    </section>

    <!-- Features Overview -->
    <section id="features" class="section">
      <div class="section-header">
        <h2 class="section-title">Core Engineering Modules</h2>
        <p class="section-desc">Designed from the ground up for mobile developers and web designers.</p>
      </div>
      <div class="features-grid">
        <div class="feature-card">
          <div class="feature-icon">⚡</div>
          <h3>Zero-Latency Engine</h3>
          <p>Instantaneous DOM tree reconciliation and isolated webview execution with complete devtools streaming.</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">📁</div>
          <h3>Multi-File Architecture</h3>
          <p>Organize complex workspaces with independent HTML, CSS, JavaScript, and asset trees.</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">🪄</div>
          <h3>Code Beautification</h3>
          <p>Automated standardizer for indentation, tag closing, and single-click inline compilation.</p>
        </div>
        <div class="feature-card">
          <div class="feature-icon">📦</div>
          <h3>Universal Export</h3>
          <p>Package complete .ZIP archives or standalone single-file production HTML with zero external dependencies.</p>
        </div>
      </div>
    </section>
  </main>

  <!-- Footer -->
  <footer class="footer">
    <div class="footer-container">
      <div class="footer-brand">
        <span class="logo-icon">⚡</span>
        <span>T•ZERONE CODE STUDIO</span>
      </div>
      <div class="footer-info">
        <span>Hardware-Accelerated 120 FPS Web Platform</span>
        <span>•</span>
        <span>Offline-First</span>
      </div>
    </div>
  </footer>

  <script src="script.js"></script>
</body>
</html>""",
            isFolder = false
        )

        val cssFile = ProjectFile(
            name = "styles.css",
            path = "styles.css",
            content = """/* CSS Custom Properties / Theming */
:root {
  --bg-primary: #0D0D0F;
  --bg-surface: #141317;
  --bg-elevated: #1E1D22;
  --bg-card: #26252B;
  --border-color: #2E2D36;
  --border-subtle: #1E1D22;
  --text-primary: #ECECED;
  --text-secondary: #9D9CA7;
  --text-muted: #656470;
  --accent-blue: #007ACC;
  --accent-glow: rgba(0, 122, 204, 0.35);
  --accent-success: #10B981;
  --accent-warning: #F59E0B;
  --accent-danger: #EF4444;
  --font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  --radius-sm: 8px;
  --radius-md: 12px;
  --radius-lg: 16px;
  --radius-full: 9999px;
  --transition-fast: 0.15s cubic-bezier(0.4, 0, 0.2, 1);
}

[data-theme="cyber"] {
  --bg-primary: #050811;
  --bg-surface: #0a1128;
  --bg-elevated: #101f42;
  --bg-card: #172a5a;
  --border-color: #00f0ff;
  --border-subtle: #005f73;
  --text-primary: #e0fbfc;
  --text-secondary: #90e0ef;
  --accent-blue: #00f0ff;
  --accent-glow: rgba(0, 240, 255, 0.4);
}

/* Reset & Base */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
  -webkit-tap-highlight-color: transparent;
}

html {
  scroll-behavior: smooth;
}

body {
  background-color: var(--bg-primary);
  color: var(--text-primary);
  font-family: var(--font-family);
  line-height: 1.6;
  min-height: 100vh;
  overflow-x: hidden;
  will-change: transform;
}

/* Navigation Bar */
.navbar {
  position: sticky;
  top: 0;
  z-index: 100;
  background: rgba(20, 19, 23, 0.88);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--border-color);
  padding: 10px 16px;
}

.nav-container {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.logo {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 800;
  font-size: 15px;
  letter-spacing: 0.5px;
}

.logo-icon {
  font-size: 18px;
  color: var(--accent-blue);
}

.nav-links {
  display: flex;
  gap: 14px;
}

.nav-link {
  color: var(--text-secondary);
  text-decoration: none;
  font-size: 13px;
  font-weight: 500;
  transition: color var(--transition-fast);
}

.nav-link:hover, .nav-link.active {
  color: var(--text-primary);
}

.nav-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.fps-badge {
  background: var(--bg-elevated);
  color: var(--accent-success);
  border: 1px solid var(--accent-success);
  font-size: 11px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: var(--radius-full);
  font-family: monospace;
}

.theme-btn {
  background: var(--bg-elevated);
  border: 1px solid var(--border-color);
  color: var(--text-primary);
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  transition: transform var(--transition-fast);
}

.theme-btn:active {
  transform: scale(0.92);
}

/* Main Layout */
.main-content {
  max-width: 1100px;
  margin: 0 auto;
  padding: 24px 16px 60px;
  display: flex;
  flex-direction: column;
  gap: 48px;
}

/* Hero Section */
.hero-section {
  text-align: center;
  padding: 32px 12px 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.hero-badge {
  display: inline-block;
  background: var(--bg-elevated);
  color: var(--accent-blue);
  font-size: 11px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: var(--radius-full);
  border: 1px solid var(--border-color);
  margin-bottom: 16px;
  letter-spacing: 0.5px;
}

.hero-title {
  font-size: clamp(26px, 5vw, 42px);
  font-weight: 900;
  line-height: 1.2;
  margin-bottom: 14px;
  max-width: 780px;
}

.gradient-text {
  background: linear-gradient(135deg, #007ACC 0%, #38BDF8 50%, #818CF8 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.hero-subtitle {
  font-size: clamp(14px, 2.5vw, 16px);
  color: var(--text-secondary);
  max-width: 640px;
  margin-bottom: 24px;
}

.hero-cta-group {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: 36px;
}

/* Buttons */
.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 10px 20px;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 700;
  text-decoration: none;
  cursor: pointer;
  border: none;
  transition: transform var(--transition-fast), background-color var(--transition-fast);
}

.btn:active {
  transform: scale(0.96);
}

.btn-primary {
  background: var(--accent-blue);
  color: #ffffff;
  box-shadow: 0 4px 14px var(--accent-glow);
}

.btn-secondary {
  background: var(--bg-elevated);
  color: var(--text-primary);
  border: 1px solid var(--border-color);
}

.btn-danger {
  background: var(--accent-danger);
  color: #ffffff;
}

.btn-ghost {
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-color);
}

.btn-sm {
  padding: 6px 12px;
  font-size: 11px;
}

.btn-full {
  width: 100%;
}

/* Metrics Grid */
.metrics-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: 12px;
  width: 100%;
  max-width: 780px;
}

.metric-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 16px 10px;
  text-align: center;
}

.metric-value {
  font-size: 20px;
  font-weight: 800;
  color: var(--accent-blue);
  font-family: monospace;
}

.metric-label {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 4px;
}

/* Section Header */
.section {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

.section-header {
  text-align: left;
}

.section-title {
  font-size: 20px;
  font-weight: 800;
  color: var(--text-primary);
}

.section-desc {
  font-size: 13px;
  color: var(--text-secondary);
}

/* Canvas Card */
.canvas-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: 0 10px 25px rgba(0,0,0,0.4);
}

.canvas-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--bg-elevated);
  border-bottom: 1px solid var(--border-color);
  font-size: 11px;
  font-family: monospace;
}

.canvas-status {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-secondary);
}

.status-dot {
  width: 8px;
  height: 8px;
  background: var(--accent-success);
  border-radius: 50%;
  box-shadow: 0 0 8px var(--accent-success);
}

.canvas-controls {
  display: flex;
  gap: 6px;
}

#particleCanvas {
  display: block;
  width: 100%;
  height: 280px;
  background: #08080a;
  touch-action: none;
}

/* Playground Grid */
.playground-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 18px;
}

.widget-card {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.widget-card.full-width {
  grid-column: 1 / -1;
}

.card-header h3 {
  font-size: 14px;
  font-weight: 700;
}

/* Counter */
.counter-display {
  font-size: 44px;
  font-weight: 900;
  text-align: center;
  font-family: monospace;
  color: var(--accent-blue);
  padding: 10px 0;
}

.counter-actions {
  display: flex;
  gap: 6px;
  justify-content: center;
  flex-wrap: wrap;
}

/* Palette */
.palette-display {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
}

.swatch {
  height: 52px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding: 4px;
  cursor: pointer;
  transition: transform var(--transition-fast);
}

.swatch:hover {
  transform: translateY(-2px);
}

.swatch span {
  font-size: 8px;
  font-family: monospace;
  font-weight: bold;
  background: rgba(0,0,0,0.6);
  padding: 2px 4px;
  border-radius: 4px;
  color: #fff;
}

/* Tasks */
.todo-input-group {
  display: flex;
  gap: 8px;
}

.input-text {
  flex: 1;
  background: var(--bg-elevated);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  color: var(--text-primary);
  font-size: 13px;
  outline: none;
}

.input-text:focus {
  border-color: var(--accent-blue);
}

.task-list {
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--bg-elevated);
  border: 1px solid var(--border-color);
  padding: 10px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  cursor: pointer;
}

.task-item.completed {
  opacity: 0.6;
}

.task-item.completed .task-text {
  text-decoration: line-through;
}

.task-check {
  margin-right: 8px;
  color: var(--accent-blue);
  font-weight: bold;
}

.task-del {
  background: transparent;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 4px;
}

.task-del:hover {
  color: var(--accent-danger);
}

/* Features Grid */
.features-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 14px;
}

.feature-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-color);
  border-radius: var(--radius-md);
  padding: 20px;
}

.feature-icon {
  font-size: 24px;
  margin-bottom: 10px;
}

.feature-card h3 {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 6px;
}

.feature-card p {
  font-size: 12px;
  color: var(--text-secondary);
  line-height: 1.5;
}

/* Footer */
.footer {
  border-top: 1px solid var(--border-color);
  padding: 24px 16px;
  background: var(--bg-surface);
}

.footer-container {
  max-width: 1100px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--text-muted);
}

.footer-brand {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  color: var(--text-primary);
}

.footer-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

@media (max-width: 600px) {
  .nav-links {
    display: none;
  }
}""",
            isFolder = false
        )

        val jsFile = ProjectFile(
            name = "script.js",
            path = "script.js",
            content = """// T•ZERONE High-Performance Runtime & Interactive Engine
console.log("⚡ T•ZERONE 120 FPS Engine initialized successfully.");

// 1. High-Precision 120 FPS Performance Tracker
let frameCount = 0;
let lastFpsUpdate = performance.now();
let currentFps = 120;
const fpsDisplay = document.getElementById("fpsDisplay");

function measureFps(timestamp) {
  frameCount++;
  const elapsed = timestamp - lastFpsUpdate;
  if (elapsed >= 500) {
    currentFps = Math.round((frameCount * 1000) / elapsed);
    if (fpsDisplay) {
      fpsDisplay.textContent = currentFps + " FPS";
      fpsDisplay.style.color = currentFps >= 90 ? "#10B981" : (currentFps >= 50 ? "#F59E0B" : "#EF4444");
    }
    frameCount = 0;
    lastFpsUpdate = timestamp;
  }
  requestAnimationFrame(measureFps);
}
requestAnimationFrame(measureFps);

// 2. Hardware-Accelerated 120 FPS Particle Canvas
const canvas = document.getElementById("particleCanvas");
if (canvas) {
  const ctx = canvas.getContext("2d");
  let particles = [];
  const particleColors = ["#007ACC", "#38BDF8", "#818CF8", "#10B981", "#EC4899"];

  function resizeCanvas() {
    const rect = canvas.getBoundingClientRect();
    canvas.width = rect.width * (window.devicePixelRatio || 1);
    canvas.height = rect.height * (window.devicePixelRatio || 1);
  }
  resizeCanvas();
  window.addEventListener("resize", resizeCanvas);

  class Particle {
    constructor(x, y) {
      this.x = x !== undefined ? x : Math.random() * canvas.width;
      this.y = y !== undefined ? y : Math.random() * canvas.height;
      this.vx = (Math.random() - 0.5) * 3;
      this.vy = (Math.random() - 0.5) * 3;
      this.radius = Math.random() * 3 + 1.5;
      this.color = particleColors[Math.floor(Math.random() * particleColors.length)];
      this.alpha = Math.random() * 0.7 + 0.3;
    }
    update() {
      this.x += this.vx;
      this.y += this.vy;
      if (this.x <= 0 || this.x >= canvas.width) this.vx *= -1;
      if (this.y <= 0 || this.y >= canvas.height) this.vy *= -1;
    }
    draw() {
      ctx.save();
      ctx.globalAlpha = this.alpha;
      ctx.fillStyle = this.color;
      ctx.beginPath();
      ctx.arc(this.x, this.y, this.radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    }
  }

  function initParticles(count = 60) {
    particles = [];
    for (let i = 0; i < count; i++) {
      particles.push(new Particle());
    }
    updateCanvasStats();
  }
  initParticles(60);

  function updateCanvasStats() {
    const statsEl = document.getElementById("canvasStats");
    if (statsEl) {
      statsEl.textContent = "Active Particles: " + particles.length + " | Smooth 120 FPS Loop";
    }
  }

  // Render loop
  function renderCanvas() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    for (let i = 0; i < particles.length; i++) {
      particles[i].update();
      particles[i].draw();
      // Draw connection lines
      for (let j = i + 1; j < particles.length; j++) {
        const dx = particles[i].x - particles[j].x;
        const dy = particles[i].y - particles[j].y;
        const dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < 90) {
          ctx.save();
          ctx.strokeStyle = particles[i].color;
          ctx.globalAlpha = (1 - dist / 90) * 0.25;
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(particles[i].x, particles[i].y);
          ctx.lineTo(particles[j].x, particles[j].y);
          ctx.stroke();
          ctx.restore();
        }
      }
    }
    requestAnimationFrame(renderCanvas);
  }
  requestAnimationFrame(renderCanvas);

  // Canvas Interactions
  canvas.addEventListener("pointerdown", (e) => {
    const rect = canvas.getBoundingClientRect();
    const scale = window.devicePixelRatio || 1;
    const x = (e.clientX - rect.left) * scale;
    const y = (e.clientY - rect.top) * scale;
    for (let i = 0; i < 6; i++) {
      particles.push(new Particle(x, y));
    }
    updateCanvasStats();
  });

  const addBtn = document.getElementById("addParticlesBtn");
  if (addBtn) {
    addBtn.addEventListener("click", () => {
      for (let i = 0; i < 20; i++) particles.push(new Particle());
      updateCanvasStats();
      console.log("Spawned 20 particles. Total: " + particles.length);
    });
  }

  const clearBtn = document.getElementById("clearParticlesBtn");
  if (clearBtn) {
    clearBtn.addEventListener("click", () => {
      initParticles(40);
      console.log("Reset particle canvas.");
    });
  }
}

// 3. Theme Toggle Controller
const themeToggleBtn = document.getElementById("themeToggleBtn");
if (themeToggleBtn) {
  themeToggleBtn.addEventListener("click", () => {
    const currentTheme = document.body.getAttribute("data-theme");
    const nextTheme = currentTheme === "dark" ? "cyber" : "dark";
    document.body.setAttribute("data-theme", nextTheme);
    console.log("Theme switched to: " + nextTheme);
  });
}

// 4. Reactive State Counter
let counterState = 0;
const counterValueEl = document.getElementById("counterValue");
function setCounter(val) {
  counterState = val;
  if (counterValueEl) {
    counterValueEl.textContent = counterState;
    counterValueEl.style.transform = "scale(1.15)";
    setTimeout(() => { counterValueEl.style.transform = "scale(1.0)"; }, 100);
  }
}
document.getElementById("counterInc")?.addEventListener("click", () => setCounter(counterState + 5));
document.getElementById("counterIncOne")?.addEventListener("click", () => setCounter(counterState + 1));
document.getElementById("counterDec")?.addEventListener("click", () => setCounter(counterState - 5));
document.getElementById("counterDecOne")?.addEventListener("click", () => setCounter(counterState - 1));
document.getElementById("counterReset")?.addEventListener("click", () => {
  setCounter(0);
  console.log("Counter reset to 0");
});

// 5. Dynamic Palette Generator
const generatePaletteBtn = document.getElementById("generatePaletteBtn");
const paletteContainer = document.getElementById("paletteBoxes");
if (generatePaletteBtn && paletteContainer) {
  function randomHex() {
    return "#" + Math.floor(Math.random() * 16777215).toString(16).padStart(6, "0").toUpperCase();
  }
  generatePaletteBtn.addEventListener("click", () => {
    paletteContainer.innerHTML = "";
    for (let i = 0; i < 5; i++) {
      const hex = randomHex();
      const swatch = document.createElement("div");
      swatch.className = "swatch";
      swatch.style.background = hex;
      swatch.setAttribute("data-color", hex);
      swatch.innerHTML = "<span>" + hex + "</span>";
      swatch.addEventListener("click", () => {
        console.log("Selected Color Swatch: " + hex);
      });
      paletteContainer.appendChild(swatch);
    }
    console.log("Generated fresh harmonious palette.");
  });
}

// 6. Interactive Task Manager
const taskInput = document.getElementById("taskInput");
const addTaskBtn = document.getElementById("addTaskBtn");
const taskList = document.getElementById("taskList");

function addTask() {
  if (!taskInput || !taskList || !taskInput.value.trim()) return;
  const text = taskInput.value.trim();
  const li = document.createElement("li");
  li.className = "task-item";
  li.innerHTML = '<span class="task-check">○</span><span class="task-text">' + text + '</span><button class="task-del">✕</button>';
  
  li.addEventListener("click", (e) => {
    if (e.target.classList.contains("task-del")) {
      li.remove();
      console.log("Deleted task: " + text);
    } else {
      li.classList.toggle("completed");
      const check = li.querySelector(".task-check");
      if (check) check.textContent = li.classList.contains("completed") ? "✓" : "○";
    }
  });

  taskList.appendChild(li);
  taskInput.value = "";
  console.log("Added new task: " + text);
}

addTaskBtn?.addEventListener("click", addTask);
taskInput?.addEventListener("keydown", (e) => {
  if (e.key === "Enter") addTask();
});

console.log("T•ZERONE Full Website loaded with 0 errors.");""",
            isFolder = false
        )

        val defaultFiles = listOf(htmlFile, cssFile, jsFile)

        undoStacks.clear()
        redoStacks.clear()
        defaultFiles.forEach { file ->
            undoStacks[file.id] = ArrayDeque(listOf(file.content))
            redoStacks[file.id] = ArrayDeque()
        }

        _uiState.update {
            it.copy(
                projectName = name,
                files = defaultFiles,
                openTabs = defaultFiles,
                activeFileId = htmlFile.id,
                isReadOnly = true,
                canUndo = false,
                canRedo = false,
                visualElements = emptyList(),
                selectedElementId = null,
                hasUnsavedVisualChanges = false,
                statusMessage = "Initialized multi-file workspace"
            )
        }
        syncVisualElementsFromActiveFile()
    }

    fun selectTab(file: ProjectFile) {
        _uiState.update { current ->
            val updatedTabs = if (current.openTabs.any { it.id == file.id }) {
                current.openTabs
            } else {
                current.openTabs + file
            }
            val activeId = file.id
            val uStack = undoStacks[activeId]
            val rStack = redoStacks[activeId]
            current.copy(
                openTabs = updatedTabs,
                activeFileId = activeId,
                canUndo = (uStack?.size ?: 0) > 1,
                canRedo = (rStack?.size ?: 0) > 0
            )
        }
        syncVisualElementsFromActiveFile()
    }

    fun closeTab(file: ProjectFile) {
        _uiState.update { current ->
            val remainingTabs = current.openTabs.filter { it.id != file.id }
            val nextActiveId = if (current.activeFileId == file.id) {
                remainingTabs.lastOrNull()?.id
            } else {
                current.activeFileId
            }
            val uStack = undoStacks[nextActiveId]
            val rStack = redoStacks[nextActiveId]
            current.copy(
                openTabs = remainingTabs,
                activeFileId = nextActiveId,
                canUndo = (uStack?.size ?: 0) > 1,
                canRedo = (rStack?.size ?: 0) > 0
            )
        }
    }

    fun updateActiveFileContent(newContent: String, isUndoRedoAction: Boolean = false) {
        val current = _uiState.value
        val activeId = current.activeFileId ?: return

        if (!isUndoRedoAction) {
            val uStack = undoStacks.getOrPut(activeId) { ArrayDeque() }
            if (uStack.isEmpty() || uStack.last() != newContent) {
                uStack.addLast(newContent)
                if (uStack.size > 50) uStack.removeFirst()
                redoStacks[activeId]?.clear()
            }
        }

        val updatedFiles = current.files.map { file ->
            if (file.id == activeId) {
                file.copy(content = newContent, isModified = true)
            } else file
        }
        val updatedTabs = current.openTabs.map { tab ->
            if (tab.id == activeId) {
                tab.copy(content = newContent, isModified = true)
            } else tab
        }

        val uStack = undoStacks[activeId]
        val rStack = redoStacks[activeId]

        _uiState.update {
            it.copy(
                files = updatedFiles,
                openTabs = updatedTabs,
                canUndo = (uStack?.size ?: 0) > 1,
                canRedo = (rStack?.size ?: 0) > 0
            )
        }

        // Debounced instant auto-save to Room database
        autoSaveJob?.cancel()
        autoSaveJob = viewModelScope.launch {
            delay(500)
            repository.saveProject(_uiState.value.projectName, _uiState.value.files)
        }
    }

    fun undo() {
        val current = _uiState.value
        val activeId = current.activeFileId ?: return
        val uStack = undoStacks[activeId] ?: return
        val rStack = redoStacks.getOrPut(activeId) { ArrayDeque() }

        if (uStack.size > 1) {
            val currentContent = uStack.removeLast()
            rStack.addLast(currentContent)
            val previousContent = uStack.last()
            updateActiveFileContent(previousContent, isUndoRedoAction = true)
        }
    }

    fun redo() {
        val current = _uiState.value
        val activeId = current.activeFileId ?: return
        val uStack = undoStacks.getOrPut(activeId) { ArrayDeque() }
        val rStack = redoStacks[activeId] ?: return

        if (rStack.isNotEmpty()) {
            val redoContent = rStack.removeLast()
            uStack.addLast(redoContent)
            updateActiveFileContent(redoContent, isUndoRedoAction = true)
        }
    }

    fun toggleReadOnly() {
        _uiState.update { it.copy(isReadOnly = !it.isReadOnly) }
    }

    fun setSyntaxTheme(theme: SyntaxTheme) {
        _uiState.update { it.copy(syntaxTheme = theme) }
    }

    fun setNavTab(tab: MainNavTab) {
        _uiState.update { it.copy(activeNavTab = tab) }
        if (tab == MainNavTab.PREVIEW) {
            syncVisualElementsFromActiveFile()
        }
    }

    fun setPreviewSubMode(mode: PreviewSubMode) {
        _uiState.update { it.copy(previewSubMode = mode) }
    }

    fun toggleExplorer(open: Boolean? = null) {
        _uiState.update { it.copy(isExplorerOpen = open ?: !it.isExplorerOpen) }
    }

    fun addNewFile(fileName: String) {
        val newFile = ProjectFile(
            name = fileName,
            path = fileName,
            content = "",
            isFolder = false
        )
        undoStacks[newFile.id] = ArrayDeque(listOf(""))
        redoStacks[newFile.id] = ArrayDeque()

        _uiState.update { current ->
            val updatedFiles = current.files + newFile
            val updatedTabs = current.openTabs + newFile
            current.copy(
                files = updatedFiles,
                openTabs = updatedTabs,
                activeFileId = newFile.id,
                canUndo = false,
                canRedo = false,
                statusMessage = "Created $fileName"
            )
        }
    }

    fun importFiles(newFiles: List<ProjectFile>) {
        if (newFiles.isEmpty()) return
        newFiles.forEach { file ->
            undoStacks[file.id] = ArrayDeque(listOf(file.content))
            redoStacks[file.id] = ArrayDeque()
        }
        _uiState.update { current ->
            val updated = current.files + newFiles.filter { nf -> current.files.none { it.path == nf.path } }
            val tabs = current.openTabs + newFiles.take(3).filter { nf -> current.openTabs.none { it.path == nf.path } }
            val firstId = newFiles.firstOrNull()?.id ?: current.activeFileId
            current.copy(
                files = updated,
                openTabs = tabs,
                activeFileId = firstId,
                showOpenModal = false,
                statusMessage = "Imported ${newFiles.size} files"
            )
        }
        syncVisualElementsFromActiveFile()
    }

    fun importDirectoryWorkspace(name: String, importedFiles: List<ProjectFile>) {
        if (importedFiles.isEmpty()) return

        undoStacks.clear()
        redoStacks.clear()
        importedFiles.forEach { file ->
            undoStacks[file.id] = ArrayDeque(listOf(file.content))
            redoStacks[file.id] = ArrayDeque()
        }

        val primaryFile = importedFiles.firstOrNull { it.name.equals("index.html", ignoreCase = true) }
            ?: importedFiles.firstOrNull { it.extension.equals("html", ignoreCase = true) }
            ?: importedFiles.first()

        _uiState.update {
            it.copy(
                projectName = name,
                files = importedFiles,
                openTabs = importedFiles.take(5),
                activeFileId = primaryFile.id,
                isReadOnly = false,
                canUndo = false,
                canRedo = false,
                visualElements = emptyList(),
                selectedElementId = null,
                showOpenModal = false,
                statusMessage = "Loaded directory $name (${importedFiles.size} files)"
            )
        }

        viewModelScope.launch {
            repository.saveProject(name, importedFiles)
        }
    }

    fun deleteFile(file: ProjectFile) {
        _uiState.update { current ->
            val updatedFiles = current.files.filter { it.id != file.id }
            val updatedTabs = current.openTabs.filter { it.id != file.id }
            val nextActiveId = if (current.activeFileId == file.id) updatedTabs.firstOrNull()?.id else current.activeFileId
            current.copy(
                files = updatedFiles,
                openTabs = updatedTabs,
                activeFileId = nextActiveId,
                statusMessage = "Deleted ${file.name}"
            )
        }
    }

    // Resolves and bundles multi-file project (index.html, styles.css, script.js, svg assets) into standalone preview HTML
    fun getBundledHtml(): String {
        val current = _uiState.value
        val htmlFile = current.files.firstOrNull { it.extension == "html" || it.extension == "htm" }
            ?: current.files.firstOrNull()
            ?: return "<!DOCTYPE html><html><body style='background:#0D0D0F;color:#fff;'>Empty Workspace</body></html>"

        var html = htmlFile.content

        // Inline external CSS links that match project files
        current.files.filter { it.extension == "css" }.forEach { cssFile ->
            val linkRegex = Regex("""<link[^>]*href=["'](?:\./)?${Regex.escape(cssFile.name)}["'][^>]*>""", RegexOption.IGNORE_CASE)
            val styleTag = "<style>\n/* Inlined from ${cssFile.name} */\n${cssFile.content}\n</style>"
            if (linkRegex.containsMatchIn(html)) {
                html = html.replace(linkRegex, styleTag)
            } else if (!html.contains(cssFile.content) && cssFile.name == "styles.css") {
                // If standard styles.css is present but no link tag, append style before </head> or at beginning
                html = if (html.contains("</head>", ignoreCase = true)) {
                    html.replaceFirst(Regex("</head>", RegexOption.IGNORE_CASE), "$styleTag\n</head>")
                } else {
                    "$styleTag\n$html"
                }
            }
        }

        // Inline external JS scripts that match project files
        current.files.filter { it.extension == "js" || it.extension == "ts" }.forEach { jsFile ->
            val scriptRegex = Regex("""<script[^>]*src=["'](?:\./)?${Regex.escape(jsFile.name)}["'][^>]*>\s*</script>""", RegexOption.IGNORE_CASE)
            val scriptTag = "<script>\n// Inlined from ${jsFile.name}\n${jsFile.content}\n</script>"
            if (scriptRegex.containsMatchIn(html)) {
                html = html.replace(scriptRegex, scriptTag)
            } else if (!html.contains(jsFile.content) && jsFile.name == "script.js") {
                html = if (html.contains("</body>", ignoreCase = true)) {
                    html.replaceFirst(Regex("</body>", RegexOption.IGNORE_CASE), "$scriptTag\n</body>")
                } else {
                    "$html\n$scriptTag"
                }
            }
        }

        return html
    }

    // Code Transformations
    fun formatCurrentCode() {
        val current = _uiState.value
        val activeFile = current.files.firstOrNull { it.id == current.activeFileId } ?: return
        val formatted = CodeFormatter.formatCode(activeFile.content, activeFile.extension)
        updateActiveFileContent(formatted)
        _uiState.update { it.copy(statusMessage = "Formatted ${activeFile.name}") }
    }

    fun combineWorkspaceFiles() {
        val current = _uiState.value
        val combinedHtml = CodeFormatter.combineFiles(current.files)
        val combinedFile = ProjectFile(
            name = "bundle.html",
            path = "bundle.html",
            content = combinedHtml,
            isFolder = false
        )
        _uiState.update { curr ->
            val filtered = curr.files.filter { it.name != "bundle.html" } + combinedFile
            val tabs = curr.openTabs.filter { it.name != "bundle.html" } + combinedFile
            curr.copy(
                files = filtered,
                openTabs = tabs,
                activeFileId = combinedFile.id,
                statusMessage = "Combined workspace into unified bundle.html"
            )
        }
    }

    fun splitActiveHtml() {
        val current = _uiState.value
        val activeFile = current.files.firstOrNull { it.id == current.activeFileId } ?: return
        if (activeFile.extension != "html" && activeFile.extension != "htm") return

        val splitFiles = CodeFormatter.splitHtml(activeFile.content)
        _uiState.update { curr ->
            curr.copy(
                files = splitFiles,
                openTabs = splitFiles,
                activeFileId = splitFiles.firstOrNull()?.id,
                statusMessage = "De-embedded HTML into styles.css and script.js"
            )
        }
    }

    fun importZipBytes(zipBytes: ByteArray) {
        val extractedFiles = ZipUtils.extractArchive(zipBytes)
        if (extractedFiles.isNotEmpty()) {
            importFiles(extractedFiles)
            _uiState.update { it.copy(statusMessage = "Unpacked ${extractedFiles.size} files from archive") }
        }
    }

    // Interactive Visual Design Staging
    fun selectVisualElement(id: String?) {
        _uiState.update { it.copy(selectedElementId = id) }
    }

    fun updateVisualElement(updated: DOMElementNode) {
        _uiState.update { current ->
            val list = current.visualElements.map { if (it.id == updated.id) updated else it }
            current.copy(visualElements = list, hasUnsavedVisualChanges = true)
        }
    }

    fun addVisualElement(newElem: DOMElementNode) {
        _uiState.update { current ->
            val list = current.visualElements + newElem
            current.copy(
                visualElements = list,
                selectedElementId = newElem.id,
                hasUnsavedVisualChanges = true
            )
        }
    }

    fun deleteVisualElement(id: String) {
        _uiState.update { current ->
            val list = current.visualElements.filter { it.id != id }
            current.copy(
                visualElements = list,
                selectedElementId = if (current.selectedElementId == id) null else current.selectedElementId,
                hasUnsavedVisualChanges = true
            )
        }
    }

    fun commitVisualChangesToCode() {
        val current = _uiState.value
        val sb = StringBuilder()
        sb.append("<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n  <meta charset=\"UTF-8\">\n")
        sb.append("  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
        sb.append("  <title>T•ZERON App</title>\n")
        sb.append("  <style>\n")
        sb.append("    body { margin: 0; background: #0D0D0F; color: #ECECED; font-family: system-ui, sans-serif; position: relative; width: 100vw; height: 100vh; overflow: hidden; }\n")
        sb.append("  </style>\n</head>\n<body>\n")

        for (elem in current.visualElements) {
            val styleAttrs = elem.styles.entries.joinToString("; ") { "${it.key}: ${it.value}" }
            val inlineStyle = "position: absolute; left: ${elem.x}px; top: ${elem.y}px; width: ${elem.width}px; height: ${elem.height}px; border-radius: ${elem.borderRadius}px; opacity: ${elem.opacity}; transform: rotate(${elem.rotation}deg); $styleAttrs"
            sb.append("  <${elem.tagName} style=\"$inlineStyle\">${elem.textContent}</${elem.tagName}>\n")
        }

        sb.append("</body>\n</html>")

        val generatedHtml = sb.toString()
        val activeHtml = current.files.firstOrNull { it.extension == "html" || it.extension == "htm" }

        if (activeHtml != null) {
            val activeId = activeHtml.id
            val updatedFiles = current.files.map { if (it.id == activeId) it.copy(content = generatedHtml, isModified = true) else it }
            val updatedTabs = current.openTabs.map { if (it.id == activeId) it.copy(content = generatedHtml, isModified = true) else it }
            _uiState.update { it.copy(files = updatedFiles, openTabs = updatedTabs, hasUnsavedVisualChanges = false, statusMessage = "Saved visual changes to index.html") }
        } else {
            addNewFile("index.html")
            updateActiveFileContent(generatedHtml)
        }
    }

    fun discardVisualChanges() {
        syncVisualElementsFromActiveFile()
        _uiState.update {
            it.copy(
                hasUnsavedVisualChanges = false,
                statusMessage = "Discarded visual changes"
            )
        }
    }

    private fun syncVisualElementsFromActiveFile() {
        val current = _uiState.value
        val htmlFile = current.files.firstOrNull { it.extension == "html" || it.extension == "htm" }
        val content = htmlFile?.content ?: ""

        if (current.visualElements.isEmpty() && content.isNotBlank()) {
            val parsedElements = mutableListOf<DOMElementNode>()

            // 1. Check for starter app container or card boxes
            if (content.contains("badge", ignoreCase = true)) {
                parsedElements.add(
                    DOMElementNode(
                        id = "badge_${System.currentTimeMillis()}_0",
                        tagName = "div",
                        textContent = "120 FPS ENGINE",
                        x = 90f,
                        y = 30f,
                        width = 140f,
                        height = 28f,
                        borderRadius = 14f,
                        styles = mapOf(
                            "background" to "#1E1D22",
                            "border" to "1px solid #007ACC",
                            "color" to "#007ACC",
                            "font-size" to "11px",
                            "font-weight" to "bold",
                            "text-align" to "center"
                        )
                    )
                )
            }

            var yOffset = 70f

            Regex("""<h[1-6][^>]*>(.*?)</h[1-6]>""", RegexOption.IGNORE_CASE).findAll(content).forEach { m ->
                val cleanText = m.groupValues[1].replace(Regex("<.*?>"), "").trim()
                if (cleanText.isNotEmpty()) {
                    parsedElements.add(
                        DOMElementNode(
                            id = "h_${System.currentTimeMillis()}_${parsedElements.size}",
                            tagName = "h1",
                            textContent = cleanText,
                            x = 24f,
                            y = yOffset,
                            width = 272f,
                            height = 46f,
                            borderRadius = 8f,
                            styles = mapOf(
                                "color" to "#FFFFFF",
                                "font-size" to "22px",
                                "font-weight" to "bold",
                                "text-align" to "center"
                            )
                        )
                    )
                    yOffset += 56f
                }
            }

            Regex("""<p[^>]*>(.*?)</p>""", RegexOption.IGNORE_CASE).findAll(content).forEach { m ->
                val cleanText = m.groupValues[1].replace(Regex("<.*?>"), "").trim()
                if (cleanText.isNotEmpty()) {
                    parsedElements.add(
                        DOMElementNode(
                            id = "p_${System.currentTimeMillis()}_${parsedElements.size}",
                            tagName = "p",
                            textContent = cleanText,
                            x = 24f,
                            y = yOffset,
                            width = 272f,
                            height = 54f,
                            borderRadius = 6f,
                            styles = mapOf(
                                "color" to "#8E8D99",
                                "font-size" to "12px",
                                "text-align" to "center"
                            )
                        )
                    )
                    yOffset += 64f
                }
            }

            Regex("""<button[^>]*>(.*?)</button>""", RegexOption.IGNORE_CASE).findAll(content).forEach { m ->
                val cleanText = m.groupValues[1].replace(Regex("<.*?>"), "").trim()
                if (cleanText.isNotEmpty()) {
                    parsedElements.add(
                        DOMElementNode(
                            id = "btn_${System.currentTimeMillis()}_${parsedElements.size}",
                            tagName = "button",
                            textContent = cleanText,
                            x = 40f,
                            y = yOffset,
                            width = 240f,
                            height = 42f,
                            borderRadius = 10f,
                            styles = mapOf(
                                "background" to "#007ACC",
                                "color" to "#FFFFFF",
                                "font-size" to "13px",
                                "font-weight" to "bold",
                                "text-align" to "center"
                            )
                        )
                    )
                    yOffset += 54f
                }
            }

            if (parsedElements.isNotEmpty()) {
                _uiState.update { it.copy(visualElements = parsedElements, hasUnsavedVisualChanges = false) }
            }
        }
    }

    // Console Logging
    fun addConsoleLog(level: LogLevel, message: String) {
        val item = ConsoleLogItem(level = level, message = message)
        _uiState.update { it.copy(consoleLogs = it.consoleLogs + item) }
    }

    fun clearConsoleLogs() {
        _uiState.update { it.copy(consoleLogs = emptyList()) }
    }

    // Tunneling
    fun toggleTunnel() {
        _uiState.update { curr ->
            val nextState = !curr.isTunnelActive
            val message = if (nextState) "Live tunnel activated: ${curr.tunnelUrl}" else "Tunnel disconnected"
            curr.copy(isTunnelActive = nextState, statusMessage = message)
        }
    }

    // Modal Visibility Controls
    fun setModalState(
        newProject: Boolean = false,
        open: Boolean = false,
        format: Boolean = false,
        download: Boolean = false,
        tunnel: Boolean = false,
        syntaxTheme: Boolean = false
    ) {
        _uiState.update {
            it.copy(
                showNewProjectModal = newProject,
                showOpenModal = open,
                showFormatModal = format,
                showDownloadModal = download,
                showTunnelModal = tunnel,
                showSyntaxThemeModal = syntaxTheme
            )
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }
}
