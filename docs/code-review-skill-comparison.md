# Code-Review Skill — As-Is vs. Improved

> **What is a "skill"?**  
> Skills are plain `.md` files stored in `.local/skills/` inside your Replit project.  
> Replit Agent reads them like any other file — there's nothing built-in. When a skill
> is relevant to a task, the agent loads it and follows its instructions.

---

## Side-by-Side Comparison

| Dimension | **Current skill** (`.local/skills/code-review/SKILL.md`) | **Improved prompt** (below) |
|---|---|---|
| **Core metaphor** | "Spawn an architect subagent" | "Spawn a senior code reviewer + architect" — distinguishes *review* (catching real bugs) from *planning* |
| **When to use** | 4 bullet points, all analysis/planning | Adds explicit trigger: "after every non-trivial feature merge" so the agent doesn't have to judge |
| **When NOT to use** | 3 bullets, implementation/simple tasks | Adds: "don't use for style nitpicks the linter already catches" — stops noise |
| **Responsibilities** | 3 modes: `evaluate_task`, `plan`, `debug` | Same 3 modes + clear one-line outcome for each so you know what you'll get back |
| **Output contract** | `text` field described as "Full analysis output" | Specifies *what goes in that text*: severity-ranked findings, actionable file+line refs, concrete fix suggestions |
| **Best practices** | 5 bullets, mostly mechanical (which params to set) | Rewritten as *decision rules* — "if you're debugging, do X; if planning, do Y" |
| **Anti-patterns** | Not mentioned | Section added: things that make architect output useless (vague task, missing files, wrong responsibility) |
| **Examples** | 2 code blocks — rate limiting plan, session debug | Same examples extended with expected output shape so you can spot a bad response |

---

## Current Skill (verbatim)

```markdown
---
name: code-review
description: Spawn a code review (architect) subagent for deep analysis, planning,
  and debugging. Architect should be called after building major features.
  Relies on `delegation` skill.
---

# Architect Skill

Spawn a code review (a.k.a architect) subagent for analysis and planning.
The architect specializes in analysis and strategic guidance rather than implementation.

## When to Use
- You need deep architectural analysis or code understanding
- You want strategic recommendations about system design or patterns
- You need comprehensive analysis of code quality or technical debt
- You want root cause analysis and debugging assistance

## When NOT to Use
- Simple tasks that you can complete directly
- Tasks that require file edits or implementation (use delegation skill instead)
- Read-only operations (use ripgrep or glob/read tools instead)

## Available Function

### subagent({ name, task, config: { $kind: "architect", ... } })

Parameters:
- name (str, required): Short handle, alphanumeric and - only
- task (str, required): The analytical task or question
- config.$kind (required): "architect"
- config.relevantFiles (list[str], optional): Workspace-relative paths to analyze
- config.responsibility (str, default "evaluate_task"): one of exactly
    "evaluate_task" | "plan" | "debug"
- config.includeGitDiff (bool, default false)
- config.relevantGitCommits (str, optional): Commit range e.g. "HEAD~3..HEAD"

Returns: job → { name, jobId, status, text }

## Best Practices
1. Be specific in your task description
2. Provide relevant files
3. Choose the right responsibility
4. Use includeGitDiff when reviewing recent work
5. Use relevantGitCommits for recent history
```

---

## Improved Prompt

```markdown
---
name: code-review
description: |
  Spawn a senior code-reviewer + architect subagent. Use after every non-trivial
  feature build. Returns severity-ranked findings with file+line references and
  concrete fix suggestions. Relies on the `delegation` skill.
---

# Code Review & Architect Skill

Spawn a subagent that acts as a **senior reviewer and architect** — it reads code,
finds real bugs, judges design tradeoffs, and produces a prioritised finding list
you can act on immediately. It never edits files.

---

## When to Use

| Situation | Right responsibility |
|---|---|
| Just finished building a feature | `evaluate_task` |
| About to start a non-trivial feature | `plan` |
| Something is broken and you don't know why | `debug` |
| Suspecting tech debt in a module you're about to touch | `evaluate_task` |

**Trigger rule**: call this automatically after every non-trivial feature merge,
not just when you think something is wrong. Bugs are cheapest to catch here.

## When NOT to Use

- Tasks you can finish in one tool call yourself
- Implementation work — use the `delegation` skill for anything that writes files
- Style/formatting issues the linter or formatter already catches
- Trivial one-liners (renaming a variable, fixing a typo)

---

## What You Get Back (`result.text`)

The architect returns a structured report:

```
SEVERITY  FILE:LINE  FINDING
────────────────────────────────────────────────────────
CRITICAL  src/enforcement/NuclearMode.kt:88
          monitorJob is not @Volatile — race condition under concurrent access.
          Fix: add @Volatile to the declaration.

