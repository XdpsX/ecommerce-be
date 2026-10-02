-- liquibase formatted sql

-- changeset xdpsx:checkout-renewal-legacy-pending-boundary
-- comment: Move pre-renewal pending Orders without reservations to terminal states.

UPDATE orders o
SET o.status = CASE
    WHEN EXISTS (SELECT 1 FROM payments p WHERE p.order_id = o.id AND p.status = 'PAID') THEN 'CONFIRMED'
    ELSE 'PAYMENT_EXPIRED'
END
WHERE o.status = 'PENDING_PAYMENT'
  AND o.reservation_expires_at IS NULL;
