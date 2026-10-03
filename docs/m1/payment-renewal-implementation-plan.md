# Payment Renewal Implementation Plan

## Recommended Scope

Renew the existing VNPay integration in two implementation slices:

1. Introduce a persistent payment-attempt model and an authenticated retry flow.
2. Process provider callbacks against an exact attempt and keep Payment, Order, and Inventory transitions consistent.

Keep VNPay as the only payment provider and VND as the only currency in this round. Preserve the Checkout response
shape where practical, but stop using the Order ID as the provider transaction reference for every retry.

This renewal does not include cancellation, refunds, additional payment providers, message brokers, or automatic
financial reconciliation.

## Current Behavior

The Checkout/Order renewal already provides these foundations:

- Checkout creates one `PENDING_PAYMENT` Order and one `UNPAID` Payment.
- Inventory is reserved during Checkout.
- Payment initialization runs after the Checkout transaction commits.
- A verified successful VNPay callback consumes reserved Inventory, marks the Payment paid, and confirms the Order in
  one transaction.
- Repeated callbacks do not consume Inventory twice.
- Checkout expiry releases Inventory and moves the Order to `PAYMENT_EXPIRED`.

The remaining Payment weaknesses are:

- `Payment` is only a one-to-one summary row with `UNPAID/PAID`; it cannot represent individual attempts.
- Every initialization uses `vnp_TxnRef = orderId`, so retries cannot be distinguished or audited.
- No VNPay provider transaction ID is persisted.
- A signed failed callback leaves no attempt history.
- Checkout expiry leaves the Payment itself `UNPAID` rather than terminally expired.
- The database does not enforce the JPA one-to-one relationship on `payments.order_id`.
- The callback verifies signature, merchant code, Order and amount, but it does not resolve an exact persisted attempt.
- The controller logs the complete IPN parameter map and the VNPay adapter logs the generated URL, both of which may
  contain signatures or other sensitive query data.
- VNPay time formatting uses `Etc/GMT+7`, whose sign convention is the opposite of the intended GMT+7 zone.
- Shared `SimpleDateFormat` and `Mac` instances are unsafe under concurrent requests.
- There is no direct test proving initialization amount, reference, expiry, or signing input.

VNPay documents `vnp_TxnRef` as the merchant transaction reference that must not be duplicated within a day. Its IPN
returns the same reference together with `vnp_TransactionNo`, amount, response code, and transaction status. The
attempt reference must therefore identify a PaymentAttempt rather than the parent Order.

Reference: <https://sandbox.vnpayment.vn/apis/docs/thanh-toan-pay/pay.html>

## Business Rules

### Payment and attempt ownership

- An Order has one Payment summary.
- A Payment has one or more PaymentAttempts over its lifetime.
- Payment amount and currency come only from the immutable Order snapshot.
- VNPay remains the only method and VND remains the only supported currency.
- A customer may initiate or retry payment only for their own Order.

### Retry policy

- Retry is allowed only while the Order is `PENDING_PAYMENT` and its reservation has not expired.
- At most one non-expired `PENDING` attempt may be active for an Order.
- A retry while an attempt is still active reuses that attempt instead of creating another provider reference.
- A new attempt may be created after the previous attempt is explicitly failed or expired.
- An attempt expiry must never be later than the Order reservation expiry.
- Payment initialization failure must not delete or recreate the Order, Payment, or reservation.

The Order lock is the application-level serialization boundary for attempt creation. Database uniqueness on provider
references remains the final identity guarantee.

### Attempt lifecycle

Use these attempt states:

```text
PENDING -> SUCCEEDED
PENDING -> FAILED
PENDING -> EXPIRED
```

`SUCCEEDED` is never downgraded by a later or out-of-order callback. A verified success may complete an older attempt
while the Order is still pending; any other pending attempts then become expired/superseded for future initiation.

Use these Payment summary states:

```text
PENDING -> PAID
PENDING -> EXPIRED
```

A failed attempt does not fail the Payment summary because another attempt may still be allowed before the Order
reservation expires.

### Callback policy

