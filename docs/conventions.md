# Engineering Conventions

Improve the code incrementally. Existing code is not automatically the desired design, but a CR must not become an unrelated cleanup or rewrite.

- Keep controllers focused on HTTP concerns and services on business behavior.
- Keep transaction boundaries around complete write use cases.
- Do not expose JPA entities as API responses.
- Preserve existing API behavior unless the issue changes it.
- Add a new Liquibase changeset for database changes; do not edit an applied changeset.
- Avoid new dependencies and abstractions until a concrete need exists.

## Tests

Do not test every possible case. Prefer a small set that provides useful confidence:

1. **Main behavior** — the representative happy path or core business behavior.
2. **Representative failure** — one important rejection or failure, when the behavior has one worth protecting.
3. **Relevant learning case** — a meaningful boundary that also exercises a technique useful to this project.

Do not force all three categories when they add no value. Tests should protect behavior, not mirror every implementation detail.

## Commands

Windows PowerShell:

```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd spotless:check
.\mvnw.cmd test
```

Run `spotless:apply` explicitly when formatting is needed. Maven build phases must not modify source files; use `spotless:check` for verification.

Focused test example:

```powershell
.\mvnw.cmd -Dtest=BrandServiceImplTest test
```

On Unix-like environments, use `./mvnw`. Run focused tests first, then the applicable broader suite. Inspect the diff after formatting and builds.
