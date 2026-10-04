# Repository Instructions

- Read `CONTEXT.md` before planning or changing behavior.
- Follow `docs/change-workflow.md` and `docs/conventions.md`.
- Keep changes within the issue scope and acceptance criteria.
- Improve the touched flow incrementally; do not redesign unrelated parts of the project.
- Do not add infrastructure, dependencies, or abstractions without a concrete need.
- Preserve unrelated user changes.
- Check the relevant flow from API to database and tests when needed.
- Keep tests proportional to the change: cover the main behavior, one meaningful failure when useful, and a relevant learning boundary only when it adds value.
- Report commands run, skipped checks, assumptions, and remaining risks.
- Do not create branches, commit, push, or open a PR unless asked.

Use `$cr-plan`, `$cr-implement`, and `$cr-review` for the corresponding CR stage.
