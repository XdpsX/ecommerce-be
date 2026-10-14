-- liquibase formatted sql

-- changeset ecommerce:identity-baseline
-- comment: Squashed identity schema for fresh development databases.

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    email VARCHAR(64) NOT NULL,
    password VARCHAR(255),
    avatar VARCHAR(255),
    role VARCHAR(32) NOT NULL,
    auth_provider VARCHAR(32) NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE TABLE refresh_sessions (
    id CHAR(36) NOT NULL,
    user_id BIGINT NOT NULL,
    secret_hash BINARY(32) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    CONSTRAINT pk_refresh_sessions PRIMARY KEY (id),
    CONSTRAINT fk_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX ix_refresh_sessions_user_id (user_id)
);

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


