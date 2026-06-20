package com.focusflow.enforcement

import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.atomic.AtomicBoolean

/**
 * RegistryLockdown
 *
 * Applies / removes Windows Group Policy registry values that prevent the user
 * from escaping a Focus Launcher session through OS-level escape hatches.
 *
 * Policies applied while kiosk is active
 * ───────────────────────────────────────
 *   HKCU …\Policies\System!DisableTaskMgr      = 1
 *       Hides Task Manager — user cannot kill FocusFlow via Ctrl+Shift+Esc or
 *       right-click-taskbar even if the keyboard hook somehow misses it.
 *       Does NOT require admin; takes effect immediately for current user.
 *
 *   HKCU …\Policies\Explorer!NoLogOff          = 1
 *       Removes "Sign out" from the Start menu and the Ctrl+Alt+Del screen.
 *       Without this, a user can still log off and switch to another account.
 *       Does NOT require admin.
 *
 *   HKLM …\Policies\System!HideFastUserSwitching = 1
 *       Removes the "Switch user" button from the lock/sign-in screen and the
 *       Ctrl+Alt+Del overlay.  Requires either admin or SYSTEM privileges;
 *       silently skipped when FocusFlow is not elevated.
 *
 * All values are deleted (not zeroed) on disable so they leave no trace in the
 * user's policy configuration after a session ends.
 *
 * Limitations
 * ───────────
 *   Ctrl+Alt+Del itself cannot be blocked from user mode — it is a kernel-level
 *   Secure Attention Sequence. With NoLogOff + HideFastUserSwitching in place the
 *   Ctrl+Alt+Del screen still appears but it offers no useful escape route
 *   (Lock, Task Manager, and Sign out are all suppressed).
 */
object RegistryLockdown {

    private val isWindows = System.getProperty("os.name").lowercase().contains("windows")

    /**
     * Guard so we only register one JVM shutdown hook per process.
     * AtomicBoolean so the check-and-set in registerShutdownHook() is truly
     * atomic. A plain @Volatile bool allows two concurrent enable() calls to
     * both read false and both register duplicate shutdown hooks.
     */
    private val shutdownHookRegistered = AtomicBoolean(false)

    // ── Registry paths ────────────────────────────────────────────────────────

    private const val SYSTEM_HKCU   = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System"
    private const val EXPLORER_HKCU = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer"
    private const val SYSTEM_HKLM   = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System"

    /**
     * Path to the Desktop settings key that controls low-level hook timeout.
     * HKCU\Control Panel\Desktop\LowLevelHooksTimeout (REG_SZ, decimal milliseconds)
     * Windows default is ~200–300 ms. JVM GC pauses can exceed this, causing Windows
     * to silently unregister WH_KEYBOARD_LL hooks mid-session.
     */
    private const val DESKTOP_HKCU       = "Control Panel\\Desktop"
    private const val HOOKS_TIMEOUT_NAME = "LowLevelHooksTimeout"
    private const val HOOKS_TIMEOUT_MS   = "1000"

    /**
     * Original value of LowLevelHooksTimeout before we raised it — restored on exit.
     * Null means the key did not exist before we set it (delete it on restore).
     */
    @Volatile private var savedHooksTimeout:    String?  = null

    /**
     * True only after [setLowLevelHooksTimeout] successfully wrote to the registry.
     * Guards [restoreLowLevelHooksTimeout] so a failed or skipped set does not
     * attempt a restore (and potentially corrupt the user's registry state).
     */
    @Volatile private var hooksTimeoutModified: Boolean  = false

    // ── Minimal user32 binding for policy refresh ─────────────────────────────

    private interface PolicyUser32 : StdCallLibrary {
        /**
         * Tells the Windows Shell to re-read per-user system parameters and
         * Group Policy registry values — equivalent to GPUpdate for in-process
         * Explorer.  Flags: 0 = no extra action; fWinIni: true = broadcast
         * WM_SETTINGCHANGE so apps pick up the change.
         */
        fun UpdatePerUserSystemParameters(dwFlags: Int, fWinIni: Boolean): Boolean

