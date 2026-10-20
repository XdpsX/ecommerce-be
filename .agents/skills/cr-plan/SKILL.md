---
name: cr-plan
description: Investigate and plan one repository change request before implementation. Use for issue analysis, scope clarification, affected-flow discovery, and test or migration planning; do not use to modify production code.
---

# Plan a change request

Read `../../../CONTEXT.md`, `../../../AGENTS.md`, `../../../docs/change-workflow.md`, and `../../../docs/conventions.md` before planning.

## Inputs

Use the issue or user request as the source for the desired outcome, acceptance criteria, phase branch, constraints, and out-of-scope work. State what is missing instead of inventing material business behavior.

## Workflow

1. Inspect relevant code and history as needed to establish current behavior with evidence.
2. Trace the affected flow across API, business logic, persistence, database, and tests where applicable.
3. Identify the business rules, boundaries, and risks that materially affect this CR.
4. Separate required work from optional follow-up work.
5. Produce an ordered, implementation-ready plan with likely files or components, verification strategy, risks, assumptions, and unresolved questions.

Remain read-only. Do not create or edit files, branches, commits, issues, or pull requests unless the user explicitly expands the task.

## Output

Lead with the recommended scope and approach. Include current behavior, implementation steps, the smallest meaningful test set, migration/compatibility impact, risks, and blocking questions. Do not propose exhaustive acceptance criteria or tests.

This stage belongs to **Sol**. Follow the handoff format in `../../../docs/change-workflow.md`: end with the next model and a ready-to-copy prompt. Once the scope is approved, hand off to **Luna / `$cr-implement`**, including the plan path, comparison base and constraints. If approval or a blocking decision is pending, keep the next action with Sol to resolve it before implementation. Do not switch models or spawn agents automatically.
