---
    name: Bug fixes audit round 12 — 4-tool deep scan
    description: Four bugs found and fixed via parallel code-review, LSP diagnostics, security scan, and threat model. All compile-verified (BUILD SUCCESSFUL).
    ---

    ## Bugs fixed

    ### 1. SessionDao.kt — Missing rs.next() before getInt()
    getTotalFocusMinutesToday() called it.getInt(1) without it.next() first.
    Fix: if (it.next()) it.getInt(1) else 0

    ### 2. FocusSessionService.kt — start/end INSERT OR REPLACE race
    start() and end() both fire Database.insertSession() on IO coroutines.
    If end()'s write lands first, start()'s write (endTime=null, completed=false) overwrites the finalized row.
    Fix: @Volatile startInsertJob saved in start(); end() joins it (pendingStartInsert?.join()) inside its IO coroutine before writing.

    ### 3. NuclearMode.kt — disable() unconditional set cancels concurrent enable()'s monitorJob
    disable() used _isActiveAtomic.set(false) — not CAS.
    Race: enable() CAS→true, launches monitorJob; disable() fires, sets false, cancels that new monitorJob → nuclear mode logically active but enforcement stopped.
    Fix: if (!_isActiveAtomic.compareAndSet(true, false)) return

    ### 4. DailyAllowanceTracker.kt — lastTickMs not updated on ProcessHandle exception
    Exception on allProcesses() caused early return without updating lastTickMs.
    Next tick computed elapsed from old timestamp — double/triple-charges foreground allowance.
    Fix: lastTickMs = System.currentTimeMillis() before return in catch.

    ## Security scan
    - SAST: 0 findings. HoundDog: 0 findings.
    - Dep audit: 0 critical/high; 1 moderate (esbuild path-traversal, dev-only mockup sandbox, not shipped).

    ## Other
    - threat_model.md written to project root.
    - All fixes: BUILD SUCCESSFUL.

    **Why:** Non-obvious concurrency bugs — especially the DB race and NuclearMode CAS gap — needed careful analysis to fix safely without breaking other features.
    