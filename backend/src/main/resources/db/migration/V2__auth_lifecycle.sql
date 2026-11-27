CREATE TABLE auth_session (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    last_refreshed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_auth_session_user_active
    ON auth_session(user_id, expires_at)
    WHERE revoked_at IS NULL;

CREATE TABLE refresh_token (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL REFERENCES auth_session(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX idx_refresh_token_session
    ON refresh_token(session_id);

CREATE UNIQUE INDEX uq_refresh_token_current_session
    ON refresh_token(session_id)
    WHERE consumed_at IS NULL AND revoked_at IS NULL;

CREATE TABLE password_reset_token (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_password_reset_user_pending
    ON password_reset_token(user_id, expires_at)
    WHERE consumed_at IS NULL;
