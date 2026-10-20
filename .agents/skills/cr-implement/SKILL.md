---
name: cr-implement
description: Implement and verify an approved repository change-request plan, then produce a concise implementation review guide. Use when acceptance criteria and intended behavior are clear; do not use for exploratory planning or an independent code review.
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
6. After implementation stabilizes, create one temporary Markdown implementation walkthrough following `../../../docs/change-workflow.md`. Default to `docs/local/<issue-or-cr>-implementation-review.md`; keep it Git-ignored and untracked. Respect a requested path when it can meet that requirement; preserve any already-tracked document and use an ignored local copy instead. Verify with `git check-ignore` and `git ls-files`.
7. Inspect the final diff for scope, accidental compatibility changes, generated files, secrets, and unrelated formatting churn, including the review guide.

When repository evidence requires a material deviation from the plan, explain it before proceeding when it changes user-visible behavior; otherwise record it in the handoff.

## Implementation review guide

Write in Vietnamese unless requested otherwise. Write from the final implementation rather than copying the plan or raw diff. Start with a concrete user scenario, explain technical terms, and describe the request-to-database-to-response journey so a non-programmer can understand and review the behavior without reading code. Include:

- the issue/CR goal and implemented scope;
- behavior before and after the change;
- important decisions, invariants, and any deviations from the approved plan;
- changed components grouped by responsibility;
- a recommended code-reading order that follows the real flow, such as API entry point -> application orchestration -> domain rules -> persistence/migration -> infrastructure adapter -> representative tests;
- for each reading step, the relevant repository path and the symbol or behavior to inspect;
- verification commands and results, skipped checks, remaining risks, and known follow-up work.

Do not include secrets, provider credentials, large code listings, or a file-by-file restatement of the diff. Keep the guide accurate if files moved or planned components were not implemented. Treat the guide as part of the CR deliverable and mention its path in the final handoff.

Update the walkthrough after review/smoke fixes. Keep it local, exclude it from staging and commit commands, and leave removal to the user unless asked to delete it. Durable contracts and runbooks remain separate tracked documentation.

## Output

Lead with the implemented outcome. Link the implementation review guide, summarize changed behavior and important decisions, list exact verification commands and results, and disclose remaining risks, skipped checks, or follow-up work. Do not commit, push, or open a pull request unless explicitly requested.

This stage belongs to **Luna**, including fixes. End with **Next model: Sol** and a ready-to-copy `$cr-review` prompt containing the actual plan/walkthrough paths, base, implemented scope, verification results and remaining findings or limitations. Do not declare independent review passed or switch models/spawn agents automatically.
