CREATE TABLE IF NOT EXISTS app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);

-- Insert default admin user (password: admin, BCrypt encoded)
INSERT INTO app_user (username, password, role, enabled)
VALUES ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ROLE_ADMIN', true)
ON CONFLICT (username) DO NOTHING;
