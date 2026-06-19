---
name: Bug fixes audit round 10 — second health-check sweep continuation
description: 4 genuine bugs fixed; full audit of the remaining health-check items with detailed false-positive analysis.
---

## Fixes applied

### JMX physical memory reflection — unsafe Long cast (2 files)
- **ResourceMonitorService.kt** lines 187-188: `(totalMethod.invoke(osMx) as Long)` → `(... as Number).toLong()`
- **CrashReporter.kt** lines 246-247: same fix
- **Why:** `getTotalPhysicalMemorySize` / `getFreePhysicalMemorySize` are accessed via reflection to avoid importing the internal `com.sun.management` package. The reflection `invoke()` returns `Object`. On HotSpot the boxed type is `java.lang.Long`, but other JVM vendors (OpenJ9, GraalVM native, some JRE builds for ARM) may return `java.lang.Integer` or another `Number` subtype. Casting directly to `Long` throws `ClassCastException` on those JVMs. Casting to `Number` first and calling `toLong()` works for any numeric type.
- **How to apply:** Any reflective JMX/JVM stat that returns a primitive numeric type should always go through `as Number).toLong()` rather than `as Long`.

### NuclearMode tasklist OOM guard
- **NuclearMode.kt** `getRunningEscapeProcesses()` line 120: replaced `readText()` with a capped 256 KB manual-read loop (`CharArray(8_192)`, `totalChars > 262_144` break).
- **Why:** `readText()` is unbounded. In a pathological environment (e.g., fuzzing tool that spawns thousands of transient processes), the tasklist CSV output could grow large enough to cause an OOM. The 256 KB cap is ~4 000 processes — impossibly large for any real machine — so normal detection is unaffected.
- **How to apply:** Any unbounded `readText()` / `readBytes()` on a child-process stream in a hot loop should have a size cap.

### FocusLauncherOverlay icon smart-cast / !! elimination
- **FocusLauncherOverlay.kt** `AppTile` composable:
  - Renamed `var icon by remember { mutableStateOf<ImageBitmap?>(null) }` → `var iconState by ...`
  - LaunchedEffect now assigns to `iconState`
  - Before the `when` block, added `val icon = iconState` to capture an immutable local val
  - Changed `icon != null -> Image(bitmap = icon!!, ...)` → `icon != null -> Image(bitmap = icon, ...)` (smart-cast now works)
- **Why:** `iconState` is a Compose `MutableState` delegate. Kotlin cannot smart-cast mutable properties because the compiler cannot prove the value hasn't changed between the `!= null` check and the use site. In the old code, `icon!!` was technically unsafe — although race-prone null assignment from a background coroutine was unlikely in practice (the IO coroutine posts back to the main thread before writing `iconState`), the `!!` still produced a `KotlinNullPointerException`-risk warning and would crash if null slipped through. Capturing a local `val icon = iconState` freezes the snapshot and enables the Kotlin compiler's smart-cast.
- **How to apply:** Any `mutableStateOf` delegate where you need to null-check and then use the value — capture a local val first.

## Items confirmed already correct (no changes needed)

| Item | Location | Why it's fine |
|---|---|---|
| `scheduledTime!!` null assertion | TaskAlarmService.kt:109 | Inside `try { LocalTime.parse(task.scheduledTime!!, ...) } catch (_: Exception) { continue }` — NPE caught |
| NotificationService icon null | NotificationService.kt | Delegates to `SystemTrayManager.showNotification()` → `trayIcon?.displayMessage(...)` (safe call, no `!!`) |
| FocusLauncherOverlay `while(true)` | FocusLauncherOverlay.kt:91 | Fixed round 9 — already `while (isActive)` |
| WinEventHook / GlobalKeyboardHook thread `!!` | both | Fixed round 9 — local val pattern |
| FloatingBlockOverlay unsafe Graphics2D cast | FloatingBlockOverlay.kt | Fixed round 9 — safe cast + `?: return` |
| SoundAversion LineUnavailableException | SoundAversion.kt:289 | `tryPlay()` already catches `LineUnavailableException` and falls back to `Toolkit.beep()` |
| AutoBackupService safe restore | AutoBackupService.kt:186-218 | `restoreBackup()` already takes a VACUUM INTO safety snapshot before overwriting and rolls back on failure |
| InstalledAppsScanner per-key isolation | InstalledAppsScanner.kt:207 | `catch (_: Exception) { /* malformed key — skip */ }` is inside the inner `for (sub in subkeys)` loop — each key's failure is isolated |
| WinApiBindings FD leaks | WinApiBindings.kt | Fixed round 9 — DISCARD redirects |
| NetworkBlocker FD leaks + output cap | NetworkBlocker.kt | Fixed round 9 — DISCARD + 64 KB cap on `runPowerShellAndRead()` |
| NuclearMode batch kill streams | NuclearMode.kt:190-197 | Already uses DISCARD + `runCatching { p.outputStream.close() }` |
