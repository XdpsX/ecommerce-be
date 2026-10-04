-- liquibase formatted sql

-- changeset xdpsx:user-address-cr3-profile-address-book
-- comment: Add customer-owned structured address book rows.

CREATE TABLE user_addresses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    recipient_name VARCHAR(128) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    address_line VARCHAR(255) NOT NULL,
    ward_commune VARCHAR(128) NOT NULL,
    district VARCHAR(128) NOT NULL,
    province_city VARCHAR(128) NOT NULL,
    postal_code VARCHAR(20) NULL,
    CONSTRAINT pk_user_addresses PRIMARY KEY (id),
    CONSTRAINT fk_user_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_user_addresses_user_id (user_id)
);
