ALTER TABLE refresh_token
    ALTER COLUMN token_hash TYPE VARCHAR(64);

ALTER TABLE password_reset_token
    ALTER COLUMN token_hash TYPE VARCHAR(64);
