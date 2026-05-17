package com.focusflow.enforcement

import com.sun.jna.Native
import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.sun.jna.win32.StdCallLibrary

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

    // ── Registry paths ────────────────────────────────────────────────────────

    private const val SYSTEM_HKCU   = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\System"
    private const val EXPLORER_HKCU = "Software\\Microsoft\\Windows\\CurrentVersion\\Policies\\Explorer"
    private const val SYSTEM_HKLM   = "SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Policies\\System"

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

    // ── Internal helpers ──────────────────────────────────────────────────────

    private fun trySet(root: WinReg.HKEY, path: String, name: String, value: Int) {
        try { Advapi32Util.registrySetIntValue(root, path, name, value) } catch (_: Throwable) {}
    }

    private fun tryDelete(root: WinReg.HKEY, path: String, name: String) {
        try { Advapi32Util.registryDeleteValue(root, path, name) } catch (_: Throwable) {}
    }

    private fun tryRefreshPolicies() {
        try { PolicyUser32.INSTANCE.UpdatePerUserSystemParameters(0, true) } catch (_: Throwable) {}
    }
}
