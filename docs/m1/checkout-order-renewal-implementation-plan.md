# Checkout and Order Renewal Implementation Plan

Target branch: `phase/m1-ecommerce-renewal`

Issue/feature branch: none. This renewal continues directly on the M1 phase branch to shorten the milestone feedback
loop. Implementation should still be split into independently verified slices so checkout creation, reservation
lifecycle, and Order transitions can be reviewed separately.

## Recommended Scope and Approach

Replace the current action-style `placeOrder` flow with an authenticated Checkout use case. Checkout is the database
transaction boundary that validates the current customer Cart and shipping address, locks the relevant catalog and
Inventory rows, recalculates prices, reserves stock, creates immutable Order snapshots, clears the Cart, and commits.
Payment initialization must happen only after that transaction commits.

Implement the renewal in three slices:

1. Build the atomic Checkout transaction with structured shipping snapshots, inventory reservation, Cart clearing,
   and submit idempotency.
2. Complete the reservation lifecycle by consuming stock on verified payment success and releasing it when an unpaid
   checkout expires.
3. Move Order status changes behind explicit domain transitions and renew the affected customer/admin boundaries.

Keep the existing single-store and single-currency model. Continue using VNPay as the only payment provider in this
round, but do not redesign payment attempts, cancellation, refunds, or fulfillment as part of this plan.

## Current Behavior and Problems

- `POST /orders/create` accepts free-form `address` and `mobileNumber` instead of an owned address-book identifier.
- `OrderServiceImpl.placeOrder()` reads customer Cart items without locking the Cart.
- Availability checks only establish that an Inventory balance has `onHand > reserved`; they do not verify that
  available quantity covers the requested Cart quantity.
- Checkout does not reserve or decrement Inventory, so two customers can purchase beyond available stock.
- Cart clearing is commented out.
- `paymentService.init()` is called inside the database transaction.
- Retrying the same request can create duplicate Orders and reservations because no idempotency contract exists.
- Order status can be assigned directly through a generic admin patch without validating the current state or actor.
- A public application method can mark a Payment as paid without a verified provider callback, even though it is not
  currently exposed by a controller.
- The verified VNPay callback locks the Order and is idempotent for an already-paid Payment, but it does not move the
  Order state or consume reserved Inventory.
- Customer Order lookup is already owner-scoped and should be preserved.
- `OrderItem` already snapshots Product ID/name, Variant ID/SKU/description, quantity, base price, discount, final
  price, subtotal, and currency. That part should be retained rather than rebuilt.

## Proposed Business Policies Pending Confirmation

- Clear Cart items immediately after the Checkout database transaction succeeds; keep the customer Cart aggregate.
- Reserve Inventory for 15 minutes by default. Configure this independently from VNPay, while using the same default
  duration.
- When an unpaid checkout expires, release its reservation and mark the Order `PAYMENT_EXPIRED`.
- Do not automatically restore expired Order items to Cart because the customer may have changed the Cart meanwhile.
- A signed failed payment result leaves the Order pending until expiry so the customer can retry payment.
- Payment success consumes the reservation and confirms the Order exactly once.
- Cancellation, refund, restock, and full payment-attempt modeling remain separate renewal work.

These policies materially affect behavior and must be approved before implementation begins.

## Target API Contract

Replace the legacy create route with:

```text
POST /checkout
Authorization: Bearer <access-token>
Idempotency-Key: <client-generated-key>

{
  "addressId": 123,
  "description": "Deliver during office hours"
}
```

The request never accepts user ID, Cart ID, price, total, currency, stock, reservation expiry, or raw shipping fields.
The authenticated principal selects the customer, the current customer Cart is the checkout source, and `addressId`
must resolve through an owner-scoped query.

Return a Checkout response containing:

```text
CheckoutResponse
- order
- payment
- replayed
```

- First successful submission returns `201 Created`.
- A replay with the same idempotency key and request returns the original Order without reserving or clearing again;
  it may return `200 OK` and can regenerate the payment URL while the Order is still pending.
- Reusing a key with a different request returns a conflict.
- Guest Checkout is not supported. A guest must authenticate and claim the guest Cart first.
- Remove `POST /orders/create`; do not keep a compatibility alias unless an actual consumer is identified before
  implementation.
- Keep existing customer/admin Order read routes unless renewal exposes a concrete correctness or security problem.

## Checkout Transaction

Use a non-transactional orchestration service around a dedicated transactional component. This structural boundary
makes it impossible for payment initialization to accidentally run inside the Checkout database transaction.

Inside the transaction:

