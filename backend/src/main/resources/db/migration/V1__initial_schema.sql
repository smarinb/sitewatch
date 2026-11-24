CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE site (
    id                     BIGSERIAL PRIMARY KEY,
    owner_id               BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    name                   VARCHAR(120) NOT NULL,
    url                    VARCHAR(2048) NOT NULL,
    check_interval_minutes INT          NOT NULL DEFAULT 5 CHECK (check_interval_minutes BETWEEN 1 AND 1440),
    active                 BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (owner_id, url)
);

CREATE TABLE site_check (
    id               BIGSERIAL PRIMARY KEY,
    site_id          BIGINT      NOT NULL REFERENCES site (id) ON DELETE CASCADE,
    checked_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    up               BOOLEAN     NOT NULL,
    http_status      INT,
    response_time_ms INT,
    ssl_expires_at   TIMESTAMPTZ,
    wp_version       VARCHAR(20),
    error            TEXT
);

CREATE INDEX idx_site_check_site_time ON site_check (site_id, checked_at DESC);
