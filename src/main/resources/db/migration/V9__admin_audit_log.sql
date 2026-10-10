-- V9: admin audit trail. One row per staff action (login, logout, catalog, stock, customer,
-- order, payment, refund and shipping changes): who, what, on which record, the changed
-- fields before and after, from which IP, and when. Rows are only ever inserted.

CREATE TABLE admin_audit_log (
    id              BIGSERIAL PRIMARY KEY,
    -- null for a failed login with an unknown username, or once the admin row is gone
    admin_user_id   BIGINT REFERENCES admin_user(id) ON DELETE SET NULL,
    -- as typed for logins, so failed attempts against unknown names are visible too
    username        VARCHAR(100) NOT NULL,
    action          VARCHAR(50)  NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     VARCHAR(100),
    -- only the fields that changed; old_value is null on create, new_value null on delete
    old_value       JSONB,
    new_value       JSONB,
    ip_address      VARCHAR(45),
    user_agent      VARCHAR(500),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_admin_audit_created  ON admin_audit_log (created_at DESC);
CREATE INDEX idx_admin_audit_admin    ON admin_audit_log (admin_user_id, created_at DESC);
CREATE INDEX idx_admin_audit_action   ON admin_audit_log (action, created_at DESC);
CREATE INDEX idx_admin_audit_resource ON admin_audit_log (resource_type, resource_id);