1. Canonicalize and resolve the authenticated customer.
2. Validate and hash the idempotency key.
3. Lock the User row, then recheck whether an Order already exists for that customer/key.
4. If it exists, validate the request hash and return the existing result before consulting the live address or Cart.
5. Resolve `addressId` through `findByIdAndUserId`; a missing or foreign address produces the same not-found result.
6. Lock the customer Cart and load every Cart item with the catalog data needed for saleability and snapshots.
7. Reject an empty Cart or any non-saleable SKU.
8. Lock Product/Variant rows and Inventory balances in ascending ID order.
9. Resolve every price at one injected `Clock` instant and verify `available >= requested quantity` for every line.
10. Reserve each quantity.
11. Copy every structured address field into the Order shipping snapshot.
12. Create the Order, Payment placeholder, and immutable OrderItem snapshots; calculate total and currency at the
    server.
13. Delete the checked-out Cart items while retaining the customer Cart.
14. Commit all database changes atomically.

The recommended lock order is:

```text
User -> customer Cart -> Products/Variants by ascending ID -> Inventory balances by ascending Variant ID
```

This serializes concurrent checkout submissions for one customer, prevents checkout from racing with price/Variant
updates that use the Product lock, and reduces deadlock risk when different customers share multiple SKUs.

After commit, initialize VNPay from the persisted Order snapshot. If payment initialization fails, keep the Order and
reservation and return a controlled provider failure. A retry with the same key must reuse the Order and retry payment
initialization rather than duplicating business state.

## Idempotency Contract

Require a bounded, nonblank `Idempotency-Key` header and persist only its SHA-256 hash. Add a second SHA-256 request
hash covering stable normalized request fields such as `addressId` and description. Do not include the request IP or
current Cart contents in that hash.

Store idempotency on the Order:

```text
idempotency_key_hash  BINARY(32)
checkout_request_hash BINARY(32)
```

Add a unique database constraint on `(user_id, idempotency_key_hash)`. The User row lock prevents the normal
absent-row race for concurrent submissions by one customer; the unique constraint remains the final guarantee.

An idempotent replay must look up the existing Order before reading mutable resources. It therefore remains valid
after Cart clearing or address editing/deletion. A replay can generate another payment URL only while the Order is
still `PENDING_PAYMENT`; it must never reopen a confirmed or expired Order.

## Inventory Reservation Lifecycle

Add explicit domain operations to `InventoryBalance`:

- `reserve(quantity)` increases `reserved` only when enough quantity is available.
- `release(quantity)` decreases `reserved` without changing `onHand`.
- `consumeReserved(quantity)` decreases both `reserved` and `onHand` after successful payment.

Every operation must preserve:

```text
0 <= reserved <= onHand
available = onHand - reserved
```

The lifecycle is:

```text
Checkout committed
    -> PENDING_PAYMENT + reserved Inventory

Verified payment success
    -> consume reserved Inventory + CONFIRMED

Reservation timeout
    -> release reserved Inventory + PAYMENT_EXPIRED
```

For payment callbacks, lock the Order first, recheck amount/status, lock all referenced Inventory balances in
ascending Variant ID order, consume the exact OrderItem quantities, mark Payment paid, and transition the Order to
`CONFIRMED` in the same transaction. A repeated callback must return an idempotent result without consuming again.

Add a bounded scheduled cleanup for expired pending Orders. Select candidate IDs, then lock and recheck each Order's
status and expiry before releasing its reservation. Both callback and cleanup must acquire the Order lock first, so a
callback-versus-expiry race has exactly one final result.

Recommended configuration:

```text
app.checkout.reservation-lifetime=15m
app.checkout.cleanup-batch-size=100
app.checkout.cleanup-fixed-delay-ms=60000
```

## Order Domain and State Transitions

Replace direct status setters with named domain operations and validate transitions against the current state.

Target states for this round:

```text
PENDING_PAYMENT
CONFIRMED
PROCESSING
SHIPPED
DELIVERED
PAYMENT_EXPIRED
```

Allowed transitions:

```text
PENDING_PAYMENT -> CONFIRMED
PENDING_PAYMENT -> PAYMENT_EXPIRED
CONFIRMED       -> PROCESSING
PROCESSING      -> SHIPPED
SHIPPED         -> DELIVERED
```

- Only the verified payment callback may confirm an Order.
- Only the expiry flow may mark an unpaid Order expired.
- Admin may perform only the forward fulfillment transitions in this round.
- `deliveredAt` is assigned only by the valid transition to `DELIVERED`.
- Generic cancellation is not accepted until the cancellation/refund/restock policy is defined.
- Remove the application method that directly marks a customer Payment paid.

