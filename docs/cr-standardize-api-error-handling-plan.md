# CR Plan — Standardize API Error Handling

## Status

Proposed.

Target base branch: `phase/m1-ecommerce-renewal`.

No issue number or final acceptance criteria have been provided yet. This plan uses the agreed goal of establishing a small, consistent error-handling foundation before further M1 domain work.

## Recommended approach

Adopt Spring's RFC 9457 `ProblemDetail` support for error responses, replace the HTTP-oriented exception hierarchy with one `ApplicationException` carrying a typed `ErrorCode`, and centralize the mapping from application errors to HTTP responses at the API boundary.

Keep the design intentionally small:

- One application exception type rather than one exception class per domain case.
- One initial `ErrorCode` enum containing only errors required by current behavior.
- One MVC exception handler based on `ResponseEntityExceptionHandler`.
- One small problem factory shared with security components that execute outside Spring MVC.
- No new dependency, infrastructure, persistence change, or speculative error framework.

This CR should intentionally change the existing error response contract. It should not change successful response envelopes in the same CR.

## Current behavior

The current error flow has several incompatible conventions:

- `APIException#getMessage()` can contain either an `E_MESSAGE_*` value or literal English text.
- `ErrorDTO.message` therefore has no single stable meaning.
- Exception types such as `NotFoundException`, `DuplicateException`, and `BadRequestException` primarily describe an HTTP outcome rather than an application error.
- The same logical error can map to different statuses. For example, an invalid media resource type is raised as both `BadRequestException` and `InvalidResourceTypeException`, producing either 400 or 422.
- `CustomExceptionHandler` and `GlobalExceptionHandler` are both global advice beans, have no explicit ordering, and one contains a catch-all `Exception` handler.
- The catch-all handler can turn Spring MVC client errors not handled explicitly, such as malformed request bodies, into generic 500 responses.
- Expected 4xx results and validation failures are logged at error level with stack traces.
- `AccessDeniedException` and `CustomAuthEntryPoint` expose framework exception messages directly to clients.
- Validation errors are collapsed into `Map<String, String>`, losing individual validation codes and repeated violations.
- There are no focused tests protecting the complete error contract.

The success envelope has separate concerns:

- `APIResponse.noContent()` returns a 200 response with a body despite its name.
- `SMessage` values form part of the current successful response contract and are asserted in controller tests.

Those success concerns are excluded from this CR so error standardization remains independently reviewable.

## Target contract

### Application error

Example:

```json
{
  "type": "about:blank",
  "title": "Resource not found",
  "status": 404,
  "detail": "The requested brand does not exist",
  "instance": "/admin/brands/123",
  "code": "RESOURCE_NOT_FOUND",
  "parameters": {
    "resourceType": "brand",
    "resourceId": 123
  }
}
```

Contract rules:

- `code` is the stable, machine-readable application error identifier.
- `title` and `detail` are controlled server values, never raw framework exception messages.
- `status` is the actual HTTP response status represented by `ProblemDetail`.
- `instance` identifies the request path using Spring's standard behavior.
- `parameters` contains only explicitly selected, client-safe context.
- Parameter values should be simple JSON-safe scalar values such as strings, numbers, booleans, or identifiers unless a concrete use case requires otherwise.
- Entities, DTOs, exceptions, request objects, and arbitrary domain objects must not be placed in `parameters`.
- Stack traces, causes, implementation class names, and arbitrary rejected values are never returned.

### Validation error

Example:

```json
{
  "type": "about:blank",
  "title": "Request validation failed",
  "status": 400,
  "instance": "/auth/register",
  "code": "VALIDATION_FAILED",
  "errors": [
    {
      "field": "email",
      "code": "NotBlank",
      "message": "must not be blank"
    },
    {
      "field": "email",
      "code": "Email",
      "message": "must be a well-formed email address"
    }
  ]
}
```

Validation rules:

- Preserve each individual violation instead of concatenating messages.
- Include field name, validation code, and resolved message.
- Support object-level errors without inventing a fake field name.
- Do not expose rejected values by default because they can contain passwords, tokens, or other sensitive input.

## Proposed components

### `ErrorCode`

Create `common/error/ErrorCode.java` as a plain enum with no dependency on Spring or `HttpStatus`.

The initial codes should cover current behavior only. Expected examples include:

```text
VALIDATION_FAILED
MALFORMED_REQUEST
INVALID_CREDENTIALS
AUTHENTICATION_REQUIRED
ACCESS_DENIED
RESOURCE_NOT_FOUND
RESOURCE_ALREADY_EXISTS
RESOURCE_IN_USE
CONCURRENT_MODIFICATION
INVALID_CATEGORY_DEPTH
INVALID_MEDIA_RESOURCE_TYPE
INVALID_IMAGE_WIDTH
MEDIA_UPLOAD_FAILED
INTERNAL_ERROR
```

The final list should be based on the migrated call sites. Do not add hypothetical checkout, payment, inventory, promotion, or fulfillment codes in this CR.