        companion object {
            val INSTANCE: PolicyUser32 by lazy {
                Native.load("user32", PolicyUser32::class.java)
            }
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Apply all lockdown registry values and signal the shell to reload policies.
     * Safe to call multiple times — idempotent writes.
     * Must be called from a thread that is allowed to block briefly.
     */
    fun enable() {
        if (!isWindows) return
        // HKCU writes — never need elevation
        trySet(WinReg.HKEY_CURRENT_USER, SYSTEM_HKCU,   "DisableTaskMgr", 1)
        trySet(WinReg.HKEY_CURRENT_USER, EXPLORER_HKCU, "NoLogOff",       1)
        // HKLM write — requires admin; silently skipped when not elevated
        trySet(WinReg.HKEY_LOCAL_MACHINE, SYSTEM_HKLM,  "HideFastUserSwitching", 1)
        tryRefreshPolicies()
        // Register a JVM shutdown hook the very first time we apply lockdown.
        // The hook runs on: normal exit, System.exit(), SIGTERM, and uncaught crash
        // exits — guaranteeing the registry is cleaned up even if safetyCleanup()
        // itself throws. (SIGKILL / power loss are handled by the startup janitor
        // in Main.kt instead, since shutdown hooks cannot run then.)
        registerShutdownHook()
    }

    /**
     * Remove all lockdown values and signal the shell to reload.
     * Called at session exit, break start, kill-switch activation, and crash recovery.
     * Never throws — every operation is individually guarded.
     */
    fun disable() {
        if (!isWindows) return
        tryDelete(WinReg.HKEY_CURRENT_USER,  SYSTEM_HKCU,   "DisableTaskMgr")
        tryDelete(WinReg.HKEY_CURRENT_USER,  EXPLORER_HKCU, "NoLogOff")
        tryDelete(WinReg.HKEY_LOCAL_MACHINE, SYSTEM_HKLM,   "HideFastUserSwitching")
        tryRefreshPolicies()
    }

    /**
     * Raise [LowLevelHooksTimeout] to 1 000 ms for the duration of a kiosk session.
     *
     * Windows silently unregisters WH_KEYBOARD_LL hooks whose callbacks do not return
     * within LowLevelHooksTimeout milliseconds (default ~200–300 ms on most systems).
     * A JVM GC pause during the keyboard hook callback can easily exceed the default,
     * causing Windows to tear down the hook mid-session with no notification.
     *
     * This call saves the current value (or absence) and raises it.  The KioskWatchdog
     * already reinstalls the hook when it detects death; this is the complementary
     * proactive measure — making the hook less likely to die in the first place.
     *
     * Call once at kiosk [enter()]. Pair with [restoreLowLevelHooksTimeout] at exit.
     * Separate from [enable] / [disable] so it is NOT re-applied on every watchdog
     * tick (those call enable() repeatedly for idempotent policy re-assertion).
     */
    fun setLowLevelHooksTimeout() {
        if (!isWindows || hooksTimeoutModified) return
        try {
            val keyExists = Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, DESKTOP_HKCU)
            val valExists = keyExists &&
                Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, DESKTOP_HKCU, HOOKS_TIMEOUT_NAME)

            val current = if (valExists)
                Advapi32Util.registryGetStringValue(WinReg.HKEY_CURRENT_USER, DESKTOP_HKCU, HOOKS_TIMEOUT_NAME)
            else null

            // If the existing value is already >= our target, leave it untouched.
            val currentMs = current?.toLongOrNull() ?: 0L
            if (currentMs >= HOOKS_TIMEOUT_MS.toLong()) return

            savedHooksTimeout   = current    // null → key did not exist
            hooksTimeoutModified = true
            Advapi32Util.registrySetStringValue(
                WinReg.HKEY_CURRENT_USER, DESKTOP_HKCU, HOOKS_TIMEOUT_NAME, HOOKS_TIMEOUT_MS
            )
        } catch (e: Throwable) {
            EnforcementLog.warn("RegistryLockdown", "Failed to raise LowLevelHooksTimeout", e)
        }
    }

