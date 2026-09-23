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

## 1. Plan

- Read the issue and inspect the relevant code.
- Describe current behavior, proposed changes, affected files, the smallest meaningful test set, risks, and open questions.
- Do not change production code during planning.
- Resolve questions that materially change behavior before implementation.

## 2. Implement

- Implement the approved scope only.
- Add only the relevant representative tests described in `docs/conventions.md`.
- Use Liquibase when the database changes.
- Run formatting and relevant tests.
- Report material deviations from the plan.

## 3. Review

- Review the complete diff against the phase branch.
- Check acceptance criteria, correctness, data integrity, security, compatibility, and whether tests give proportional confidence.
- Report concrete findings first, ordered by severity.
- Do not modify code during review unless asked.

## 4. Pull request

- Link the issue and target the phase branch.
- Summarize the change and verification performed.
- Merge after acceptance criteria pass and blocking review findings are resolved.

Keep plans and decisions in the issue or PR. Add permanent documentation only when it will be useful for future changes.
