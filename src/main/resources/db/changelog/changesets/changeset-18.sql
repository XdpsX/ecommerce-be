-- liquibase formatted sql

-- changeset xdpsx:user-auth-cr2-refresh-sessions
-- comment: Add revocable rotating refresh sessions for LOCAL accounts.

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