    /**
     * Restore [LowLevelHooksTimeout] to the value it had before [setLowLevelHooksTimeout].
     *
     * If the key did not exist before the session, it is deleted.  If it had a
     * value, that exact value is restored.  If [setLowLevelHooksTimeout] was never
     * called or failed, this is a no-op.
     *
     * Call once at kiosk [exit()] and in [emergencyRestoreWindows].
     */
    fun restoreLowLevelHooksTimeout() {
        if (!isWindows || !hooksTimeoutModified) return
        try {
            val original = savedHooksTimeout
            if (original == null) {
                tryDelete(WinReg.HKEY_CURRENT_USER, DESKTOP_HKCU, HOOKS_TIMEOUT_NAME)
            } else {
                Advapi32Util.registrySetStringValue(
                    WinReg.HKEY_CURRENT_USER, DESKTOP_HKCU, HOOKS_TIMEOUT_NAME, original
                )
            }
        } catch (e: Throwable) {
            EnforcementLog.warn("RegistryLockdown", "Failed to restore LowLevelHooksTimeout", e)
        } finally {
            savedHooksTimeout    = null
            hooksTimeoutModified = false
        }
    }

    // ── Orphan-key detection ──────────────────────────────────────────────────

    /**
     * Checks whether [DisableTaskMgr] is stuck in the registry when it should
     * not be. Call this ONLY after the caller has confirmed that both nuclear
     * mode and kiosk/launcher mode are currently OFF (this function does not
     * import those services to avoid a circular package dependency).
     *
     * Conservative two-pass design to eliminate false positives:
     *   1. First read  — confirms the value exists AND equals 1.
     *   2. Fresh [disable] pass — attempts silent auto-recovery.
     *   3. Second read — if STILL 1 after cleanup, returns true (stuck).
     *
     * Returns false immediately on non-Windows or if first read shows nothing.
     * Silent auto-recovery (passes 1+2 succeed) also returns false — no dialog
     * is needed because the OS state was already corrected.
     *
     * Must be called from a background (IO) thread — JNA registry reads block.
     */
    fun detectOrphanedKeys(): Boolean {
        if (!isWindows) return false

        fun readDisableTaskMgr(): Boolean = try {
            Advapi32Util.registryKeyExists(WinReg.HKEY_CURRENT_USER, SYSTEM_HKCU) &&
            Advapi32Util.registryValueExists(WinReg.HKEY_CURRENT_USER, SYSTEM_HKCU, "DisableTaskMgr") &&
            Advapi32Util.registryGetIntValue(WinReg.HKEY_CURRENT_USER, SYSTEM_HKCU, "DisableTaskMgr") == 1
        } catch (_: Throwable) { false }

        // Pass 1 — nothing stuck? Nothing to do, definitely no false positive.
        if (!readDisableTaskMgr()) return false

        // Key is stuck — run a fresh cleanup pass (may silently succeed).
        disable()

        // Pass 2 — still stuck even after cleanup? Caller should prompt admin.
        return readDisableTaskMgr()
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Register a one-time JVM shutdown hook that calls [disable].
     * Idempotent — subsequent calls after the first are no-ops.
     * The hook thread is a daemon so it never prevents a clean JVM exit.
     */
    private fun registerShutdownHook() {
        if (!shutdownHookRegistered.compareAndSet(false, true)) return
        try {
            Runtime.getRuntime().addShutdownHook(
                Thread({ try { disable() } catch (_: Throwable) {} },
                    "RegistryLockdown-Shutdown")
                    .also { it.isDaemon = true }
            )
        } catch (_: Throwable) {
            // IllegalStateException if JVM is already shutting down — safe to ignore.
        }
    }


    private fun trySet(root: WinReg.HKEY, path: String, name: String, value: Int) {
        try { Advapi32Util.registrySetIntValue(root, path, name, value) }
        catch (e: Throwable) { EnforcementLog.warn("RegistryLockdown", "Failed to set $path\\$name=$value", e) }
    }

    private fun tryDelete(root: WinReg.HKEY, path: String, name: String) {
        try { Advapi32Util.registryDeleteValue(root, path, name) }
        catch (e: Throwable) { EnforcementLog.warn("RegistryLockdown", "Failed to delete $path\\$name", e) }
    }

    private fun tryRefreshPolicies() {
        try { PolicyUser32.INSTANCE.UpdatePerUserSystemParameters(0, true) }
        catch (e: Throwable) { EnforcementLog.warn("RegistryLockdown", "UpdatePerUserSystemParameters failed", e) }
    }
}