- Verify the VNPay signature and merchant code before accessing business state.
- Resolve `vnp_TxnRef` to an exact PaymentAttempt.
- Lock the Order first, then lock and revalidate the attempt, to preserve the Order-first lock order already used by
  payment success and checkout expiry.
- Compare the callback amount with the attempt snapshot.
- Enforce VND at initialization and on the stored attempt. VNPay's documented PAY IPN does not echo `vnp_CurrCode`, so
  currency consistency cannot depend on an absent callback field.
- Persist `vnp_TransactionNo`, the response code, and completion time, but never persist the raw signed query or secure
  hash.
- A failed provider result marks only the attempt `FAILED`; it does not release Inventory immediately.
- A successful result updates Attempt, Payment, Order, and Inventory in one transaction.
- The same callback repeated is idempotent.
- A success after Order expiry cannot reopen the Order or consume released Inventory.
- If a different verified provider transaction succeeds after the Payment is already paid, retain enough attempt data
  to diagnose a potential duplicate charge, acknowledge the callback without repeating Order/Inventory side effects,
  and leave financial reconciliation for a later operational/refund CR.

## Target API

### Checkout initialization

`POST /checkout` continues returning an Order plus payment initialization data. Internally, it delegates payment
initialization to the same attempt use case as manual retry.

The existing response field can remain compatible:

```json
{
  "payment": {
    "vnpUrl": "...",
    "attemptReference": "opaque-reference",
    "expiresAt": "..."
  }
}
```

The additional fields are useful for client state but the client must not construct or choose amount, currency, or
provider reference.

### Authenticated retry

Add:

```text
POST /orders/{orderId}/payment-attempts
```

The endpoint:

- obtains the customer from the authenticated principal;
- applies owner-scoped Order lookup;
- creates or reuses the active attempt;
- returns the VNPay URL and attempt metadata;
- rejects confirmed, expired, foreign, or otherwise ineligible Orders without exposing another customer's Order.

### Provider callback

Keep the exact public provider endpoint unless compatibility testing identifies a reason to rename it:

```text
GET /payments/vnpay_ipn
```

The route remains public because authenticity comes from the provider signature, not customer authentication. Only
this exact callback route should be public; retry routes remain authenticated.

## Database Migration

Add `changeset-23.sql` after changeset 22. Do not edit an applied changeset.

### Payment summary

- Change `payments.status` from MySQL `ENUM` to bounded `VARCHAR`.
- Migrate `UNPAID` to `PENDING`; preserve `PAID`.
- Mark legacy unpaid Payments belonging to `PAYMENT_EXPIRED` Orders as `EXPIRED`.
- Add a unique constraint on `payments.order_id` after guarding against legacy duplicates.
- Retain the existing payment date as the paid timestamp, renaming only if migration clarity outweighs compatibility
  cost.

### Payment attempts

Create `payment_attempts` with at least:

```text
id                       BIGINT primary key
payment_id               BIGINT not null
provider_reference       VARCHAR(100) not null
provider_transaction_id  VARCHAR(32) null
status                   VARCHAR(32) not null
expected_amount          DECIMAL(15,2) not null
currency                 VARCHAR(3) not null
response_code            VARCHAR(16) null
created_at               DATETIME(6) not null
expires_at               DATETIME(6) not null
completed_at             DATETIME(6) null
```

Add:

- FK from attempt to Payment;
- unique constraint on `provider_reference`;
- unique constraint on non-null provider transaction ID using MySQL's multiple-null behavior;
- index supporting lookup by Payment/status/expiry;
- positive amount, supported currency, and bounded status constraints where compatible with the project MySQL
  baseline.

Legacy paid Payments may remain without an attempt because their provider transaction identity cannot be reconstructed
safely. New payment flows must always use an attempt.

## Slice 1 - Attempt Model, Initialization, and Retry

1. Add changeset 23 and the `PaymentAttempt` persistence model.
2. Replace general Payment setters with named lifecycle operations.
3. Introduce attempt reference generation with an opaque, globally unique value of at most 100 characters.
4. Implement a transactional attempt-preparation use case:
   - owner-scope and lock the Order;
   - validate Order/payment/reservation state;
   - return the current active attempt or expire it and create a new one;
   - snapshot amount and currency from the Order;
   - cap attempt expiry at the reservation expiry.
