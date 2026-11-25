
-- RESOURCE ALLOCATION TRACKER
-- Initial Database Schema
-- Flyway Migration: V1

-- 1. APP USER TABLE
CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL
        CHECK (role IN ('ADMIN', 'MANAGER', 'VIEWER')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. SOURCE DOCUMENT TABLE
CREATE TABLE source_document (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    s3_key VARCHAR(500),
    uploaded_by BIGINT REFERENCES app_user(id),
    upload_status VARCHAR(30) NOT NULL DEFAULT 'UPLOADED',
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. RESOURCE TABLE
CREATE TABLE resource (
    id BIGSERIAL PRIMARY KEY,
    employee_id VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    designation VARCHAR(100),
    location VARCHAR(150),
    total_experience_years DECIMAL(5,2) DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),
    source VARCHAR(20) NOT NULL DEFAULT 'MANUAL'
        CHECK (source IN ('MANUAL', 'PDF', 'CSV')),
    source_document_id BIGINT REFERENCES source_document(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. SKILL TABLE
CREATE TABLE skill (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    category VARCHAR(100),
    aliases TEXT[]
);

-- 5. RESOURCE SKILL TABLE
CREATE TABLE resource_skill (
    resource_id BIGINT NOT NULL REFERENCES resource(id) ON DELETE CASCADE,
    skill_id BIGINT NOT NULL REFERENCES skill(id) ON DELETE CASCADE,
    proficiency INTEGER CHECK (proficiency BETWEEN 1 AND 5),
    years DECIMAL(5,2) DEFAULT 0,
    is_primary BOOLEAN DEFAULT FALSE,
    PRIMARY KEY (resource_id, skill_id)
);

-- 6. PROJECT TABLE
CREATE TABLE project (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE,
    name VARCHAR(200) NOT NULL,
    client_name VARCHAR(200),
    project_manager VARCHAR(150),
    start_date DATE,
    end_date DATE,
    status VARCHAR(30) DEFAULT 'ACTIVE'
);

-- 7. ALLOCATION TABLE
CREATE TABLE allocation (
    id BIGSERIAL PRIMARY KEY,
    resource_id BIGINT NOT NULL REFERENCES resource(id),
    project_id BIGINT NOT NULL REFERENCES project(id),
    role VARCHAR(100),
    allocation_percent DECIMAL(5,2) NOT NULL
        CHECK (allocation_percent > 0 AND allocation_percent <= 100),
    start_date DATE NOT NULL,
    end_date DATE,
    CHECK (end_date IS NULL OR end_date >= start_date)
);

--8. CHAT MESSAGE TABLE
CREATE TABLE chat_message (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES app_user(id),
    session_id VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL
        CHECK (role IN ('USER', 'ASSISTANT')),
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. AUDIT LOG TABLE
CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    entity VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    action VARCHAR(50) NOT NULL,
    changed_by BIGINT REFERENCES app_user(id),
    changed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    old_value JSONB,
    new_value JSONB
);


-- INDEXES FOR BETTER QUERY PERFORMANCE


CREATE INDEX idx_resource_name ON resource(name);
CREATE INDEX idx_resource_location ON resource(location);
CREATE INDEX idx_resource_skill_skill ON resource_skill(skill_id);
CREATE INDEX idx_allocation_resource ON allocation(resource_id);
CREATE INDEX idx_allocation_project ON allocation(project_id);
CREATE INDEX idx_allocation_dates ON allocation(start_date, end_date);
CREATE INDEX idx_chat_user ON chat_message(user_id);
CREATE INDEX idx_chat_session ON chat_message(session_id);
CREATE INDEX idx_audit_entity ON audit_log(entity, entity_id);


