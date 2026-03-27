CREATE TABLE IF NOT EXISTS image_metadata (
    id VARCHAR(255) PRIMARY KEY,
    filename VARCHAR(255),
    content_type VARCHAR(255),
    size BIGINT,
    s3_key VARCHAR(255),
    s3_url VARCHAR(1024),
    thumbnail_key VARCHAR(255),
    thumbnail_url VARCHAR(1024),
    uploaded_at TIMESTAMP,
    last_modified TIMESTAMP
);