5. Generate the provider URL from the persisted attempt after the preparation transaction completes.
6. Make Checkout initialization delegate to this use case.
7. Add the authenticated retry endpoint.
8. Harden VNPay URL generation:
   - inject `Clock`;
   - use `java.time` with `Asia/Ho_Chi_Minh` and a thread-safe formatter;
   - avoid a shared mutable `Mac` instance;
   - use explicit character encoding;
   - validate the amount can be represented as VNPay's integer value multiplied by 100;
   - avoid logging the complete URL, signature, or query.
9. Preserve the existing controlled `PAYMENT_INITIALIZATION_FAILED` behavior when URL creation fails after Checkout
   has committed.

Likely new components:

- `payment/domain/PaymentAttempt.java`
- `payment/domain/PaymentAttemptStatus.java`
- `payment/persistence/PaymentAttemptRepository.java`
- a payment-attempt application service and transactional coordinator
- retry request/response API DTOs if the existing initialization DTO is insufficient
- `changeset-23.sql`

Likely modified components:

- `Payment`, `PaymentStatus`, `PaymentRepository`
- `PaymentService`, `InitPaymentRequest`, `InitPaymentResponse`
- `PaymentController`
- `VNPayService`, `CryptoService`, `DateUtil`
- `CheckoutServiceImpl`
- security and OpenAPI boundary tests
- Liquibase master changelog

## Slice 2 - Attempt-Aware Callback and Terminal Lifecycle

1. Parse and validate the required callback fields after signature verification.
2. Resolve the attempt by provider reference rather than parsing the reference as an Order ID.
3. Move callback orchestration out of `OrderServiceImpl` into the Payment application boundary.
4. In the successful callback transaction:
   - lock and recheck the Order;
   - lock and recheck the attempt;
   - verify amount and attempt ownership;
   - lock Inventory balances in ascending Variant ID order;
   - consume the exact OrderItem reservations;
   - mark the attempt `SUCCEEDED` and persist provider transaction data;
   - mark Payment `PAID`;
   - transition Order to `CONFIRMED`.
5. In a signed failed callback, mark the matching pending attempt `FAILED` without changing Order or Inventory.
6. Handle callback repetition and out-of-order delivery without downgrading terminal success or applying inventory
   effects twice.
7. Extend checkout expiry so it marks Payment and pending attempts `EXPIRED` in the same Order-locked transaction that
   releases Inventory.
8. Preserve the current VNPay response-code contract where it is correct, while distinguishing unknown reference,
   invalid amount, already completed, and internal failure consistently.
9. Remove full IPN parameter logging and log only safe identifiers/status metadata.

## Proportional Test Plan

### Domain and application

- New Checkout creates a pending Payment and one attempt with the Order amount/currency snapshot.
- Retry while the active attempt is valid reuses it; retry after failed/expired attempt creates a new reference.
- Confirmed, expired, foreign, or reservation-expired Orders cannot start another attempt.
- A failed URL-generation step leaves the committed Order recoverable through retry.
- Payment and attempt transition methods reject invalid state changes.

### VNPay adapter

- With a fixed Clock, initialization emits the exact amount multiplied by 100, attempt reference, VND currency,
  GMT+7 creation time, and bounded expiry.
- URL signing is deterministic for a known fixture and signature verification rejects modified input.
- Concurrent signing calls do not share mutable cryptographic state.
- No test or production logger emits the secure hash or complete signed URL.

### Callback and consistency

- A verified success for the exact attempt updates Attempt, Payment, Order, and Inventory once.
- Invalid signature is rejected before persistence access.
- Unknown reference or amount mismatch does not mark Payment paid.
- A signed failed result marks only the attempt failed and permits a later retry.
- Duplicate success and failure-after-success are idempotent and never downgrade state.
- A callback arriving after Order expiry cannot consume released stock or reopen the Order.
- A callback-versus-expiry race has exactly one Order/Inventory result.
- Two simultaneous retry requests create or return one active attempt.
- Unrelated Orders are not globally serialized.

### API and security

