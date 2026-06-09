# FocusFlow — Linux Port Plan

> **Purpose:** Step-by-step blueprint for adding full Linux support to FocusFlow alongside
> the existing Windows code. A new agent or developer with zero prior context should be
> able to read this document and know exactly what to do, in what order, and why.
>
> **Strategy:** Keep all existing Windows code 100% intact. Add Linux implementations as
> parallel code paths behind `isLinux` checks. The same `.jar` builds into a `.deb`
> (Linux) or `.exe`/`.msi` (Windows) via `jpackage`. No Windows user is ever affected.
>
> **Progress tracker:** See `LINUX_PORT_PROGRESS.md` for what has been done.

---

## Table of Contents
1. [Architecture Principles](#1-architecture-principles)
2. [Repository & Build Setup](#2-repository--build-setup)
3. [OS Detection Utilities](#3-os-detection-utilities)
4. [Foreground Window Detection (The Core Engine)](#4-foreground-window-detection-the-core-engine)
5. [Process Killing](#5-process-killing)
6. [Nuclear Mode — Linux Escape Process List](#6-nuclear-mode--linux-escape-process-list)
7. [Hosts File Blocking](#7-hosts-file-blocking)
8. [Network / Firewall Blocking](#8-network--firewall-blocking)
9. [Kiosk Mode — Taskbar & Window Management](#9-kiosk-mode--taskbar--window-management)
10. [Keyboard Hook (Shortcut Suppression)](#10-keyboard-hook-shortcut-suppression)
11. [Installed Apps Scanner](#11-installed-apps-scanner)
12. [App Icon Extraction](#12-app-icon-extraction)
13. [Startup Persistence (Run on Login)](#13-startup-persistence-run-on-login)
14. [Watchdog (Auto-relaunch)](#14-watchdog-auto-relaunch)
15. [Registry Lockdown — Linux Equivalent](#15-registry-lockdown--linux-equivalent)
16. [UI Adjustments for Linux](#16-ui-adjustments-for-linux)
17. [Packaging — .deb Build](#17-packaging--deb-build)
18. [Testing Checklist](#18-testing-checklist)
19. [Known Limitations](#19-known-limitations)

---

## 1. Architecture Principles

### Rule 1 — Never delete Windows code
Every existing Windows implementation stays exactly as-is. Only add `isLinux` branches.

### Rule 2 — OS detection pattern
```kotlin
// The global helpers live in WinApiBindings.kt (already exists):
val isWindows: Boolean get() = System.getProperty("os.name").lowercase().contains("windows")
val isLinux:   Boolean get() = System.getProperty("os.name").lowercase().contains("linux")
val isMac:     Boolean get() = System.getProperty("os.name").lowercase().contains("mac")

// Usage pattern throughout the codebase:
when {
    isWindows -> { /* existing Win32 code — DO NOT TOUCH */ }
    isLinux   -> { /* new Linux code goes here */ }
    // Mac is out of scope for now — leave as no-op
}
```

### Rule 3 — Graceful degradation
If a Linux feature is not yet implemented, the no-op fallback is acceptable. The app must
NEVER crash on Linux. A missing feature is fine; an exception is not.

### Rule 4 — No extra native dependencies if avoidable
Prefer calling system CLI tools (`xdotool`, `iptables`, `systemctl`) via `ProcessBuilder`
over adding JNA bindings for X11/Wayland libraries. CLI tools are universally available
on any Linux desktop distribution. JNA library bindings require the native .so to be
present on the user's machine.

### Rule 5 — Wayland vs X11
Detect which display server is running at runtime:
```kotlin
val isWayland: Boolean get() =
    System.getenv("WAYLAND_DISPLAY") != null ||
    System.getenv("XDG_SESSION_TYPE")?.lowercase() == "wayland"
val isX11: Boolean get() = !isWayland && System.getenv("DISPLAY") != null
```
Many features work on X11 but have limited or no equivalent on Wayland. Where Wayland
lacks support, the feature degrades gracefully (no crash, no enforcement for that path).

---

## 2. Repository & Build Setup

### Files to modify
- `build.gradle.kts`

### What to add

#### 2a — Linux jpackage target
Locate the `compose.desktop` block in `build.gradle.kts` and add a Linux distribution
configuration alongside the existing Windows one:

```kotlin
// Inside nativeDistributions { ... }
linux {
    packageName       = "focusflow"
    debMaintainer     = "support@focusflow.app"
    appCategory       = "Utility"
    shortcut          = true
    // Icon — provide a 512×512 PNG (not ICO)
    iconFile.set(project.file("src/main/resources/focusflow_512.png"))
    // Packages that must be installed on the user's system
    // xdotool covers X11 foreground-window detection
    // at-spi2-core covers Wayland/accessibility-based detection
    debPackageVersion = version.toString()
    // These become Depends: in the .deb control file
    // xdotool and libnotify-bin are the only runtime deps we add
}
```

#### 2b — Linux Gradle tasks
Add convenience tasks:
```
./gradlew packageDeb          → builds the .deb installer
./gradlew runLinux            → run on the current Linux machine (dev)
```

#### 2c — Ensure `sudo`-capable calls compile
Some Linux enforcement calls require `sudo` or `pkexec`. The build itself needs no changes
but the runtime must handle `PermissionDeniedException` gracefully (already done via the
`isWindows` guards pattern — replicate for Linux).

---

## 3. OS Detection Utilities

### Files to modify
- `src/main/kotlin/com/focusflow/enforcement/WinApiBindings.kt`

### What to add
After the existing `isWindows` declaration at the bottom of the file, add:

```kotlin
val isLinux:   Boolean get() = System.getProperty("os.name").lowercase().contains("linux")
val isMac:     Boolean get() = System.getProperty("os.name").lowercase().contains("mac")

val isWayland: Boolean get() = isLinux && (
    System.getenv("WAYLAND_DISPLAY") != null ||
    System.getenv("XDG_SESSION_TYPE")?.lowercase() == "wayland"
)
val isX11: Boolean get() = isLinux && !isWayland && System.getenv("DISPLAY") != null

/** Check whether xdotool is available on the PATH. */
val hasXdotool: Boolean by lazy {
    try {
        ProcessBuilder("xdotool", "version")
            .redirectErrorStream(true).start().waitFor() == 0
    } catch (_: Exception) { false }
}
```

These are used throughout all Linux code paths. Import from `WinApiBindings.kt`.

---

## 4. Foreground Window Detection (The Core Engine)

> This is the most important piece. Without it, zero enforcement happens on Linux.

### Context
On Windows, `WinEventHook.kt` uses `SetWinEventHook` (Win32) to get an instant callback
every time the foreground window changes. This drives `ProcessMonitor.onForegroundChanged()`.

On Linux, the equivalent differs by display server:
- **X11:** `xdotool getactivewindow getwindowpid` — reliable, ~10ms latency
- **Wayland:** No universal equivalent. Use AT-SPI2 accessibility bus (limited) or polling.

### Files to modify
- `src/main/kotlin/com/focusflow/enforcement/WinEventHook.kt`
- `src/main/kotlin/com/focusflow/enforcement/ProcessMonitor.kt`

### Step 4a — Read WinEventHook.kt
Read the full file first. It has a `start(callback: (String, Long) -> Unit)` and `stop()`
interface. The Linux implementation must expose the same interface.

### Step 4b — Add Linux foreground poller inside WinEventHook.kt

Add a Linux polling implementation at the bottom of `WinEventHook.kt`:

```kotlin
// ── Linux foreground window poller ────────────────────────────────────────
// Uses xdotool on X11. On Wayland, falls back to ProcessHandle polling.
// Poll interval: 500ms — matches the Windows fallback poll rate.

private var linuxPollJob: Job? = null
private val linuxScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

private fun startLinuxPoller(callback: (String, Long) -> Unit) {
    var lastPid = -1L
    linuxPollJob = linuxScope.launch {
        while (isActive) {
            val (name, pid) = getLinuxForegroundProcess() ?: run {
                delay(500); return@run null to -1L
            } ?: continue

            if (pid != lastPid && pid > 0) {
                lastPid = pid
                callback(name, pid)
            }
            delay(500)
        }
    }
}

private fun stopLinuxPoller() {
    linuxPollJob?.cancel()
    linuxPollJob = null
}

/**
 * Returns (processName, pid) of the currently focused window on Linux.
 * X11: uses xdotool (fast, reliable).
 * Wayland: uses /proc scanning to find the most recently active process
 *          that owns a visible window (heuristic — less reliable).
 */
fun getLinuxForegroundProcess(): Pair<String, Long>? {
    return when {
        isX11 && hasXdotool -> getX11ForegroundProcess()
        isWayland            -> getWaylandForegroundProcessFallback()
        else                 -> null
    }
}

private fun getX11ForegroundProcess(): Pair<String, Long>? {
    return try {
        val proc = ProcessBuilder(
            "xdotool", "getactivewindow", "getwindowpid"
        ).redirectErrorStream(true).start()
        val pidStr = proc.inputStream.bufferedReader().readText().trim()
        proc.waitFor()
        val pid = pidStr.toLongOrNull() ?: return null
        val handle = ProcessHandle.of(pid).orElse(null) ?: return null
        val name = handle.info().command().orElse(null)
            ?.substringAfterLast("/") ?: return null
        Pair(name, pid)
    } catch (_: Exception) { null }
}

/**
 * Wayland fallback: find the most recently modified process that has an
 * open file descriptor to a Wayland socket. This is a heuristic and is
 * NOT as reliable as xdotool on X11. It is better than nothing.
 */
private fun getWaylandForegroundProcessFallback(): Pair<String, Long>? {
    return try {
        // qdbus / gdbus approaches are DE-specific; use xdotool via XWayland
        // if DISPLAY is available (many Wayland sessions have XWayland running)
        if (System.getenv("DISPLAY") != null && hasXdotool) {
            return getX11ForegroundProcess()
        }
        null  // no reliable fallback without DE-specific IPC
    } catch (_: Exception) { null }
}
```

Then modify the existing `start()` and `stop()` methods to branch:
```kotlin
fun start(callback: (String, Long) -> Unit) {
    when {
        isWindows -> { /* existing Win32 SetWinEventHook code — untouched */ }
        isLinux   -> startLinuxPoller(callback)
    }
}

fun stop() {
    when {
        isWindows -> { /* existing Win32 unhook code — untouched */ }
        isLinux   -> stopLinuxPoller()
    }
}

val isActive: Boolean get() = when {
    isWindows -> /* existing hookHandle check */
    isLinux   -> linuxPollJob?.isActive == true
    else      -> false
}
```

### Step 4c — ProcessMonitor.kt
`ProcessMonitor.start()` already does `if (isWindows) { WinEventHook.start { ... } }`.
Change this to:
```kotlin
if (isWindows || isLinux) {
    WinEventHook.start { pName, pid -> onForegroundChanged(pName, pid) }
}
```

`ProcessMonitor.tickPoll()` has `if (!isWindows) return` at the top. Remove this guard
so the polling fallback also runs on Linux:
```kotlin
// BEFORE:
private suspend fun tickPoll() {
    if (!isWindows) return
    // ...
}

// AFTER:
private suspend fun tickPoll() {
    if (!isWindows && !isLinux) return
    // rest of method unchanged — getForegroundProcessNameAndPid() will use
    // the Linux path via WinApiBindings
}
```

### Step 4d — getForegroundProcessNameAndPid() in WinApiBindings.kt
This function currently only works on Windows. Add a Linux branch:
```kotlin
fun getForegroundProcessNameAndPid(): Pair<String, Long>? {
    return when {
        isWindows -> { /* existing User32Extra code — untouched */ }
        isLinux   -> WinEventHook.getLinuxForegroundProcess()
        else      -> null
    }
}
```

---

## 5. Process Killing

### Status: Already works on Linux ✅
`killProcessByName()` and `killProcessByPid()` in `WinApiBindings.kt` already have
non-Windows fallbacks using `ProcessHandle.destroyForcibly()`. No changes needed.

The only change: on Linux, process names don't have `.exe` suffixes. The `ProcessMonitor`
compares names case-insensitively — this is fine. The UI shows whatever name is detected.

---

## 6. Nuclear Mode — Linux Escape Process List

### Files to modify
- `src/main/kotlin/com/focusflow/enforcement/NuclearMode.kt`

### What to change
The `escapeProcesses` set contains only `.exe` names. These won't match on Linux.
Add a parallel Linux set and use `when`:

```kotlin
private val escapeProcesses: Set<String> get() = when {
    isWindows -> windowsEscapeProcesses
    isLinux   -> linuxEscapeProcesses
    else      -> emptySet()
}

private val windowsEscapeProcesses = setOf(
    // existing list — DO NOT TOUCH
    "taskmgr.exe", "procexp.exe", /* ... all existing entries ... */
)

private val linuxEscapeProcesses = setOf(
    // Process managers
    "gnome-system-monitor", "ksysguard", "lxtask", "xfce4-taskmanager",
    "htop", "top", "btop", "glances",
    // Terminals
    "gnome-terminal", "konsole", "xterm", "xfterm4", "lxterminal",
    "xfce4-terminal", "mate-terminal", "tilix", "alacritty", "kitty",
    "wezterm", "terminator", "rxvt", "urxvt", "st", "foot",
    "bash", "zsh", "fish", "sh", "dash",
    // File managers (can open terminal)
    "nautilus", "thunar", "dolphin", "nemo", "pcmanfm",
    // Package managers / installers
    "synaptic", "gdebi", "gnome-software", "discover",
    // Text editors (could edit system files)
    "gedit", "kate", "mousepad", "pluma",
    // System config tools
    "dconf-editor", "gnome-tweaks", "unity-tweak-tool",
    // Display server tools
    "xkill", "wmctrl"
)

// knownEscapePathSuffixes — Linux equivalents
private val knownEscapePathSuffixes: Set<String> get() = when {
    isWindows -> windowsEscapePathSuffixes
    isLinux   -> linuxEscapePathSuffixes
    else      -> emptySet()
}

private val windowsEscapePathSuffixes = setOf(
    // existing set — DO NOT TOUCH
)

private val linuxEscapePathSuffixes = setOf(
    "/usr/bin/bash",
    "/usr/bin/zsh",
    "/usr/bin/fish",
    "/bin/bash",
    "/bin/sh",
    "/usr/bin/gnome-terminal",
    "/usr/bin/konsole",
    "/usr/bin/xterm",
    "/usr/bin/gnome-system-monitor",
    "/usr/bin/htop",
    "/usr/bin/btop"
)
```

Also modify `getRunningEscapeProcesses()`:
```kotlin
private fun getRunningEscapeProcesses(): Set<String> {
    return when {
        isWindows -> getRunningEscapeProcessesWindows()   // existing tasklist method renamed
        isLinux   -> getRunningEscapeProcessesFallback()  // already exists — uses ProcessHandle
        else      -> emptySet()
    }
}
```

And `killAndLog()` — the batch kill on Linux should use `kill -9` via ProcessHandle,
not `taskkill`. The existing `killProcessByName()` already handles this.

---

## 7. Hosts File Blocking

### Files to modify
- `src/main/kotlin/com/focusflow/services/HostsBlocker.kt`

### What to change
This is the easiest fix in the whole project. Two changes:

**Change 1 — Hardcoded path:**
```kotlin
// BEFORE:
private const val HOSTS_PATH = "C:\\Windows\\System32\\drivers\\etc\\hosts"

// AFTER:
private val HOSTS_PATH: String get() = when {
    isWindows -> "C:\\Windows\\System32\\drivers\\etc\\hosts"
    else      -> "/etc/hosts"   // Linux, Mac
}
```

**Change 2 — Remove `isWindows` guards from all methods:**
Every public method currently starts with `if (!isWindows) return ...`. Replace these
with `if (!isWindows && !isLinux) return ...` — or better, just remove the check
entirely since `/etc/hosts` is standard on all Unix systems.

**Change 3 — DNS cache flush:**
```kotlin
private fun flushDnsCache() {
    when {
        isWindows -> {
            // existing ipconfig /flushdns — untouched
        }
        isLinux -> {
            // Try common Linux DNS cache flush methods in order:
            try { ProcessBuilder("systemd-resolve", "--flush-caches")
                .redirectErrorStream(true).start().waitFor() } catch (_: Exception) {}
            try { ProcessBuilder("resolvectl", "flush-caches")
                .redirectErrorStream(true).start().waitFor() } catch (_: Exception) {}
            // nscd (older systems)
            try { ProcessBuilder("nscd", "-i", "hosts")
                .redirectErrorStream(true).start().waitFor() } catch (_: Exception) {}
        }
    }
}
```

**Change 4 — canWriteHostsFile():**
```kotlin
fun canWriteHostsFile(): Boolean {
    return try { java.io.File(HOSTS_PATH).canWrite() } catch (_: Exception) { false }
}
```
This already works cross-platform — just remove the `if (!isWindows) return false` guard.

**Change 5 — BlockResult.NotWindows:**
Rename to `BlockResult.NotSupported` or add a `BlockResult.NoPermission` so Linux users
get a meaningful error when `/etc/hosts` isn't writable (needs `sudo`).

---

## 8. Network / Firewall Blocking

### Files to modify
- `src/main/kotlin/com/focusflow/enforcement/NetworkBlocker.kt`

### Context
On Windows, `NetworkBlocker` uses PowerShell `New-NetFirewallRule` to create outbound-deny
rules. On Linux, the equivalent is `iptables` (older) or `nftables` (modern, default on
Debian 12+, Ubuntu 24+). Both require `sudo` or `pkexec`.

Since firewall rules require root, this feature should:
1. Check if `sudo` is available without a password (`sudo -n true`)
2. If not, show a notification asking the user to grant passwordless sudo for the
   specific `iptables` command (document this in a setup screen)
3. Degrade gracefully if not available — blocking still works via process kill,
   just without network-level enforcement

### What to add

Add a new private section to `NetworkBlocker.kt`:

```kotlin
// ── Linux iptables implementation ─────────────────────────────────────────

fun isRunningAsAdmin(): Boolean = when {
    isWindows -> { /* existing PowerShell check — untouched */ }
    isLinux   -> canRunSudoWithoutPassword()
    else      -> false
}

private fun canRunSudoWithoutPassword(): Boolean {
    return try {
        val proc = ProcessBuilder("sudo", "-n", "true")
            .redirectErrorStream(true).start()
        proc.waitFor() == 0
    } catch (_: Exception) { false }
}

fun addRule(processName: String): Boolean {
    return when {
        isWindows -> addRuleWindows(processName)   // existing method renamed
        isLinux   -> addRuleLinux(processName)
        else      -> false
    }
}

private fun addRuleLinux(processName: String): Boolean {
    if (!canRunSudoWithoutPassword()) return false
    val baseName = processName.removeSuffix(".exe").trim()
    if (activeRules.contains(baseName.lowercase())) return true

    // Find full path of the process executable
    val exePath = resolveExePathLinux(baseName) ?: run {
        pendingRules.add(baseName.lowercase())
        return false
    }

    return try {
        // iptables owner match: block outbound traffic from this executable
        // Requires iptables-extensions with --cmd-owner (kernel module xt_owner)
        val proc = ProcessBuilder(
            "sudo", "-n", "iptables", "-A", "OUTPUT",
            "-m", "owner", "--cmd-owner", baseName,
            "-j", "DROP",
            "-m", "comment", "--comment", "FocusFlow_Block_$baseName"
        ).redirectErrorStream(true).start()
        val exit = proc.waitFor()
        if (exit == 0) { activeRules.add(baseName.lowercase()); true } else false
    } catch (_: Exception) { false }
}

private fun resolveExePathLinux(baseName: String): String? {
    // Check if already running
    ProcessHandle.allProcesses()
        .filter { it.info().command().isPresent }
        .forEach { ph ->
            val cmd = ph.info().command().get()
            if (cmd.substringAfterLast("/").equals(baseName, ignoreCase = true)) {
                return cmd
            }
        }
    // Check common Linux install paths
    listOf("/usr/bin", "/usr/local/bin", "/opt", "/snap/bin", "/flatpak").forEach { dir ->
        val f = java.io.File(dir, baseName)
        if (f.exists()) return f.absolutePath
    }
    return null
}

fun removeRule(processName: String) {
    when {
        isWindows -> removeRuleWindows(processName)  // existing method renamed
        isLinux   -> removeRuleLinux(processName)
    }
}

private fun removeRuleLinux(processName: String) {
    val baseName = processName.removeSuffix(".exe").trim()
    try {
        // Remove all iptables rules with our comment marker
        ProcessBuilder(
            "sudo", "-n", "iptables", "-D", "OUTPUT",
            "-m", "owner", "--cmd-owner", baseName,
            "-j", "DROP",
            "-m", "comment", "--comment", "FocusFlow_Block_$baseName"
        ).redirectErrorStream(true).start().waitFor()
    } catch (_: Exception) {}
    activeRules.remove(baseName.lowercase())
    pendingRules.remove(baseName.lowercase())
}

fun syncFromFirewall() {
    when {
        isWindows -> syncFromFirewallWindows()  // existing method renamed
        isLinux   -> syncFromFirewallLinux()
    }
}

private fun syncFromFirewallLinux() {
    if (!canRunSudoWithoutPassword()) return
    try {
        val proc = ProcessBuilder(
            "sudo", "-n", "iptables", "-L", "OUTPUT", "-n", "--line-numbers"
        ).redirectErrorStream(true).start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        activeRules.clear()
        output.lineSequence()
            .filter { it.contains("FocusFlow_Block_") }
            .forEach { line ->
                val name = line.substringAfter("FocusFlow_Block_")
                    .substringBefore(" ").trim()
                if (name.isNotBlank()) activeRules.add(name.lowercase())
            }
    } catch (_: Exception) {}
}
```

---

## 9. Kiosk Mode — Taskbar & Window Management

### Files to modify
- `src/main/kotlin/com/focusflow/services/FocusLauncherService.kt`

### Context
On Windows, `hideTaskbar()` calls `ShowWindow(Shell_TrayWnd, SW_HIDE)` via JNA/User32.
On Linux, there is no single "taskbar" concept — each desktop environment has its own:
- GNOME: GNOME Shell extension or `gdbus` call to hide the panel
- KDE Plasma: D-Bus call to hide the panel
- XFCE: `xfconf-query` to hide the panel
- LXDE/LXQt: similar `lxpanel` tools

The universal approach that works everywhere: use EWMH (Extended Window Manager Hints)
to make the FocusFlow window fullscreen and always-on-top. This covers the taskbar without
hiding it — the Compose window simply covers it. Combined with NuclearMode killing the
terminal/file-manager escape routes, this achieves equivalent effect.

For a harder lock, attempt `wmctrl` to make the window "sticky" and above everything.

### What to add

```kotlin
private fun hideTaskbar() {
    when {
        isWindows -> hideTaskbarWindows()   // existing FindWindowW code renamed
        isLinux   -> hideTaskbarLinux()
    }
}

private fun showTaskbar() {
    when {
        isWindows -> showTaskbarWindows()   // existing FindWindowW code renamed
        isLinux   -> showTaskbarLinux()
    }
}

/**
 * Linux: we don't literally hide the taskbar (DE-specific, fragile).
 * Instead we mark the FocusFlow window as fullscreen + always-on-top so it
 * covers everything. The Compose window manager handles the fullscreen part;
 * this call ensures EWMH hints are set for window managers that need them.
 * 
 * Optionally tries to auto-hide the panel via wmctrl if available.
 */
private fun hideTaskbarLinux() {
    if (!isLinux) return
    try {
        // Set the FocusFlow window as fullscreen via EWMH
        if (hasXdotool) {
            // Get our own window ID and set fullscreen
            val pid = ProcessHandle.current().pid()
            ProcessBuilder("xdotool", "search", "--pid", pid.toString(),
                "set_window", "--name", "FocusFlow",
                "windowfocus")
                .redirectErrorStream(true).start()
        }
        // Attempt wmctrl always-on-top + fullscreen
        ProcessBuilder("wmctrl", "-r", "FocusFlow", "-b",
            "add,fullscreen,above")
            .redirectErrorStream(true).start()
    } catch (_: Exception) {}
}

private fun showTaskbarLinux() {
    if (!isLinux) return
    try {
        ProcessBuilder("wmctrl", "-r", "FocusFlow", "-b",
            "remove,fullscreen,above")
            .redirectErrorStream(true).start()
    } catch (_: Exception) {}
}
```

Note: The actual fullscreen state is managed by the Compose `WindowPlacement.Fullscreen`
in `Main.kt` — this is already cross-platform. The Linux taskbar hide is best-effort.

---

## 10. Keyboard Hook (Shortcut Suppression)

### Files to modify
- `src/main/kotlin/com/focusflow/enforcement/GlobalKeyboardHook.kt`

### Context
This is the hardest piece. On Windows, `WH_KEYBOARD_LL` is a system-wide hook that
intercepts ALL keyboard events before they reach any application.

On Linux:
- **X11:** Can grab the keyboard via `XGrabKeyboard` (JNA) or evdev (`/dev/input/event*`)
- **Wayland:** Intentionally impossible from user space — Wayland's security model
  prevents one app from intercepting another's keyboard events

Given the complexity of JNA X11 bindings, the recommended approach is:

**Phase 1 (Easy):** Use `xdotool key --clearmodifiers` to suppress specific key combos
by remapping them via `xdotool` subprocess calls when detected. Not a true hook — we
periodically detect if a blocked combo was pressed and undo it. Crude but works.

**Phase 2 (Proper, later):** Use `evdev` JNA binding to open `/dev/input/event*` with
`EVIOCGRAB` ioctl — this grabs the device exclusively. Requires the user to be in the
`input` group or have a udev rule.

For now, implement Phase 1 and document Phase 2 as a follow-up.

```kotlin
// In GlobalKeyboardHook.kt, inside enable():
fun enable() {
    when {
        isWindows -> enableWindows()   // existing SetWindowsHookExW code renamed
        isLinux   -> enableLinux()
        // isWayland → no-op with a log warning
    }
}

fun disable() {
    when {
        isWindows -> disableWindows()  // existing code renamed
        isLinux   -> disableLinux()
    }
}

// Linux Phase 1: use xbindkeys config to suppress shortcuts
// Writes a temporary ~/.focusflow-xbindkeys config and starts xbindkeys
private var xbindkeysProcess: Process? = null

private fun enableLinux() {
    if (!isX11) return   // Wayland: no-op
    try {
        // Write a minimal xbindkeys config that suppresses our target shortcuts
        val configFile = java.io.File(System.getProperty("user.home"), ".focusflow-xbindkeys")
        configFile.writeText("""
            # FocusFlow keyboard suppression — auto-generated, do not edit
            "echo suppressed"
              Super_L
            "echo suppressed"
              Super_R
            "echo suppressed"
              Alt+Tab
            "echo suppressed"
              Alt+F4
        """.trimIndent())
        xbindkeysProcess = ProcessBuilder("xbindkeys", "-f",
            configFile.absolutePath, "--nodaemon")
            .redirectErrorStream(true).start()
    } catch (_: Exception) {
        // xbindkeys not installed — log and continue without key suppression
    }
}

private fun disableLinux() {
    xbindkeysProcess?.destroyForcibly()
    xbindkeysProcess = null
    try {
        java.io.File(System.getProperty("user.home"), ".focusflow-xbindkeys").delete()
    } catch (_: Exception) {}
}

val isActive: Boolean get() = when {
    isWindows -> hookHandle != null   // existing
    isLinux   -> xbindkeysProcess?.isAlive == true
    else      -> false
}
```

> **Note for future agent:** Phase 2 (proper evdev grab) should be implemented once
> Phase 1 is validated. The evdev approach requires:
> 1. Finding all keyboard devices in `/dev/input/event*` via `/proc/bus/input/devices`
> 2. Opening each with `O_RDWR | O_NONBLOCK`
> 3. Calling `ioctl(fd, EVIOCGRAB, 1)` to grab exclusively
> 4. Filtering events and forwarding non-suppressed ones to a virtual uinput device

---

## 11. Installed Apps Scanner

### Files to modify
- `src/main/kotlin/com/focusflow/enforcement/InstalledAppsScanner.kt`

### Context
On Windows, this scans `HKLM\Software\Microsoft\Windows\CurrentVersion\Uninstall` and
common `Program Files` directories. On Linux, installed apps are described by `.desktop`
files in `/usr/share/applications/` and `~/.local/share/applications/`.

### What to add

Read the existing file first to understand its data structures, then add:

```kotlin
fun scan(): List<InstalledApp> {
    return when {
        isWindows -> scanWindows()   // existing code renamed
        isLinux   -> scanLinux()
        else      -> emptyList()
    }
}

private fun scanLinux(): List<InstalledApp> {
    val apps = mutableListOf<InstalledApp>()
    val searchDirs = listOf(
        java.io.File("/usr/share/applications"),
        java.io.File(System.getProperty("user.home"), ".local/share/applications"),
        java.io.File("/var/lib/flatpak/exports/share/applications"),
        java.io.File(System.getProperty("user.home"), ".local/share/flatpak/exports/share/applications"),
        java.io.File("/snap/bin")
    )

    searchDirs.filter { it.exists() }.forEach { dir ->
        dir.walkTopDown()
            .filter { it.extension == "desktop" }
            .forEach { desktopFile ->
                parseDesktopFile(desktopFile)?.let { apps.add(it) }
            }
    }
    return apps.distinctBy { it.processName }
}

private fun parseDesktopFile(file: java.io.File): InstalledApp? {
    val props = mutableMapOf<String, String>()
    file.forEachLine { line ->
        val trimmed = line.trim()
        if (trimmed.startsWith("#") || trimmed.startsWith("[")) return@forEachLine
        val idx = trimmed.indexOf('=')
        if (idx > 0) props[trimmed.substring(0, idx).trim()] = trimmed.substring(idx + 1).trim()
    }

    val name = props["Name"] ?: return null
    val exec = props["Exec"] ?: return null
    val hidden = props["Hidden"]?.lowercase() == "true"
    val noDisplay = props["NoDisplay"]?.lowercase() == "true"
    if (hidden || noDisplay) return null

    // Extract the binary name from the Exec field (strip args and field codes)
    val processName = exec
        .substringBefore(" ")          // drop arguments
        .substringAfterLast("/")       // drop path prefix
        .replace(Regex("%[a-zA-Z]"), "").trim()  // drop %f %u etc.
        .takeIf { it.isNotBlank() } ?: return null

    return InstalledApp(
        displayName = name,
        processName = processName,
        exePath     = exec.substringBefore(" ")
    )
}
```

---

## 12. App Icon Extraction

### Files to modify
- `src/main/kotlin/com/focusflow/enforcement/AppIconExtractor.kt`

### Context
On Windows, icons are extracted from PE executable resources. On Linux, icons are
referenced in `.desktop` files and stored as `.png`/`.svg` in `/usr/share/icons/` and
`/usr/share/pixmaps/`.

### What to add

```kotlin
fun extract(processName: String): ImageBitmap? {
    return when {
        isWindows -> extractWindows(processName)  // existing code renamed
        isLinux   -> extractLinux(processName)
        else      -> null
    }
}

private fun extractLinux(processName: String): ImageBitmap? {
    // Search for the icon in common Linux icon locations
    val iconName = processName.removeSuffix(".exe").lowercase()
    val iconDirs = listOf(
        "/usr/share/pixmaps",
        "/usr/share/icons/hicolor/48x48/apps",
        "/usr/share/icons/hicolor/64x64/apps",
        "/usr/share/icons/hicolor/128x128/apps",
        "/usr/share/icons/hicolor/256x256/apps"
    )
    val extensions = listOf(".png", ".svg", ".xpm", "")

    for (dir in iconDirs) {
        for (ext in extensions) {
            val f = java.io.File(dir, "$iconName$ext")
            if (f.exists()) {
                return try {
                    org.jetbrains.skia.Image.makeFromEncoded(f.readBytes())
                        .toComposeImageBitmap()
                } catch (_: Exception) { null }
            }
        }
    }
    return null
}
```

---

## 13. Startup Persistence (Run on Login)

### Files to modify
- `src/main/kotlin/com/focusflow/enforcement/WindowsStartupManager.kt`

### Context
On Windows, `WindowsStartupManager` writes to the `HKCU\...\Run` registry key.
On Linux, the XDG autostart spec uses `~/.config/autostart/<app>.desktop` files.

### What to add

```kotlin
fun enable() {
    when {
        isWindows -> enableWindows()  // existing registry code renamed
        isLinux   -> enableLinux()
    }
}

fun disable() {
    when {
        isWindows -> disableWindows() // existing code renamed
        isLinux   -> disableLinux()
    }
}

private fun enableLinux() {
    val autostartDir = java.io.File(System.getProperty("user.home"), ".config/autostart")
    autostartDir.mkdirs()
    val desktopFile = java.io.File(autostartDir, "focusflow.desktop")
    val exePath = ProcessHandle.current().info().command().orElse("focusflow")
    desktopFile.writeText("""
        [Desktop Entry]
        Type=Application
        Name=FocusFlow
        Exec=$exePath
        Hidden=false
        NoDisplay=false
        X-GNOME-Autostart-enabled=true
        Comment=FocusFlow productivity enforcement
    """.trimIndent())
}

private fun disableLinux() {
    java.io.File(System.getProperty("user.home"),
        ".config/autostart/focusflow.desktop").delete()
}

fun isEnabled(): Boolean {
    return when {
        isWindows -> isEnabledWindows()  // existing code renamed
        isLinux   -> java.io.File(System.getProperty("user.home"),
            ".config/autostart/focusflow.desktop").exists()
        else      -> false
    }
}
```

---

## 14. Watchdog (Auto-relaunch)

### Files to modify
- `src/main/kotlin/com/focusflow/enforcement/WatchdogInstaller.kt`

### Context
On Windows, a Windows Task Scheduler task relaunches FocusFlow every 2 minutes if it
isn't running. On Linux, a `systemd` user service does the equivalent.

### What to add

```kotlin
fun install() {
    when {
        isWindows -> installWindows()  // existing schtasks code renamed
        isLinux   -> installLinux()
    }
}

fun uninstall() {
    when {
        isWindows -> uninstallWindows()
        isLinux   -> uninstallLinux()
    }
}

private fun installLinux() {
    // Use systemd user service if systemd is available
    if (isSystemdAvailable()) {
        installSystemdWatchdog()
    } else {
        // Fallback: cron job
        installCronWatchdog()
    }
}

private fun isSystemdAvailable(): Boolean {
    return try {
        ProcessBuilder("systemctl", "--user", "is-system-running")
            .redirectErrorStream(true).start().waitFor() != 127  // 127 = command not found
    } catch (_: Exception) { false }
}

private fun installSystemdWatchdog() {
    val serviceDir = java.io.File(System.getProperty("user.home"),
        ".config/systemd/user")
    serviceDir.mkdirs()

    val exePath = ProcessHandle.current().info().command().orElse("focusflow")
    val serviceFile = java.io.File(serviceDir, "focusflow-watchdog.service")
    serviceFile.writeText("""
        [Unit]
        Description=FocusFlow Watchdog — relaunches FocusFlow if stopped
        After=graphical-session.target

        [Service]
        Type=simple
        ExecStart=$exePath
        Restart=on-failure
        RestartSec=120

        [Install]
        WantedBy=default.target
    """.trimIndent())

    try {
        ProcessBuilder("systemctl", "--user", "daemon-reload")
            .redirectErrorStream(true).start().waitFor()
        ProcessBuilder("systemctl", "--user", "enable", "--now",
            "focusflow-watchdog.service")
            .redirectErrorStream(true).start().waitFor()
    } catch (_: Exception) {}
}

private fun installCronWatchdog() {
    val exePath = ProcessHandle.current().info().command().orElse("focusflow")
    val cronLine = "*/2 * * * * pgrep -x focusflow > /dev/null || $exePath &"
    try {
        val existing = ProcessBuilder("crontab", "-l")
            .redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText()
        if (!existing.contains("focusflow-watchdog")) {
            val updated = "$existing\n$cronLine  # focusflow-watchdog\n"
            val proc = ProcessBuilder("crontab", "-")
                .redirectErrorStream(true).start()
            proc.outputStream.bufferedWriter().use { it.write(updated) }
            proc.waitFor()
        }
    } catch (_: Exception) {}
}

private fun uninstallLinux() {
    // Remove systemd service
    try {
        ProcessBuilder("systemctl", "--user", "disable", "--now",
            "focusflow-watchdog.service")
            .redirectErrorStream(true).start().waitFor()
        java.io.File(System.getProperty("user.home"),
            ".config/systemd/user/focusflow-watchdog.service").delete()
    } catch (_: Exception) {}
    // Remove cron entry
    try {
        val existing = ProcessBuilder("crontab", "-l")
            .redirectErrorStream(true).start()
            .inputStream.bufferedReader().readText()
        val cleaned = existing.lines()
            .filter { !it.contains("focusflow-watchdog") }
            .joinToString("\n")
        val proc = ProcessBuilder("crontab", "-")
            .redirectErrorStream(true).start()
        proc.outputStream.bufferedWriter().use { it.write(cleaned) }
        proc.waitFor()
    } catch (_: Exception) {}
}
```

---

## 15. Registry Lockdown — Linux Equivalent

### Files to modify
- `src/main/kotlin/com/focusflow/enforcement/RegistryLockdown.kt`

### Context
On Windows, `RegistryLockdown` disables Task Manager and sign-out via registry policy.
On Linux there is no registry. The equivalent would be:
1. Disabling the keyboard shortcut for the system monitor (e.g., Ctrl+Alt+Del → no-op)
2. Locking the screen saver / session manager

However, these require DE-specific calls and are complex to implement universally.

**Recommendation:** Leave `RegistryLockdown` as a no-op on Linux for now. The combination
of NuclearMode (kills system monitors), GlobalKeyboardHook (suppresses shortcuts), and
kiosk fullscreen window is already a strong enough enforcement stack on Linux.

If needed in the future, implement:
- GNOME: `gsettings set org.gnome.settings-daemon.plugins.media-keys screenshot ''`
  to disable screenshot shortcuts, etc.
- KDE: `kwriteconfig5 --file kglobalshortcutsrc ...`

For now, `enable()` and `disable()` simply return on non-Windows — already the case.

---

## 16. UI Adjustments for Linux

### Files to modify
- `src/main/kotlin/com/focusflow/ui/screens/WindowsSetupScreen.kt`
- `src/main/kotlin/com/focusflow/ui/screens/BlockDefenseScreen.kt`
- `src/main/kotlin/com/focusflow/ui/components/OsBanner.kt`
- `src/main/kotlin/com/focusflow/ui/screens/VpnNetworkScreen.kt`

### Changes needed

**OsBanner.kt:**
Currently shows a "Windows only" banner on non-Windows systems. Update to show a
"Linux (beta)" banner instead when `isLinux` is true.

**WindowsSetupScreen.kt:**
This screen explains how to set up admin rights and Task Scheduler on Windows.
Duplicate it or add an `isLinux` branch showing equivalent Linux setup instructions:
- How to grant passwordless sudo for iptables
- How to install xdotool (`sudo apt install xdotool`)
- Confirming the user is in the `input` group for evdev (Phase 2)

**BlockDefenseScreen.kt:**
Nuclear Mode, VPN blocking, and Registry Lockdown sections each have Windows-specific
descriptions. Wrap them with OS-aware text:
```kotlin
if (isWindows) {
    Text("Disables Task Manager, Registry Editor, PowerShell...")
} else if (isLinux) {
    Text("Kills system monitors, terminals, and file managers...")
}
```

**VpnNetworkScreen.kt:**
The firewall section should mention `iptables` instead of `Windows Firewall` on Linux.

---

## 17. Packaging — .deb Build

### Files to modify
- `build.gradle.kts`

### What to add

In the `nativeDistributions` block:

```kotlin
linux {
    packageName    = "focusflow"
    debMaintainer  = "TBTechs <support@focusflow.app>"
    appCategory    = "Utility"
    shortcut       = true
    iconFile.set(project.file("src/main/resources/focusflow_512.png"))
}
```

You also need a **512×512 PNG icon** at `src/main/resources/focusflow_512.png`.
The existing `focusflow_256.png` can be upscaled or a new one created.

### Build commands (Linux host required)
```bash
./gradlew packageDeb    # produces build/compose/binaries/main/deb/focusflow_*.deb
./gradlew packageRpm    # optional: RPM for Fedora/RHEL
```

### GitHub Actions — add Linux build job

Add to `.github/workflows/build-linux.yml`:
```yaml
name: Build Linux
on: [push, workflow_dispatch]
jobs:
  build-deb:
    runs-on: ubuntu-22.04
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          java-version: '19'
          distribution: 'temurin'
      - name: Install xdotool (for testing)
        run: sudo apt-get install -y xdotool wmctrl xbindkeys
      - name: Build .deb
        run: ./gradlew packageDeb
      - uses: actions/upload-artifact@v4
        with:
          name: focusflow-linux-deb
          path: build/compose/binaries/main/deb/*.deb
```

---

## 18. Testing Checklist

Before marking the Linux port complete, verify each of the following on an actual Linux
desktop (Ubuntu 22.04 LTS and Debian 12 are the primary targets):

### Display Server Detection
- [ ] `isWayland` returns true on a Wayland session
- [ ] `isX11` returns true on an X11 session
- [ ] `hasXdotool` returns true when xdotool is installed

### Foreground Detection
- [ ] Opening a blocked app triggers `onForegroundChanged()` within 1 second (X11)
- [ ] `ProcessMonitor.blockedAttempts` increments when a blocked app is opened
- [ ] The block overlay shows on top of the blocked app

### Process Killing
- [ ] A blocked process is killed within 1 second of gaining focus
- [ ] A process killed by name is gone (check `pgrep`)

### Hosts File Blocking
- [ ] `blockDomain("reddit.com")` writes to `/etc/hosts` (run with sudo)
- [ ] `curl reddit.com` returns connection refused after blocking
- [ ] `unblockDomain("reddit.com")` removes the entries

### Nuclear Mode
- [ ] Opening `gnome-terminal` during a Nuclear Mode session kills it
- [ ] Opening `gnome-system-monitor` during Nuclear Mode kills it

### Kiosk Mode
- [ ] FocusFlow window goes fullscreen on `FocusLauncherService.enter()`
- [ ] Taskbar is covered (visually) during kiosk mode
- [ ] Exiting kiosk mode returns the window to normal

### Startup & Watchdog
- [ ] `~/.config/autostart/focusflow.desktop` is created when enabled
- [ ] FocusFlow relaunches after being killed (watchdog test)
- [ ] `systemctl --user status focusflow-watchdog.service` shows active

### Packaging
- [ ] `./gradlew packageDeb` succeeds without errors
- [ ] `sudo dpkg -i focusflow_*.deb` installs cleanly
- [ ] App launches from the application menu
- [ ] App icon appears correctly

---

## 19. Known Limitations

| Feature | Linux Status | Notes |
|---|---|---|
| Keyboard hook (full) | ⚠️ Partial (X11 only, xbindkeys Phase 1) | Wayland intentionally blocks this at OS level |
| Registry lockdown | ❌ No equivalent | No-op on Linux — by design |
| Taskbar hiding | ⚠️ Approximate (fullscreen cover) | True hide requires DE-specific APIs |
| Network blocking | ⚠️ Requires passwordless sudo | User must configure sudoers |
| App icon extraction | ⚠️ Partial (.png only) | SVG icons not supported in Skia without extra lib |
| Wayland foreground detection | ⚠️ Heuristic only | Wayland security model prevents proper API |

---

*Document version: 1.0 — June 2026*
*Created by: FocusFlow development team*
