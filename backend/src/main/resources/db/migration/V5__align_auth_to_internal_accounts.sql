DROP TABLE IF EXISTS auth_rate_limit_bucket;
DROP TABLE IF EXISTS email_verification_token;
DROP TABLE IF EXISTS password_reset_token;

ALTER TABLE app_user
    DROP COLUMN IF EXISTS email_verified,
    ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_app_user_active_role
    ON app_user(role)
    WHERE is_active = TRUE;

ALTER TABLE source_document
    DROP CONSTRAINT source_document_uploaded_by_fkey,
    ADD CONSTRAINT source_document_uploaded_by_fkey
        FOREIGN KEY (uploaded_by) REFERENCES app_user(id) ON DELETE SET NULL;

ALTER TABLE chat_message
    DROP CONSTRAINT chat_message_user_id_fkey,
    ADD CONSTRAINT chat_message_user_id_fkey
        FOREIGN KEY (user_id) REFERENCES app_user(id) ON DELETE CASCADE;

ALTER TABLE audit_log
    DROP CONSTRAINT audit_log_changed_by_fkey,
    ADD CONSTRAINT audit_log_changed_by_fkey
        FOREIGN KEY (changed_by) REFERENCES app_user(id) ON DELETE SET NULL;
