-- V12: staff management. is_active = false disables an account (login refused, sessions
-- revoked, tokens rejected); accounts are never deleted so the audit trail keeps its actor.
-- must_change_password is set for the bootstrap admin, new staff and password resets: until
-- the person picks their own password the account can only change it, read /me or log out.

ALTER TABLE admin_user
    ADD COLUMN is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
