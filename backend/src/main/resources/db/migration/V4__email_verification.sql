-- An account may only receive alert emails once its address is confirmed.
ALTER TABLE app_user ADD COLUMN email_verified_at TIMESTAMPTZ;

-- Accounts that already exist were created before verification existed: they count as verified.
UPDATE app_user SET email_verified_at = created_at;

-- One pending link per user at most. Only the SHA-256 of the token is stored, so a leaked database
-- does not hand out working links.
CREATE TABLE email_verification (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX email_verification_user_idx ON email_verification (user_id);
CREATE INDEX email_verification_expires_idx ON email_verification (expires_at);
