-- Per-user alert channels.
ALTER TABLE app_user
    ADD COLUMN email_alerts     BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN telegram_chat_id VARCHAR(32);

-- Alert state machine per site, so a down/recovery/SSL alert is sent once.
ALTER TABLE site
    ADD COLUMN status               VARCHAR(10) NOT NULL DEFAULT 'UNKNOWN'
        CHECK (status IN ('UNKNOWN', 'UP', 'DOWN')),
    ADD COLUMN consecutive_failures INT NOT NULL DEFAULT 0,
    ADD COLUMN down_since           TIMESTAMPTZ,
    ADD COLUMN ssl_alerted_expiry   TIMESTAMPTZ;
