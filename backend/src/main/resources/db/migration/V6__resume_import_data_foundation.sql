ALTER TABLE source_document
    ADD COLUMN document_type VARCHAR(20) NOT NULL DEFAULT 'RESUME',
    ADD COLUMN content_type VARCHAR(100),
    ADD COLUMN file_size_bytes BIGINT,
    ADD COLUMN file_hash VARCHAR(64),
    ADD COLUMN error_message TEXT,
    ADD COLUMN updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD CONSTRAINT chk_source_document_document_type
        CHECK (document_type IN ('RESUME', 'CSV')),
    ADD CONSTRAINT chk_source_document_upload_status
        CHECK (upload_status IN (
            'UPLOADED',
            'EXTRACTING',
            'EXTRACTED',
            'NEEDS_OCR',
            'FAILED',
            'APPROVED',
            'REJECTED'
        ));

CREATE INDEX idx_source_document_upload_status
    ON source_document(upload_status);

CREATE INDEX idx_source_document_file_hash
    ON source_document(file_hash);

CREATE TABLE import_profile (
    id BIGSERIAL PRIMARY KEY,
    source_document_id BIGINT NOT NULL UNIQUE,
    extraction_method VARCHAR(20),
    extracted_text TEXT,
    extracted_data JSONB,
    confidence JSONB,
    resource_id BIGINT,
    reject_reason TEXT,
    reviewed_by BIGINT,
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_import_profile_source_document
        FOREIGN KEY (source_document_id)
        REFERENCES source_document(id)
        ON DELETE CASCADE,
    CONSTRAINT chk_import_profile_extraction_method
        CHECK (extraction_method IS NULL OR extraction_method = 'PDFBOX'),
    CONSTRAINT fk_import_profile_resource
        FOREIGN KEY (resource_id)
        REFERENCES resource(id)
        ON DELETE SET NULL,
    CONSTRAINT fk_import_profile_reviewed_by
        FOREIGN KEY (reviewed_by)
        REFERENCES app_user(id)
        ON DELETE SET NULL
);

CREATE INDEX idx_import_profile_resource_id
    ON import_profile(resource_id);

INSERT INTO skill AS existing_skill (name, category, aliases)
VALUES
    ('React', 'Frontend', ARRAY['ReactJS', 'React.js']::TEXT[]),
    ('Java', 'Programming', ARRAY[]::TEXT[]),
    ('Spring Boot', 'Backend', ARRAY['SpringBoot']::TEXT[]),
    ('AWS', 'Cloud', ARRAY['Amazon Web Services']::TEXT[]),
    ('SQL', 'Database', ARRAY[]::TEXT[]),
    ('Python', 'Programming', ARRAY[]::TEXT[])
ON CONFLICT (name) DO UPDATE
SET aliases = ARRAY(
    SELECT DISTINCT alias_value
    FROM unnest(
        COALESCE(existing_skill.aliases, ARRAY[]::TEXT[]) || EXCLUDED.aliases
    ) AS aliases(alias_value)
    ORDER BY alias_value
);
