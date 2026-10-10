-- Admin sessions: opaque refresh tokens are stored only as SHA-256 hashes, grouped in a
-- family per login so a reused (stolen) token can revoke the whole session.
CREATE TABLE admin_refresh_token (
    id                  BIGSERIAL PRIMARY KEY,
    admin_user_id       BIGINT NOT NULL REFERENCES admin_user(id) ON DELETE CASCADE,
    family_id           UUID NOT NULL,
    token_hash          VARCHAR(64) NOT NULL UNIQUE,
    issued_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    session_started_at  TIMESTAMPTZ NOT NULL,   -- first login of the family; caps the total session length
    expires_at          TIMESTAMPTZ NOT NULL,
    revoked_at          TIMESTAMPTZ,
    revoked_reason      VARCHAR(30),            -- ROTATED, LOGOUT, LOGOUT_ALL, REUSE_DETECTED, PASSWORD_CHANGED
    user_agent          VARCHAR(255),
    ip_address          VARCHAR(45)
);

CREATE INDEX idx_admin_refresh_token_user ON admin_refresh_token(admin_user_id);
CREATE INDEX idx_admin_refresh_token_family ON admin_refresh_token(family_id);
CREATE INDEX idx_admin_refresh_token_expires ON admin_refresh_token(expires_at);

-- Access tokens issued before this instant are rejected (set when the password changes).
ALTER TABLE admin_user ADD COLUMN password_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();


-- Commercial order lifecycle
ALTER TYPE order_status
    ADD VALUE IF NOT EXISTS 'PENDING_PAYMENT';

ALTER TYPE order_status
    ADD VALUE IF NOT EXISTS 'PAYMENT_FAILED';

ALTER TYPE order_status
    ADD VALUE IF NOT EXISTS 'EXPIRED';

-- Sales channels; retain legacy values for existing data.
ALTER TYPE order_channel
    ADD VALUE IF NOT EXISTS 'WEB_CHECKOUT';

ALTER TYPE order_channel
    ADD VALUE IF NOT EXISTS 'PIPER';

-- Payment lifecycle
ALTER TYPE payment_status
    ADD VALUE IF NOT EXISTS 'PROCESSING';

ALTER TYPE payment_status
    ADD VALUE IF NOT EXISTS 'EXPIRED';

ALTER TYPE payment_status
    ADD VALUE IF NOT EXISTS 'CANCELLED';

ALTER TYPE payment_status
    ADD VALUE IF NOT EXISTS 'REFUNDED';