Prefer a shared code when different resources have the same client-visible meaning and recovery behavior. For example, missing brands, categories, products, cart items, and orders should use `RESOURCE_NOT_FOUND` with safe `resourceType` and `resourceId` parameters. Introduce a domain-specific code only when the client needs to distinguish the condition or respond differently, such as `INVALID_CATEGORY_DEPTH` or `INVALID_IMAGE_WIDTH`.

### `ApplicationException`

Create `common/error/ApplicationException.java` with:

- Required `ErrorCode code`.
- Immutable `Map<String, Object> parameters`, defaulting to an empty map.
- An optional cause constructor only where preserving a technical cause is useful.

The exception must not contain `HttpStatus`. Application code should express what failed, while the API boundary decides how that failure is transported.

Representative usage:

```java
throw new ApplicationException(
        ErrorCode.RESOURCE_NOT_FOUND,
        Map.of("resourceType", "brand", "resourceId", id));
```

For M1, the scalar-only parameter rule is a documented convention rather than a new type system or serialization abstraction. Each migrated throw site must be reviewed to ensure its keys and values are safe for client exposure.

### `FieldViolation`

Create a small immutable response value containing:

```text
field
code
message
```

The field may be absent for object-level validation errors.

### `ApiProblemFactory`

Create a focused factory responsible for constructing controlled `ProblemDetail` instances and adding the common extension properties.

The concrete need for this abstraction is that MVC advice and Spring Security's `AuthenticationEntryPoint` run through different mechanisms but must produce the same response contract.

It must not become a registry, localization framework, or general response builder.

### `GlobalExceptionHandler`

Replace the two current advice classes with one `GlobalExceptionHandler` extending `ResponseEntityExceptionHandler`.

Responsibilities:

- Convert `ApplicationException` through an explicit `ErrorCode` to HTTP status mapping.
- Convert validation errors into structured `FieldViolation` entries.
- Return controlled 401 and 403 errors for security exceptions that reach MVC.
- Preserve appropriate client-error statuses for Spring MVC exceptions such as malformed JSON, unsupported media types, missing parameters, type mismatches, and unsupported HTTP methods.
- Return a generic `INTERNAL_ERROR` response only for unexpected or unclassified exceptions.
- Log only unexpected failures with an error-level stack trace.

At the current scale, a centralized switch for `ErrorCode` to `HttpStatus` is preferred over introducing a registry or strategy hierarchy.

### Security boundary

Update `CustomAuthEntryPoint` to use `ApiProblemFactory` and return `AUTHENTICATION_REQUIRED` rather than `AuthenticationException#getMessage()`.

If the active security flow can produce filter-chain authorization failures, add or update an `AccessDeniedHandler` to return `ACCESS_DENIED` through the same factory. Do not add it speculatively without confirming the current security path.

## Existing-error migration

Migrate every current `APIException` call site before deleting the old hierarchy. Prefer domain-specific codes over generic HTTP categories.

Representative mappings:

| Current value or exception | Target direction |
| --- | --- |
| `EMessage.NOT_FOUND` | `RESOURCE_NOT_FOUND` plus safe `resourceType` and identifier parameters |
| `EMessage.DATA_EXISTS` | `RESOURCE_ALREADY_EXISTS` plus safe resource and conflicting-field context |
| `EMessage.IN_USE` | `RESOURCE_IN_USE` plus safe resource context |
| `EMessage.MODIFY_EXCLUSIVE` | `CONCURRENT_MODIFICATION` plus safe resource context |
| `EMessage.INVALID_DEPTH` | `INVALID_CATEGORY_DEPTH` |
| `EMessage.INVALID_RESOURCE_TYPE` | `INVALID_MEDIA_RESOURCE_TYPE` where applicable |
| Literal product/cart/order not-found strings | `RESOURCE_NOT_FOUND` plus named resource parameters |
| `EMessage.SERVER_ERROR` | `INTERNAL_ERROR`, created only for unexpected or unclassified failures at the response boundary |
| `EMessage.UPLOAD_IMAGE_FAILED` | `MEDIA_UPLOAD_FAILED`; preserve the cause internally and never expose its message |

`MEDIA_UPLOAD_FAILED` is a known public failure code and should map to the selected server or upstream-dependency status, initially 502 unless implementation evidence supports a more precise status. A client can use the code to display a specific message or decide whether retry is appropriate without seeing provider details.

Before applying this code, narrow the current `MediaServiceImpl#createMedia` catch boundary. Its broad `catch (Exception)` currently covers provider upload, database persistence, mapping, and cleanup. Only a confirmed media-provider/upload failure should become `MEDIA_UPLOAD_FAILED`; an unrelated persistence or programming failure must remain `INTERNAL_ERROR`. Cleanup should preserve the original cause instead of replacing it.

After all usages are migrated, remove:

```text
APIMessage
APIException
BadRequestException
DuplicateException
InUseException
InvalidResourceTypeException
ModifyExclusiveException
NotFoundException
EMessage
ErrorDTO
ErrorDetailsDTO
CustomExceptionHandler
```