- The VNPay IPN route remains public.
- Retry requires authentication and owner-scoped access.
- Customer-supplied amount, currency, Payment ID, or provider reference is never accepted.
- Checkout response compatibility remains intact with optional attempt metadata.

### MySQL migration

- Existing `PAID` and `UNPAID` rows migrate to the correct summary status.
- Legacy paid rows may exist without attempts.
- One Payment per Order and unique attempt/provider identities are enforced.
- Monetary, status, timestamp, FK, and lookup-index definitions exist as planned.

Do not add an exhaustive test matrix for every VNPay response code. Protect the state transitions, security boundary,
idempotency, and one meaningful concurrency race.

## Verification Commands

Use final test names established during implementation. Expected focused verification:

```powershell
.\mvnw.cmd '-Dtest=PaymentServiceTest,PaymentAttemptServiceTest,VNPayServiceTest,VNPayIpnHandlerTest,PaymentControllerSecurityTest,PaymentConcurrencyTest,PaymentMigrationTest,CheckoutServiceImplTest,ExpiredCheckoutCleanupTest' test
```

Then run:

```powershell
.\mvnw.cmd spotless:check
.\mvnw.cmd test
git diff --check
```

Migration and concurrency tests require Docker/Testcontainers. Report unavailable Docker as a skipped environmental
check rather than an application success.

## Compatibility Impact

- `POST /checkout` remains the initial payment entry point.
- The Checkout response keeps `payment.vnpUrl`; attempt metadata may be added compatibly.
- The public VNPay IPN route remains unchanged.
- New VNPay references identify attempts rather than Orders. Existing pending legacy Payments without attempts can
  create their first attempt through retry if their Order is still eligible.
- Admin Order filtering can continue using the Payment summary status rather than joining attempts.
- Existing paid Order and Payment history is retained even when no historical provider transaction reference exists.

## Risks and Mitigations

### Duplicate active attempts

Risk: concurrent retries create multiple usable payment URLs and increase the chance of duplicate charges.

Mitigation: serialize attempt creation with the Order lock, reuse a non-expired pending attempt, and enforce unique
provider references in the database.

### Provider success after local expiry

Risk: Inventory has already been released when a late provider success arrives.

Mitigation: Order-first locking and state recheck prevent reopening or consuming released Inventory. Persist safe
transaction evidence for operational follow-up; do not silently mutate fulfillment state.

### Duplicate provider charges

Risk: an older attempt reports success after another attempt already paid the Order.

Mitigation: never repeat Order/Inventory effects, keep provider transaction IDs unique, retain attempt evidence, and
defer automated refund/reconciliation until that policy is designed.

### Migration of legacy Payment rows

Risk: existing rows may violate the intended one-Payment-per-Order rule or lack reconstructable attempt metadata.

Mitigation: add a migration guard for duplicate Order references, preserve paid summaries without invented attempt
data, and require attempts only for new initialization/callback flows.

### Sensitive provider data in logs

Risk: signed URLs or callback maps expose signatures and payment metadata.

Mitigation: remove full query logging and log only safe internal reference/status fields without hashes, secrets, or
complete URLs.

### Time and cryptographic concurrency

Risk: incorrect timezone or shared mutable formatter/MAC state generates invalid requests under load.

Mitigation: use injected Clock, `Asia/Ho_Chi_Minh`, immutable formatters, and isolated MAC computation with fixed
fixture and concurrency tests.

## Assumptions

- Retry is a required M1 behavior while the Order reservation remains valid.
- Only one payment provider and one currency are needed in M1.
- The application does not need to store or expose the complete signed VNPay URL after returning it.
- A provider callback is authoritative only after signature, merchant, reference, amount, and current-state checks.
- Provider calls that may perform network I/O remain outside long database transactions.

## Out of Scope

- Additional payment providers or a general provider plugin architecture.
- Guest Checkout.
- Cancellation, refund, partial refund, chargeback, or restock policy.
- VNPay transaction-query and refund APIs.
- Automatic duplicate-charge reconciliation.
- Webhook queues, transactional outbox, message broker, or distributed locks.
- Storing raw provider requests, signed queries, secrets, or full payment URLs.
- A repository-wide package or API reorganization.
