---
name: cr-implement
description: Implement an approved repository change-request plan and verify it. Use when acceptance criteria and intended behavior are clear; do not use for exploratory planning or an independent code review.
---

# Implement a change request

Read `../../../CONTEXT.md`, `../../../AGENTS.md`, `../../../docs/change-workflow.md`, and `../../../docs/conventions.md` before editing.

## Preconditions

Identify the issue, approved plan, acceptance criteria, and intended phase branch. If a missing decision would materially change behavior, stop implementation at that decision and ask one focused question. Otherwise make conservative, documented assumptions.

## Workflow

1. Inspect Git status and preserve unrelated user changes.
2. Verify that the working branch is appropriate for the CR; do not create or switch branches without authorization.
3. Implement the smallest coherent vertical change that satisfies the acceptance criteria.
4. Add a proportional test set: the main behavior, one meaningful failure when useful, and a relevant learning boundary only when it adds value.
5. Run focused tests and formatting, then the applicable broader checks.
6. Inspect the final diff for scope, accidental compatibility changes, generated files, secrets, and unrelated formatting churn.

When repository evidence requires a material deviation from the plan, explain it before proceeding when it changes user-visible behavior; otherwise record it in the handoff.

## Output

Lead with the implemented outcome. Summarize changed behavior and important decisions, list exact verification commands and results, and disclose remaining risks, skipped checks, or follow-up work. Do not commit, push, or open a pull request unless explicitly requested.
