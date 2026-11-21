-- Storage types used by resource-service. The files themselves are in S3 (LocalStack), here are only the places.
CREATE TABLE IF NOT EXISTS storages (
    id           BIGSERIAL    PRIMARY KEY,
    storage_type VARCHAR(20)  NOT NULL CHECK (storage_type IN ('STAGING', 'PERMANENT')),
    bucket       VARCHAR(255) NOT NULL,
    path         VARCHAR(255) NOT NULL,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_storages_bucket_path UNIQUE (bucket, path)
);

-- Pre-created storages. The buckets are created in LocalStack by init-scripts/localstack/01-create-bucket.sh
INSERT INTO storages (storage_type, bucket, path)
VALUES ('STAGING', 'staging-bucket', '/files'),
       ('PERMANENT', 'permanent-bucket', '/files')
ON CONFLICT (bucket, path) DO NOTHING;
