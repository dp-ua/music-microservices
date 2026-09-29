CREATE TABLE IF NOT EXISTS songs (
    id           BIGINT       PRIMARY KEY,
    name         VARCHAR(255) NOT NULL,
    artist       VARCHAR(255),
    album        VARCHAR(255),
    duration     VARCHAR(50),
    release_year VARCHAR(10),
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT NOW(),
    is_deleted   BOOLEAN      NOT NULL DEFAULT FALSE
);
