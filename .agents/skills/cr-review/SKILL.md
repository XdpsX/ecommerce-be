---
name: cr-review
description: Review a completed change request against its issue, approved plan, and phase branch. Use for findings-first correctness and regression review; do not modify code unless the user separately asks for fixes.
---

# Review a change request

Read `../../../CONTEXT.md`, `../../../AGENTS.md`, `../../../docs/change-workflow.md`, and `../../../docs/conventions.md` before reviewing.

## Inputs and comparison

Identify the issue, acceptance criteria, approved plan, and intended phase base. Review the complete branch diff against that base, not only the latest commit. If the base cannot be established, state the limitation rather than silently choosing one.

Include staged, unstaged and relevant untracked implementation files. Read the local implementation walkthrough when available, then verify its claims against code and tests. Its absence is a missing handoff deliverable, not evidence that code is correct or incorrect.

## Review priorities

1. Incorrect business behavior and unmet acceptance criteria.
2. Data integrity, transaction, concurrency, and Liquibase migration risks.
3. Authentication, authorization, validation, and information-exposure regressions.
4. API, database, and persistence compatibility regressions.
5. Tests that miss the main behavior or provide too little confidence for the risk of the change.
6. Maintainability problems introduced by the diff.

Inspect surrounding code when needed to validate a finding. Run relevant non-mutating checks when practical, and distinguish verified defects from questions or optional improvements. Do not request exhaustive tests when a small representative set is sufficient.

## Output

Report findings first, ordered by severity. For each finding, include a concise title, file and line reference where possible, impact, reasoning, and a concrete correction direction. Then list open questions and a brief verification summary. If there are no findings, say so and note residual risks or untested areas.

Remain read-only. Do not edit files, commit, push, or open a pull request unless the user explicitly asks for a separate fix step.

## Handoff, smoke and commit guidance

This stage belongs to **Sol**. Follow `../../../docs/change-workflow.md`:

- When fixes are required, end with **Next model: Luna** and a ready-to-copy `$cr-implement` prompt naming concrete findings, plan/walkthrough paths, verification expectations and the need to update the local walkthrough. Fixes return to Sol for re-review.
- When review passes, provide a few concrete main-flow smoke cases with prerequisites, steps and expected results. Recommend Postman for API/backend behavior and UI/browser for browser behavior; combine only when needed. Missing UI leaves browser checks pending. Disclose skipped checks and blockers; manual smoke does not automatically replace required automated verification.
- End the successful review with **Next model: Sol after the user's smoke report**, and a ready-to-copy reply template for results and pending/failed cases. A failed smoke returns to Luna for fixes and then Sol for re-review.
- When the user later confirms smoke passed, inspect current Git status/diff and review any subsequent changes. If no required corrections remain, provide scoped PowerShell `git add -- <explicit paths>` and `git commit -m '<message>'` commands for the user to execute. Exclude the ignored walkthrough, secrets and unrelated changes; account for unrelated files already staged before recommending a commit. Do not run the commands, unstage user work, push or open a PR without a separate request.
- End that handoff with the next actor/action and a ready-to-copy prompt for any follow-up. Do not claim to switch models or spawn agents automatically.
