================================================================================
  FocusFlow Emergency Recovery Tool  v1.0.3
  by TBTechs  |  Last-resort escape hatch for locked-down machines
================================================================================

WHAT THIS TOOL DOES
-------------------
If FocusFlow crashed or left your PC in a locked state (hidden taskbar, blocked
websites, firewall rules still active), this tool fixes all of it in one click:

  1. Restores the Windows Taskbar (primary + secondary)
  2. Clears all FocusFlow enforcement flags in the database
     (crash guard, hard-lock, Nuclear Mode, Kill Switch state)
  3. Removes all FocusFlow_Block_* Windows Firewall rules
  4. Removes all FocusFlow entries from the hosts file
  5. Flushes the DNS cache so unblocked sites resolve immediately


HOW TO RUN
----------
Option A — Normal Windows:
  Double-click FocusFlow-Recovery.exe.
  For best results, right-click > "Run as administrator" before clicking
  the button (required for firewall rules and hosts file changes).

Option B — From a USB Drive:
  Copy this entire folder (FocusFlow-Recovery.exe + README.txt) to a USB
  drive. Plug it into the affected machine and double-click the EXE.
  No installation required — the tool is fully self-contained.

Option C — Windows Safe Mode:
  1. Boot into Safe Mode (hold Shift while clicking Restart, then
     Troubleshoot > Advanced options > Startup Settings > Restart,
     press F4 for Safe Mode).
  2. Plug in your USB drive.
  3. Open File Explorer, navigate to the USB drive, and double-click
     FocusFlow-Recovery.exe.
  4. Click "Run Recovery Now" and wait for all steps to complete.
  5. Restart your PC normally.


ADMIN RIGHTS
------------
The tool works without admin rights, but two steps require elevation:

  - Remove Firewall Rules  (needs admin to modify Windows Firewall)
  - Clean Hosts File       (needs write access to System32\drivers\etc\hosts)

If you see FAILED on either of those steps, close the tool, right-click the
EXE, select "Run as administrator", and run recovery again.


WHAT GETS CHANGED
-----------------
  Database (~/.focusflow/focusflow.db):
    launcher_crash_guard       -> "false"
    launcher_hard_locked       -> "false"
    nuclear_mode               -> "false"
    killswitch_remaining_today -> "300"  (daily budget reset)
    killswitch_reset_date      -> ""

  Windows Firewall:
    All rules whose display name starts with "FocusFlow_Block_" are deleted.

  Hosts file (C:\Windows\System32\drivers\etc\hosts):
    All lines ending with "# FocusFlow" are removed.

  Taskbar:
    ShowWindow() is called on Shell_TrayWnd and Shell_SecondaryTrayWnd
    with SW_SHOW. This is a no-op if the taskbar was already visible.

  Nothing else is touched. Your FocusFlow data, tasks, habits, and
  focus history are not affected.


AFTER RUNNING
-------------
You do not need to uninstall or reinstall FocusFlow. Once recovery is
complete, simply restart FocusFlow normally. All your data is intact.

If the issue persists after running this tool, please contact support or
visit the FocusFlow GitHub page.


SYSTEM REQUIREMENTS
-------------------
  - Windows 10 or Windows 11 (x64)
  - No Java installation needed (runtime is bundled)
  - ~150 MB free space to run (no install)


================================================================================
  FocusFlow  |  https://github.com/TBTechs/focusflow
================================================================================
