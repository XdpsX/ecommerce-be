---
name: cr-review
description: Review a completed change request against its issue, approved plan, and phase branch. Use for findings-first correctness and regression review; do not modify code unless the user separately asks for fixes.
---

# Review a change request

Read `../../../CONTEXT.md`, `../../../AGENTS.md`, `../../../docs/change-workflow.md`, and `../../../docs/conventions.md` before reviewing.

## Inputs and comparison

Identify the issue, acceptance criteria, approved plan, and intended phase base. Review the complete branch diff against that base, not only the latest commit. If the base cannot be established, state the limitation rather than silently choosing one.

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
