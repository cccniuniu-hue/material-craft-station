CREATE TABLE IF NOT EXISTS identities (
    id VARCHAR(36) PRIMARY KEY,
    display_name VARCHAR(40) NOT NULL,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP(6) NOT NULL
);
CREATE TABLE IF NOT EXISTS templates (
    id VARCHAR(36) PRIMARY KEY,
    title VARCHAR(100) NOT NULL,
    category VARCHAR(40) NOT NULL,
    cover_url VARCHAR(255) NOT NULL,
    description VARCHAR(500) NOT NULL,
    required_image_count INT NOT NULL,
    max_file_size_bytes BIGINT NOT NULL,
    sort_order INT NOT NULL
);
CREATE TABLE IF NOT EXISTS assets (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    relative_path VARCHAR(255) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(40) NOT NULL,
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES identities(id),
    INDEX idx_assets_user (user_id)
);
CREATE TABLE IF NOT EXISTS generation_tasks (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    template_id VARCHAR(36) NOT NULL,
    status VARCHAR(20) NOT NULL,
    stage VARCHAR(20) NOT NULL,
    result_url VARCHAR(255),
    error_message VARCHAR(500),
    delay_ms INT NOT NULL,
    fail_stage VARCHAR(20),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    FOREIGN KEY (user_id) REFERENCES identities(id),
    FOREIGN KEY (template_id) REFERENCES templates(id),
    INDEX idx_tasks_user_created (user_id, created_at)
);
CREATE TABLE IF NOT EXISTS task_assets (
    task_id VARCHAR(36) NOT NULL,
    asset_id VARCHAR(36) NOT NULL,
    position INT NOT NULL,
    PRIMARY KEY (task_id, position),
    UNIQUE (task_id, asset_id),
    FOREIGN KEY (task_id) REFERENCES generation_tasks(id),
    FOREIGN KEY (asset_id) REFERENCES assets(id)
);
CREATE TABLE IF NOT EXISTS task_steps (
    task_id VARCHAR(36) NOT NULL,
    stage VARCHAR(20) NOT NULL,
    position INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP(6),
    finished_at TIMESTAMP(6),
    error_message VARCHAR(500),
    PRIMARY KEY (task_id, stage),
    FOREIGN KEY (task_id) REFERENCES generation_tasks(id)
);
