package com.focusflow.enforcement

import com.sun.jna.Callback
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/**
 * WinEventHook
 *
 * Replaces the 500ms polling loop with a Windows event hook using SetWinEventHook.
 * EVENT_SYSTEM_FOREGROUND fires instantly when any window comes to the foreground.
 * This is the JVM equivalent of Android's AccessibilityService onWindowStateChanged().
 *
 * Uses WINEVENT_OUTOFCONTEXT so the callback runs on THIS thread (via GetMessage pump),
 * not the target app's thread — no special privileges required.
 *
 * The hook thread runs a Win32 message pump (GetMessage/DispatchMessage loop).
 * Shutdown sends WM_QUIT via PostThreadMessageW using the REAL Win32 thread ID
 * obtained from Kernel32.GetCurrentThreadId() — NOT the JVM thread ID (they differ!).
 *
 * Falls back to polling (ProcessMonitor) if hook registration fails.
 */
object WinEventHook {

    private const val EVENT_SYSTEM_FOREGROUND = 0x0003
    private const val WINEVENT_OUTOFCONTEXT   = 0x0000
    private const val WM_QUIT                 = 0x0012

    interface WinHookUser32 : StdCallLibrary {
        fun SetWinEventHook(
            eventMin: Int, eventMax: Int,
            hmodWinEventProc: Pointer?,
            lpfnWinEventProc: WinEventProc,
            idProcess: Int, idThread: Int,
            dwFlags: Int
        ): Pointer?

        fun UnhookWinEvent(hWinEventHook: Pointer?): Boolean

        fun PostThreadMessageW(idThread: Int, msg: Int, wParam: Long, lParam: Long): Boolean

        companion object {
            val INSTANCE: WinHookUser32 = Native.load(
                "user32", WinHookUser32::class.java, W32APIOptions.DEFAULT_OPTIONS
            )
        }
    }

    interface WinEventProc : Callback {
        fun callback(
            hWinEventHook: Pointer?, event: Int, hwnd: WinDef.HWND?,
            idObject: Int, idChild: Int, dwEventThread: Int, dwmsEventTime: Int
        )
    }

    @Volatile private var hookPtr: Pointer? = null
    @Volatile private var running = false
    @Volatile private var win32ThreadId: Int = 0   // Real Win32 thread ID (NOT JVM thread ID)
    private var hookThread: Thread? = null

    var isActive: Boolean = false
        private set

    fun start(onForegroundChange: (String) -> Unit) {
        if (running || !isWindows) return
        running = true

        hookThread = Thread({
            // CRITICAL: Get the Win32 thread ID via Kernel32.GetCurrentThreadId(),
            // NOT the JVM thread ID. JVM thread IDs are internal sequential counters
            // that are completely different from OS-level Win32 thread IDs.
            win32ThreadId = try {
                Kernel32.INSTANCE.GetCurrentThreadId()
            } catch (_: Exception) { 0 }

            val proc = object : WinEventProc {
                override fun callback(
                    hWinEventHook: Pointer?, event: Int, hwnd: WinDef.HWND?,
                    idObject: Int, idChild: Int, dwEventThread: Int, dwmsEventTime: Int
                ) {
                    if (hwnd == null) return
                    try {
                        val pidRef = IntByReference()
                        User32.INSTANCE.GetWindowThreadProcessId(hwnd, pidRef)
                        ProcessHandle.of(pidRef.value.toLong())
                            .flatMap { it.info().command() }
                            .ifPresent { cmd ->
                                val name = cmd.substringAfterLast('\\').substringAfterLast('/')
                                onForegroundChange(name)
                            }
                    } catch (_: Exception) {}
                }
            }

            hookPtr = WinHookUser32.INSTANCE.SetWinEventHook(
                EVENT_SYSTEM_FOREGROUND, EVENT_SYSTEM_FOREGROUND,
                null, proc, 0, 0, WINEVENT_OUTOFCONTEXT
            )

            isActive = hookPtr != null

            val msg = WinUser.MSG()
            while (running) {
                val ret = User32.INSTANCE.GetMessage(msg, null, 0, 0)
                if (ret <= 0) break
                User32.INSTANCE.TranslateMessage(msg)
                User32.INSTANCE.DispatchMessage(msg)
            }

            hookPtr?.let { WinHookUser32.INSTANCE.UnhookWinEvent(it) }
            hookPtr = null
            isActive = false
        }, "WinEventHook-Pump")

        hookThread!!.isDaemon = true
        hookThread!!.start()
    }

    fun stop() {
        running = false
        // Send WM_QUIT to the Win32 message pump using the real Win32 thread ID.
        // This correctly exits GetMessage() and terminates the pump loop.
        val tid = win32ThreadId
        if (tid != 0) {
            try {
                WinHookUser32.INSTANCE.PostThreadMessageW(tid, WM_QUIT, 0L, 0L)
            } catch (_: Exception) {}
        }
        hookThread?.join(1000)
        hookThread = null
        win32ThreadId = 0
        isActive = false
    }
}