HIGH      src/data/Database.kt:210
          deleteWithUndo removes the row before the undo window expires.
          Fix: mark row as "pending delete", purge after TTL.

LOW       src/ui/screens/ReportsScreen.kt:44
          LazyColumn items missing stable keys — causes full recompose on update.
          Fix: add key = { it.id } to each item block.
```

If the responsibility is `plan`, the report is a sequenced task breakdown.
If `debug`, it's a root-cause chain with reproduction steps.

---

## API

### subagent({ name, task, config })

```javascript
const result = await subagent({
    name: "feature-review",          // alphanumeric + hyphens only; reuse for follow-ups
    task: "...",                      // see Decision Rules below
    config: {
        $kind: "architect",
        relevantFiles: [...],         // always pass the files — architect can't browse freely
        responsibility: "evaluate_task" | "plan" | "debug",
        includeGitDiff: true,         // set true whenever reviewing recent changes
        relevantGitCommits: "HEAD~3..HEAD"  // optional; embeds that range's diff
    }
});
console.log(result.text);
```

---

## Decision Rules

### Writing a good `task` string
- **evaluate_task**: "Review the BlockScheduleService changes for correctness,
  race conditions, and edge cases. Flag anything that could cause data loss."
- **plan**: "Create a step-by-step implementation plan for adding per-app daily
  time limits to AppBlocker. Identify the riskiest integration points."
- **debug**: "FocusSessionService.start() is called with taskId = null on all
  three call sites. Find the root cause and list every affected code path."

### Choosing `relevantFiles`
- Pass the files you just changed + their direct dependencies.
- For `debug`, add the test file (if any) and the service that owns the broken behaviour.
- Never pass more than ~15 files — the architect's analysis degrades with noise.

### When to set `includeGitDiff: true`
- Always set it for `evaluate_task` and `debug` on recent work.
- Skip it for `plan` (no diff to review yet).

---

## Anti-Patterns (make architect output useless)

| Anti-pattern | Why it fails | Fix |
|---|---|---|
| Vague task: "review the code" | No focus → shallow survey | Name the exact concern: race condition, data loss, API contract |
| Missing `relevantFiles` | Architect can't browse; will analyse nothing useful | Always pass the files you changed |
| Wrong `responsibility` | `plan` on a broken feature gives a roadmap, not a bug fix | Match responsibility to your actual goal |
| Ignoring severity | Acting on LOW before CRITICAL wastes time | Fix CRITICAL/HIGH first, always |

---

## Examples

```javascript
// ── After building a feature ─────────────────────────────────────────
const review = await subagent({
    name: "nuclear-mode-review",
    task: `Review NuclearMode.kt and BreakEnforcer.kt for:
           1. Race conditions on shared state
           2. Missing @Volatile on Job fields
           3. Any path where disable() could deadlock
           Flag each finding with file:line and a one-line fix.`,
    config: {
        $kind: "architect",
        relevantFiles: [
            "src/main/kotlin/com/focusflow/enforcement/NuclearMode.kt",
            "src/main/kotlin/com/focusflow/services/BreakEnforcer.kt"
        ],
        responsibility: "evaluate_task",
        includeGitDiff: true
    }
});
// Expect: severity-ranked list, each with file:line + fix suggestion

// ── Debugging a silent failure ────────────────────────────────────────
const debug = await subagent({
    name: "session-taskid-debug",
    task: `FocusSessionService.start() receives taskId = null on all call sites.
           Trace every call path, find where taskId is dropped, and list
           what data is silently lost as a result.`,
    config: {
        $kind: "architect",
        relevantFiles: [
            "src/main/kotlin/com/focusflow/services/FocusSessionService.kt",
            "src/main/kotlin/com/focusflow/ui/screens/FocusScreen.kt"
        ],
        responsibility: "debug",
        includeGitDiff: true
    }
});
// Expect: root-cause chain, affected call sites, recommended fix per site
```
```

---

## Key Differences at a Glance

```
Current skill                         Improved prompt
─────────────────────────────────────────────────────────────────────
"be specific" (advice)           →    Decision rules + task templates
"choose right responsibility"    →    Table: situation → responsibility
no output contract               →    Exact output shape documented
no anti-patterns section         →    Anti-patterns table with fixes
examples show the call           →    Examples show call + expected output
```
