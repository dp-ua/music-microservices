-- The MP3 file itself lives in the cloud storage (S3); the table keeps only its location.
CREATE TABLE IF NOT EXISTS resources (
    id             BIGSERIAL PRIMARY KEY,
    storage_bucket VARCHAR(255) NOT NULL,
    storage_key    VARCHAR(512) NOT NULL,
    file_size      BIGINT       NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);
