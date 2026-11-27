ALTER TABLE app_user
    ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE email_verification_token (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_email_verification_user_pending
    ON email_verification_token(user_id, expires_at)
    WHERE consumed_at IS NULL;

CREATE TABLE auth_rate_limit_bucket (
    bucket_key VARCHAR(64) PRIMARY KEY,
    window_started_at TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL CHECK (request_count > 0)
);

CREATE INDEX idx_auth_rate_limit_window
    ON auth_rate_limit_bucket(window_started_at);
