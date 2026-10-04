# Customer address and future shipping snapshot contract

The customer address book is mutable reference data owned by the authenticated user. It is not order history.

The profile `name` is the application's display name, required, and limited to 64 characters. Any authenticated
user may change it; the endpoint does not change email, role, authentication provider, password, avatar, or the
profile stored by an OAuth provider.

## Address-book fields

Every address has a required `recipientName` (maximum 128 characters), `phoneNumber` (optional leading `+` followed by 8–15 ASCII digits), `addressLine` (maximum 255 characters), `wardCommune` (maximum 128 characters), `district` (maximum 128 characters), and `provinceCity` (maximum 128 characters). `postalCode` is optional and has a maximum length of 20 characters.

Text values are trimmed before Bean Validation and persistence. A blank optional `postalCode` is normalized to `null`. The address response contains the generated address identifier and these structured fields; it never exposes the owning `User` entity.

## Ownership and lifecycle

The address book is scoped to the authenticated customer. List, replace, and delete lookups are owner-scoped. A missing address and an address belonging to another user both produce the same `RESOURCE_NOT_FOUND` response, without address content or ownership details in the error.

Customers may create multiple addresses, replace mutable fields, and hard-delete rows. There is no default address, custom ordering, or uniqueness rule in this scope. Profile display names are local application data and may be changed by any authenticated user; OAuth provider profile data is not updated by this endpoint.

## Future checkout boundary

A later Checkout flow may accept an `addressId`. It must verify that the selected row belongs to the checkout user and copy every structured address field into the Order shipping snapshot inside the checkout transaction.

Historical Orders must never resolve shipping data from a live `user_addresses` row. Editing or deleting an address therefore cannot change an existing Order.

The current `orders.address` and `orders.mobile_number` columns are legacy scalar fields. This CR does not claim that the current Order model already stores the complete structured shipping snapshot, and it does not add an Order-to-address-book foreign key.
