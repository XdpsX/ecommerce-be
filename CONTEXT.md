## Project Context

`ecommerce-be` is a personal backend project in the E-commerce domain, originally developed during university as a learning project using Java and Spring Boot.

Because of its origin, the current codebase should not be treated as a production-ready system. It may contain:

* Simplistic or unrealistic business logic.
* Incomplete E-commerce workflows and missing business cases.
* Weak or inconsistent domain modeling.
* Architecture and abstractions created mainly for learning purposes.
* Inconsistent or low-quality legacy code.
* Technical debt from previous implementations and partial refactoring attempts.
* Outdated or unnecessary design decisions.
* Insufficient tests and edge-case coverage.
* Missing production concerns such as observability, reliability, scalability, and operational tooling.

A refactoring effort was started previously, so some modules may already have cleaner implementations, tests, migrations, validation, or better abstractions. However, that work was incomplete and should not automatically be considered the desired architecture.

The intention is **not to rewrite the entire project from scratch**.

Instead, the project will be evolved incrementally and used as a long-term learning environment for Backend Engineering, the Spring ecosystem, databases, system design, distributed systems, and production engineering.

---

# M1 — Ecommerce Domain Renewal

## Goal

The goal of this milestone is to evolve the existing student-level E-commerce implementation into a more realistic and coherent E-commerce domain.

The primary focus is **E-commerce business problems**, not new technologies.

Before using the project to explore distributed systems, messaging, caching, search engines, advanced infrastructure, or other technologies, the application should first contain meaningful business workflows and domain problems worth solving.

This milestone therefore focuses on:

* Understanding and auditing the existing business capabilities.
* Refactoring existing implementations where necessary.
* Fixing weak or unrealistic domain models.
* Completing incomplete E-commerce workflows.
* Adding missing core E-commerce capabilities.
* Introducing realistic business rules, invariants, state transitions, and edge cases.
* Improving transaction boundaries and data consistency where they are part of the business problem.
* Improving code quality, maintainability, and testability alongside feature development.
* Adding meaningful tests around business behavior.

Possible areas include, but are not limited to:

Catalog, products, categories, brands, product variants/SKUs, inventory, pricing, promotions, carts, checkout, orders, payments, fulfillment, cancellation, refunds, and related customer workflows.

The exact scope should be determined by inspecting the existing implementation rather than blindly implementing a generic E-commerce feature checklist.

## Work Order

Complete the small repository-baseline tasks directly on the M1 phase branch, without creating separate issues:

1. Fix the build lifecycle so compilation never modifies source files; keep `spotless:apply` manual and use `spotless:check` for verification.
2. Add a minimal CI pipeline using Java 17 that runs formatting checks and tests.
3. Clean the development configuration and profiles only enough to make local and CI setup predictable.
4. Add minimal repository guidance where it is useful, especially a short README for local setup.
5. Review Maven dependencies and build configuration, changing only concrete problems rather than performing a general upgrade.

Improve the test baseline alongside each business change. Add representative tests for the touched behavior instead of trying to backfill exhaustive coverage for the whole repository.

Treat repository-wide changes as optional, separate work:

* Keep the `ecommerce` project naming consistent when adding modules or configuration.
* Move toward package-by-feature while changing a domain. Do not reorganize every package upfront. If a structural change spans many domains, plan it in a separate issue and branch.

## Technical Constraint

This milestone should introduce **little to no new infrastructure or technology unless a concrete business requirement requires it**.

Avoid introducing technologies merely for learning purposes during this phase.

For example, do not introduce Kafka, Redis, Elasticsearch, microservices, CQRS, event-driven architecture, Kubernetes, or similar technologies simply because they are commonly used in E-commerce systems.

Prefer solving problems with the existing application stack first.

Advanced technologies should be introduced in later phases when limitations of the current implementation create a clear reason to study them.

**Technology follows the problem, not the other way around.**

## Refactoring Approach

Refactoring should be incremental.

Do not attempt a full rewrite or a repository-wide architectural migration before implementing business capabilities.

Existing code should be evaluated individually:

* Keep implementations that are already reasonable.
* Refactor implementations when they obstruct new business requirements.
* Replace abstractions when their limitations become concrete.
* Avoid speculative abstractions for hypothetical future requirements.

When working on a feature, examine the complete flow where appropriate:

`API → application/business logic → persistence → database model → tests`

Refactoring and feature development should happen together so architectural improvements are driven by actual use cases.

## Long-term Direction

The resulting application is intended to become a **base project for learning backend engineering through real problems**.

Future phases may use problems discovered in this domain to explore topics such as:

* Database concurrency and locking.
* Caching.
* Search.
* Messaging and asynchronous processing.
* Event-driven architecture.
* Transactional outbox.
* Idempotency.
* Distributed transactions and consistency.
* Service decomposition and microservices.
* Scalability and performance.
* Observability.
* Resilience and fault tolerance.
* Deployment and production operations.

Those technologies and architectural patterns should be introduced because the evolving E-commerce system creates a reason for them, rather than artificially inserting them into the project.

The immediate objective of **M1 — Ecommerce Domain Renewal** is therefore simple:

> Build a sufficiently realistic E-commerce domain first, so future backend and distributed-system problems emerge naturally from the system.
