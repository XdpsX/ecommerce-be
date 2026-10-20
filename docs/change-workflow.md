# Change Request Workflow

Each issue is one change request (CR).

## Branches

```text
main
`-- phase/<milestone>
    |-- feat/<issue-number>-<short-name>
    |-- fix/<issue-number>-<short-name>
    `-- ...
```

- Create `<type>/<issue-number>-<short-name>` from the current phase branch.
- Use a simple type that describes the change: `feat`, `fix`, `refactor`, or `chore`.
- Open the PR back into that phase branch.
- Merge the phase branch into `main` when the milestone is complete.
- Keep one issue per CR where practical.
- Small, low-risk phase setup tasks may be completed directly on the phase branch without an issue.
- Use a separate issue and branch for structural, risky, or independently reviewable changes.

## 1. Plan

- Model: **Sol**, using `$cr-plan`.
- Read the issue and inspect the relevant code.
- Describe current behavior, proposed changes, affected files, the smallest meaningful test set, risks, and open questions.
- Do not change production code during planning.
- Resolve questions that materially change behavior before implementation.
- End with the plan location, approval status, and a ready-to-copy prompt for **Luna / `$cr-implement`**. Include the approved scope, plan path, base branch and constraints. If approval or a material decision is still pending, direct the user to resolve it with Sol before starting implementation.

## 2. Implement

- Model: **Luna**, using `$cr-implement`; Luna also handles fixes requested after review.
- Implement the approved scope only.
- Add only the relevant representative tests described in `docs/conventions.md`.
- Use Liquibase when the database changes.
- Run formatting and relevant tests.
- Report material deviations from the plan.
- Create or update a temporary Markdown implementation walkthrough using the rules below, including after fixes.
- End with a ready-to-copy prompt for **Sol / `$cr-review`**, including plan and walkthrough paths, comparison base, changed scope, checks and known limitations. Implementation completion is a handoff to review, not review approval.

## 3. Review

- Model: **Sol**, using `$cr-review`.
- Review the complete diff against the phase branch.
- Include staged, unstaged and relevant untracked changes when implementation is not committed. Read the temporary walkthrough, but verify its claims against code and tests.
- Check acceptance criteria, correctness, data integrity, security, compatibility, and whether tests give proportional confidence.
- Report concrete findings first, ordered by severity.
- Do not modify code during review unless asked.
- If corrections are required, end with a ready-to-copy prompt for **Luna / `$cr-implement`** naming the findings and asking for focused fixes, verification and an updated walkthrough. Luna then hands back to Sol for re-review.
- When review passes, give the user a small smoke checklist focused on the main flow. State any skipped automated checks and whether they prevent approval; smoke does not silently waive required checks.

## 4. User smoke test

- Model: **Sol** prepares the checklist and interprets the user's results.
- Choose the tool from the behavior: **Postman** for API requests, permissions, response bodies and persisted results; **UI/browser** for rendering, forms, navigation, browser CORS, direct provider uploads and other browser-specific behavior. Combine them only when both are needed. If no UI consumer exists, use Postman for the backend and explicitly leave browser-specific verification pending.
- Prefer a few representative cases: the main successful journey and, when useful, one important rejection. Do not aim for exhaustive manual coverage.
- For each case, give the tool, prerequisites/test data, concrete steps or requests and observable expected results. Avoid destructive shared data; use disposable records where practical.
- End with **Next: Sol** and a ready-to-copy reply template for the user to report smoke results, failed steps and pending cases. Do not claim smoke passed before the user reports it.
- A failed smoke goes back to Luna with the reproduction details, then Sol re-reviews the fix and selects the relevant cases to repeat.

## 5. Commit commands and pull request

- After the user confirms smoke passed and review has no unresolved required corrections, **Sol** checks current Git status/diff and provides exact PowerShell `git add -- <explicit paths>` and `git commit -m '<message>'` commands for the user to run. If changes occurred after review, review those changes first.
- Exclude temporary walkthroughs, secrets and unrelated user changes. Inspect existing staged files; if unrelated changes are staged, explain that before giving a commit command that would include them. Do not unstage the user's work automatically. Avoid `git add .` and `git add -A`.
- Providing commands does not authorize executing them. Do not commit, push or open a PR unless separately asked.
- End with **Next: user runs the commands; Sol if further help is needed**, plus a ready-to-copy prompt for the next requested step. Do not imply a commit or PR already exists.

- Link the issue when one exists and target the phase branch.
- Summarize the change and verification performed.
- Merge after acceptance criteria pass and blocking review findings are resolved.

## Temporary implementation walkthrough

Luna must write this `.md` file after implementation, even for a small CR. Default path: `docs/local/<cr-id>-implementation-review.md`; use the user's requested path when provided, and ensure the temporary file is Git-ignored and untracked. Do not force-add it. If the requested path is already tracked, preserve it and create an ignored local copy instead, explaining the location. Keep durable API contracts and runbooks separate.

Write in Vietnamese unless the user requests another language. Start with a concrete user scenario and explain before/after behavior in plain language. Follow the actual journey from the request through business rules to saved data and the response; explain technical terms when first used. Describe what the user sees on success and important failures. Then add a short code-reading map with paths/symbols for Sol, decisions/deviations, checks actually run, skipped checks and remaining risks. A reader unfamiliar with code should understand what changed and what to verify without opening Java files.

Keep it accurate after fixes. Link it in the handoff, exclude it from staging/commit commands, and leave deletion to the user after review/smoke unless deletion is requested. Plans can also live in `docs/local/` or the existing ignored `docs/m1/`; record durable decisions in the issue/PR or topic docs as appropriate. See `docs/README.md` for durable documentation.

## Model handoff format

Every stage ends with the next model (Sol or Luna), the next action, and a ready-to-copy prompt containing the real CR identifier and relevant paths, findings or results. Replace placeholders before sending; do not make the user reconstruct context. These are manual model handoffs: do not claim to switch the active model or spawn agents automatically.
