# FocusFlow — Limitations, Missing Features & Gap Analysis

_Last updated: May 2026_

---

## 1. Platform Limitations (Windows-Only Features)

FocusFlow is built for **Windows**. On Linux or macOS the UI renders but
enforcement is silently skipped — no blocking actually occurs.

| Feature | Windows | Linux/macOS | Notes |
|---------|---------|-------------|-------|
| Process killing (app blocking) | ✅ Full | ❌ Skipped | Uses JNA/Win32 `TerminateProcess` |
| WinEvent foreground hook | ✅ Full | ❌ Skipped | `SetWinEventHook` Win32 API |
| Network blocking (netsh) | ✅ Full | ❌ Skipped | Requires Windows netsh + admin |
| Nuclear Mode (kill Task Manager) | ✅ Full | ❌ Skipped | Win32 process enumeration |
| System tray icon | ✅ Full | ⚠️ Partial | AWT tray works on most Linux DEs |
| Windows startup registry | ✅ Full | ❌ N/A | Registry key `HKCU\...\Run` |
| App sound aversion (audio) | ✅ Full | ⚠️ Partial | Javax Sound may vary by OS |

**Impact**: If a user runs FocusFlow on macOS or Linux, the app launches and
the UI is fully functional, but none of the blocking enforcement works. There
is currently no warning or banner telling the user their blocking is inactive.

**Recommendation**: Add an OS detection banner in Settings and FocusScreen
that clearly warns non-Windows users.

---

## 2. Enforcement Gaps

### 2a. Browser-Level Blocking
FocusFlow kills entire browser processes (`chrome.exe`, `firefox.exe`) but
cannot block **individual websites** inside a running browser. There is no
DNS-level or hosts-file blocking. A user can:
- Open a browser (it gets killed immediately if listed)
- Use a private/incognito instance of an unlisted browser
- Use Microsoft Edge (which may not be in the blocklist)

**Recommendation**: Add optional hosts-file blocking for domain-level website
blocking, or document this limitation prominently.

### 2b. Admin Privilege Not Enforced at Startup
Nuclear Mode and network blocking require administrator privileges. If the
app runs as a standard user, `netsh` commands fail silently and Nuclear Mode
process terminations may be denied by Windows UAC.

**Recommendation**: Detect privilege level at startup and show a prompt to
re-launch as admin when Nuclear Mode or network blocking is enabled.

### 2c. Process Name Exact Match Only
Block rules match by exact process name (e.g. `chrome.exe`). Renamed
processes, portable apps, or apps launched from unusual paths bypass blocking.

**Recommendation**: Add fuzzy/window-title matching and path-based rules.

### 2d. VPN / Proxy Bypass
The `NetworkBlocker` uses `netsh advfirewall` rules. A user with a VPN or
proxy app can bypass network blocks entirely.

---

## 3. Missing User-Facing Guides

The following help content is **not present** in the app:

| Missing Guide | Where Needed | Priority |
|--------------|-------------|----------|
| First-run onboarding wizard | App startup (first launch) | High |
| What is app blocking? | Settings → Block Rules | High |
| How does Pomodoro work? | Focus screen toggle | Medium |
| What is Nuclear Mode? | Settings → Nuclear Mode toggle | High |
| How does daily allowance work? | Settings → Allowances | Medium |
| What happens during a session? | Focus screen setup | Medium |
| Exporting and backing up data | Profile → Export | Low |
| How streaks are calculated | Dashboard / Stats | Low |

**Recommendation**: Add an in-app help system (collapsible info tooltips or a
`?` icon that opens a help dialog) for each major feature.

---

## 4. Missing Setup / Onboarding

### 4a. No First-Run Experience
When the app is launched for the first time, the user is immediately shown the
Dashboard with no explanation of what the app does or how to configure it.
There is no:
- Welcome screen
- Feature tour / walkthrough
- Initial configuration (name, daily goal)
- Prompt to add block rules

### 4b. No Empty-State Guidance on Dashboard
If no tasks, no sessions, and no block rules exist, the Dashboard shows empty
cards without explaining what to do next.

---

## 5. Missing Permissions & Security Disclosures

FocusFlow performs sensitive operations without any in-app disclosure:

| Operation | Current State | Should Have |
|-----------|--------------|-------------|
| Terminating third-party processes | Silent | User consent dialog on first use |
| Modifying Windows Firewall rules | Silent | Disclosure + admin elevation prompt |
| Writing to Windows registry (startup) | Silent | Explicit opt-in toggle (partially exists) |
| Nuclear Mode (kills system utilities) | Toggle with no warning | Strong warning dialog before enabling |
| Reading all running process names | Silent | Privacy notice in Settings |

**Recommendation**: Add a **Permissions & Privacy** section to Settings that
lists what the app does and why.

---

## 6. Missing Terms of Use & Privacy Policy

For Microsoft Store submission (or any public distribution):

- ❌ No End User License Agreement (EULA) in-app or bundled
- ❌ No Privacy Policy (required for Store submission)
- ❌ No Terms of Service
- ❌ No acknowledgement that the app kills system processes

**Required action before Store submission**:
1. Write a Privacy Policy stating: all data is stored locally, no telemetry,
   no accounts, no network calls made by the app itself.
2. Write a EULA clarifying that the app is provided as-is and the user
   accepts responsibility for any productivity-enforcement decisions.
3. Host the Privacy Policy at a public URL (required by Partner Center).

---

## 7. Data & Backup Gaps

| Gap | Details |
|-----|---------|
| No automatic backup | Data exists only in `~/.focusflow/focusflow.db` — one corrupt write = data loss |
| No cloud sync | Sessions/tasks cannot be accessed from another machine |
| No import from other apps | Cannot import from Todoist, Notion, etc. |
| Export is manual only | CSV export requires user action; no scheduled backup |
| No database migration system | Schema changes between versions risk breaking existing data |

---

## 8. UX / Accessibility Gaps

| Gap | Details |
|-----|---------|
| No keyboard shortcuts | Every action requires mouse click; power users expect `Ctrl+N` for new task, etc. |
| No dark/light theme toggle | App is always dark mode; some users need light mode |
| No font size setting | No accessibility scaling for vision impairment |
| Colour is only indicator for priority | Colour-blind users can't distinguish high/medium/low priority without the label |
| No screen reader support | Compose Desktop has limited accessibility tree support on Windows |
| Minimum window size not enforced | At very small window sizes, layout breaks without horizontal scrolling |

---

## 9. What the App Cannot Do (Summary)

- Block websites (only entire browser processes)
- Run on macOS or Linux with enforcement active
- Sync data across devices
- Import tasks from external tools
- Send email/calendar reminders (notifications are Windows-only toasts)
- Schedule focus sessions in advance (only block schedules by time-of-day)
- Track focus sessions across multiple user accounts on the same machine
- Operate without a graphical desktop (headless/SSH mode)
- Block apps that run as SYSTEM or inside a sandboxed/VM environment