## Shipping Snapshot

Checkout accepts only an owned `addressId` and copies these values into the Order:

- recipient name;
- phone number;
- address line;
- ward/commune;
- district;
- province/city;
- optional postal code.

Order responses must read these snapshot columns, never the current `user_addresses` row. Editing or deleting an
address after Checkout cannot change Order history.

The migration must preserve legacy Orders. Rename/backfill the existing scalar address and phone columns where
possible, and leave newly introduced structured components nullable only for legacy rows that cannot be reconstructed.
All newly created Orders must be required by the domain factory to contain a complete structured snapshot.

## Database Migration

Add `changeset-21.sql` after changeset 20. Do not edit previously applied changesets.

Expected changes:

- migrate legacy `PENDING` rows to `PENDING_PAYMENT`;
- replace the MySQL `ENUM` Order status column with a bounded `VARCHAR` so later states do not require enum DDL;
- add idempotency key hash and checkout request hash;
- add reservation expiry and an index supporting bounded expiry cleanup;
- add Order-level currency;
- rename/backfill the legacy address and mobile columns and add the remaining shipping snapshot fields;
- add `uk_orders_user_idempotency` on customer plus key hash;
- retain existing Orders, Payments, and OrderItems;
- add practical non-negative/quantity constraints where the existing data can be migrated safely.

Do not add a separate reservation table in this round. Order status plus immutable OrderItem quantities provide the
reservation ledger, while `inventory_balances.reserved` remains the aggregate balance.

## Likely Components

New components:

- `checkout/api/CheckoutController.java`
- `checkout/api/dto/CheckoutRequest.java`
- `checkout/api/dto/CheckoutResponse.java`
- `checkout/application/CheckoutService.java`
- `checkout/application/CheckoutTransactionService.java`
- a small checkout idempotency hashing component;
- `checkout/application/ExpiredCheckoutCleanup.java`
- `config/CheckoutProperties.java`
- `order/domain/ShippingAddressSnapshot.java`
- `changeset-21.sql`

Likely modified components:

- `Order`, `OrderStatus`, `OrderRepository`, `OrderMapper`, and Order response DTOs;
- `OrderService`/`OrderServiceImpl` to remove checkout creation and unsafe direct payment mutation;
- `InventoryBalance` and `InventoryBalanceRepository` for ordered reservation locks;
- Cart repositories for locked checkout loading and Cart-item clearing;
- Product/Variant repositories for consistent ordered checkout snapshots;
- VNPay callback integration only enough to confirm an Order and consume its reservation;
- application error codes/problem mapping, application configuration, security/API documentation tests, and the
  Liquibase master changelog.

Avoid a `Checkout` entity: Checkout is an application use case, while the persisted result is the Order.

## Implementation Order

### Slice 1 - Atomic Checkout creation

1. Add changeset 21 with Order status, structured shipping, currency, idempotency, and expiry fields.
2. Introduce the structured shipping snapshot and Order creation invariants.
3. Add Inventory reservation operations and ordered lock queries.
4. Implement idempotent Checkout transaction and Cart clearing.
5. Add `/checkout` and call payment initialization outside the transaction.
6. Preserve existing Order reads and remove the legacy create route.
7. Run focused domain, application, API, migration, and checkout concurrency tests.

### Slice 2 - Reservation completion and expiry

1. Connect verified successful callbacks to Order confirmation and Inventory consumption.
2. Make duplicate callbacks idempotent across Payment, Order, and Inventory.
3. Add bounded expiry cleanup with lock-time state rechecks.
4. Cover callback-versus-expiry races and rollback behavior.
5. Run focused Checkout, Inventory, Order, and VNPay compatibility tests.

### Slice 3 - Order transition boundary

1. Replace arbitrary status assignment with domain transition methods.
2. Restrict the admin update flow to valid forward transitions.
3. Ensure customer/admin read DTOs expose stable shipping and Order snapshots.
4. Remove unsafe payment mutation and obsolete checkout compatibility queries.
5. Run the complete Checkout/Order verification and applicable repository checks.

After all slices pass, update `docs/m1/module-renewal-roadmap.md` only with verified behavior.

## Proportional Test Plan

### Domain/application tests

- Successful Checkout snapshots current SKU prices and owned structured address, reserves exact quantities, creates
  one Order, and clears Cart items.
- One insufficient SKU rejects the whole Checkout and rolls back every reservation, Order write, and Cart deletion.
- A missing or foreign address produces the same owner-scoped not-found response.
- Replaying the same key/request returns the original Order without another reservation; a different request under
  that key is rejected.
