-- V3: Add folder organization
CREATE TABLE IF NOT EXISTS folder (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    parent_id BIGINT REFERENCES folder(id) ON DELETE CASCADE,
    created_at TIMESTAMP,
    last_modified TIMESTAMP,
    UNIQUE(name, parent_id)
);

ALTER TABLE image_metadata ADD COLUMN IF NOT EXISTS folder_id BIGINT REFERENCES folder(id) ON DELETE SET NULL;
