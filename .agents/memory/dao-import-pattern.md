---
name: DAO wildcard import requirement
description: Every file outside com.focusflow.data that calls DAO extension functions must import com.focusflow.data.* not just Database
---

## Rule
Any file not in `com.focusflow.data` package that calls DAO extension functions (e.g. `Database.getTasks()`, `Database.getSetting()`, `Database.getBlockRules()`) must have:

```kotlin
import com.focusflow.data.*
```

NOT just `import com.focusflow.data.Database`.

**Why:** The DAO functions are top-level extension functions defined in `TaskDao.kt`, `SessionDao.kt`, `BlockingDao.kt`, `SettingsDao.kt`, `HabitDao.kt`, `ReportingDao.kt` — all in package `com.focusflow.data`. Kotlin requires these to be explicitly imported; importing `Database` alone does not bring them into scope.

**How to apply:** When creating new files that call any `Database.*` method, always use `import com.focusflow.data.*`. When editing existing files, replace any bare `import com.focusflow.data.Database` with `import com.focusflow.data.*`.

## BlockingDao early-return pattern
Functions like `upsertDailyUsage` and `deleteDailyUsageBefore` that use `synchronized(this)` with an early `!isReady` guard must use **block body** syntax, not expression body, and use `return@synchronized` for the early exit:

```kotlin
// CORRECT
fun Database.upsertFoo(...) {
    synchronized(this) {
        if (!isReady) return@synchronized
        // ...
    }
}

// WRONG — inferred return type mismatch
fun Database.upsertFoo(...) = synchronized(this) {
    if (!isReady) return   // compile error: returns Unit, body returns Int
    // ...
}
```