- Payment initialization failure leaves the committed Order recoverable through an idempotent retry.
- Valid and invalid Order transitions update timestamps and state correctly.

### API/security tests

- `/checkout` requires an authenticated customer, a valid idempotency header, and a valid request body.
- Guest Cart credentials cannot be used directly for Checkout.
- Customer Order reads remain owner-scoped, while admin reads/transitions retain the ADMIN boundary.
- The legacy create route is absent if no compatibility consumer is identified.

### Persistence/concurrency tests

- Two customers competing for the last available units cannot oversell.
- Concurrent requests with one customer/idempotency key create one Order and one reservation.
- Payment success consumes a reservation once; a repeated callback has no repeated side effect.
- Callback and expiry cleanup racing on the same Order result in either confirmed/consumed or expired/released state,
  never both.
- Unrelated SKUs are not globally serialized.

### MySQL migration test

- Existing Orders and OrderItem snapshots survive migration.
- Legacy `PENDING` status is migrated correctly.
- Idempotency uniqueness, expiry index, shipping columns, status representation, and monetary constraints exist.
- New Order rows can store a complete structured snapshot and reservation expiry.

Do not build an exhaustive matrix for every catalog visibility state or Order endpoint. Catalog, Pricing, Address, and
Cart modules already own detailed rules; Checkout needs representative integration boundaries and concurrency proof.

## Verification Commands

Use final test names when implementation establishes them. Expected focused verification:

```powershell
.\mvnw.cmd '-Dtest=CheckoutServiceImplTest,CheckoutControllerSecurityTest,CheckoutPersistenceTest,CheckoutConcurrencyTest,CheckoutMigrationTest,OrderServiceImplTest,VNPayIpnHandlerTest' test
```

Then run:

```powershell
.\mvnw.cmd spotless:check
.\mvnw.cmd test
git diff --check
```

MySQL migration and concurrency tests require Docker/Testcontainers. Report unavailable Docker as a skipped
environmental check rather than an application success.

## Compatibility and Migration Impact

- `POST /orders/create` is intentionally replaced by `POST /checkout`.
- Checkout request changes from free-form address/mobile fields to owned `addressId`.
- Existing customer/admin Order read routes remain unless a concrete issue requires change.
- Existing historical OrderItem pricing and SKU snapshots remain authoritative.
- Legacy Orders are preserved even when a complete structured address cannot be reconstructed.
- Cart is cleared after successful Checkout creation, before payment outcome.
- An Order can remain reserved and pending when payment initialization fails; idempotent retry and timeout cleanup are
  therefore required parts of the same renewal.
- No Redis, message broker, distributed lock, or new dependency is introduced.

## Risks and Mitigations

### Oversell and partial reservation

Risk: concurrent Checkout requests reserve the same available stock, or one failed line leaves earlier lines reserved.

Mitigation: lock balances in deterministic order and perform validation, reservation, Order creation, and Cart clearing
inside one transaction.

### Duplicate Orders

Risk: client or network retries create multiple Orders after an ambiguous response.

Mitigation: User lock, hashed idempotency key, request fingerprint, database uniqueness, and replay-before-live-state
resolution.

### Payment initialization failure after commit

Risk: an Order exists without a returned payment URL.

Mitigation: make payment initialization post-commit and retryable with the same idempotency key; release the
reservation through expiry if payment never succeeds.

### Callback versus expiry race

Risk: the same reservation is both consumed and released.

Mitigation: both flows lock and recheck the Order before locking Inventory; only a pending Order may finalize.

### Legacy shipping data

Risk: current scalar Order addresses cannot be decomposed reliably into structured fields.

Mitigation: preserve the known address/phone values, allow missing structured components only on legacy rows, and
enforce complete snapshots for every new Checkout in the domain.

### Scope expansion into Payment/Fulfillment

Risk: completing reservation lifecycle grows into payment-attempt, refund, cancellation, or shipping redesign.

Mitigation: touch the verified callback only enough to consume reservation and confirm the Order; keep attempts,
refunds, cancellation/restock, and fulfillment ownership in their later modules.

## Out of Scope

- Guest Checkout.
- Coupon, shipping-fee, or tax calculation.
- Multiple currencies or payment providers.
- Payment attempt/transaction history and retry policy beyond regenerating the current VNPay URL.
- Customer/admin cancellation, refunds, and restocking.
- Shipment/carrier integration.
- Automatic Cart restoration after payment expiry.
- A repository-wide API or package reorganization.
