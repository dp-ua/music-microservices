CREATE TABLE IF NOT EXISTS resources (
    id         BIGSERIAL PRIMARY KEY,
    file_data  BYTEA        NOT NULL,
    file_size  BIGINT       NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP    NOT NULL DEFAULT NOW(),
    is_deleted BOOLEAN      NOT NULL DEFAULT FALSE
);
