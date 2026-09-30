-- liquibase formatted sql

-- changeset xdpsx:user-auth-cr1-email-identity
-- comment: Canonicalize LOCAL email identity and enforce global uniqueness.

-- The guard must reject canonical collisions before the UPDATE changes any user row. A temporary table keeps the
-- collision check scoped to this migration connection and its named key makes an operator-facing failure clear.
CREATE TEMPORARY TABLE user_email_identity_guard (
    canonical_email VARCHAR(64) NOT NULL,
    CONSTRAINT uk_user_email_identity_guard UNIQUE (canonical_email)
);

INSERT INTO user_email_identity_guard (canonical_email)
SELECT LOWER(REGEXP_REPLACE(email, '^[\\x00-\\x20]+|[\\x00-\\x20]+$', ''))
FROM users;

UPDATE users
SET email = LOWER(REGEXP_REPLACE(email, '^[\\x00-\\x20]+|[\\x00-\\x20]+$', ''));

ALTER TABLE users
    ADD CONSTRAINT uk_users_email UNIQUE (email);

DROP TEMPORARY TABLE user_email_identity_guard;