Keep `SMessage` and `APIResponse` because they remain part of the successful response contract and are outside this CR.

## Logging policy

Apply the following policy in the centralized boundary:

| Outcome | Logging |
| --- | --- |
| Expected application 4xx | No log or debug without stack trace |
| Request validation failure | No log |
| Authentication failure | Debug without stack trace |
| Access denied | Debug or warn without stack trace, depending on operational need |
| Unexpected 5xx | Error with stack trace |

Do not log the same exception in both the service and global handler.

## Implementation plan

1. Add `ErrorCode`, `ApplicationException`, `FieldViolation`, and `ApiProblemFactory` without changing existing call sites yet.
2. Replace the two advice classes with one `GlobalExceptionHandler` based on `ResponseEntityExceptionHandler`.
3. Add controlled handling for application, validation, common framework, security, and unexpected errors.
4. Update `CustomAuthEntryPoint` and confirm whether a filter-chain `AccessDeniedHandler` is needed.
5. Migrate existing exception usages feature by feature, selecting a code at the appropriate client-facing abstraction level and safe scalar parameters for each case.
6. Remove the obsolete DTOs, message enum, and exception hierarchy after repository-wide usage checks are clean.
7. Update affected controller tests to assert the new error contract while leaving successful response assertions unchanged.
8. Run formatting, focused tests, the full test suite, and inspect the final diff for accidental success-contract changes.

Likely affected production areas:

```text
src/main/java/com/xdpsx/ecommerce/common/error
src/main/java/com/xdpsx/ecommerce/auth/infrastructure/security
src/main/java/com/xdpsx/ecommerce/catalog
src/main/java/com/xdpsx/ecommerce/cart
src/main/java/com/xdpsx/ecommerce/order
src/main/java/com/xdpsx/ecommerce/payment
src/main/java/com/xdpsx/ecommerce/media
```

No database, entity, repository, transaction, or Liquibase change is expected.

## Test plan

Add a small representative set rather than one test per error code:

1. A missing resource returns 404 with `RESOURCE_NOT_FOUND` and the expected safe resource parameters.
2. Request validation returns 400 with structured violations and preserves multiple violations when relevant.
3. Malformed JSON returns a controlled 400 response rather than falling through to 500.
4. An unexpected exception returns 500 with `INTERNAL_ERROR` and does not expose the original exception message.
5. `CustomAuthEntryPoint` returns 401 with `AUTHENTICATION_REQUIRED` and does not expose the framework exception message.
6. A confirmed media-provider upload failure returns `MEDIA_UPLOAD_FAILED` without exposing the provider exception message; an unrelated unexpected failure remains `INTERNAL_ERROR`.

Update existing error assertions in affected controller tests. Do not rewrite unrelated success-path tests.

Suggested verification commands:

```powershell
.\mvnw.cmd spotless:apply
.\mvnw.cmd spotless:check
.\mvnw.cmd -Dtest=<focused error and controller tests> test
.\mvnw.cmd test
git diff --check
git diff --stat
```

## Compatibility and migration impact

This is a breaking error-response change:

- `message` is replaced by a stable `code` plus controlled `title` and `detail`.
- Positional `args` are replaced by named `parameters`.
- Validation `fieldErrors` becomes a list of structured violations.
- The response media type may become `application/problem+json` through Spring's `ProblemDetail` handling.
- Some requests currently returned as generic 500 errors may correctly become framework-defined 4xx errors.

The project should change the contract directly only if no deployed frontend or external consumer requires the existing shape. If compatibility is required, the issue must define a versioning or transition strategy before implementation.

## Risks

- Missing an existing exception call site could leave two error contracts active at once.
- Exposing arbitrary exception parameters could leak internal or sensitive data; parameters must be explicitly chosen and restricted to simple JSON-safe scalar values at each throw site.
- A shared error code without sufficient safe context, or one that combines conditions requiring different client behavior, would reproduce the current ambiguity under a new name.
- Overly specific codes could create unnecessary taxonomy; add codes only for current client-relevant distinctions.
- A broad catch boundary could misclassify database or programming failures as `MEDIA_UPLOAD_FAILED`.
- Incorrect advice ordering or a broad catch-all could still override Spring's intended framework error handling.
- Security filter errors and MVC errors can drift unless both use the same problem factory.

## Out of scope

- Changing `APIResponse`, `SMessage`, or successful response envelopes.
- Changing `APIResponse.noContent()` or standardizing delete responses to 204.
- Designing all future ecommerce error codes.
- Localization or `MessageSource` integration.
- Trace IDs, observability infrastructure, or external error reporting.
- Database or persistence changes.
- Reorganizing unrelated packages.

## Open question

Before implementation, confirm whether any deployed frontend or external consumer depends on the existing `{status, message, args}` and validation `fieldErrors` shapes.

If there is no compatibility requirement, migrate directly to the new contract in this CR. If there is one, define API versioning or a bounded compatibility period before approving implementation.
