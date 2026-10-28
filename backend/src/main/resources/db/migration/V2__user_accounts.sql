-- Visitors, users and admins; see the add-user-accounts design.

ALTER TABLE users DROP CONSTRAINT users_role_check;
UPDATE users SET role = 'USER' WHERE role = 'STUDENT';
ALTER TABLE users ADD CONSTRAINT users_role_check CHECK (role IN ('USER', 'ADMIN'));

ALTER TABLE users
    -- Sessions remember the epoch they were created in; incrementing it ends all of the account's sessions.
    ADD COLUMN session_epoch        integer     NOT NULL DEFAULT 0,
    -- Set by an admin password reset; the account can only change its password until it is cleared.
    ADD COLUMN must_change_password boolean     NOT NULL DEFAULT false,
    ADD COLUMN last_login_at        timestamptz;

-- Admin actions on accounts. Temporary passwords are never recorded.
CREATE TABLE audit_log (
    id             bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id       bigint      NOT NULL REFERENCES users (id),
    action         varchar(32) NOT NULL CHECK (action IN ('ROLE_CHANGED', 'DISABLED', 'ENABLED', 'PASSWORD_RESET')),
    target_user_id bigint      NOT NULL REFERENCES users (id),
    -- For ROLE_CHANGED: {"from": "USER", "to": "ADMIN"}.
    details        jsonb,
    created_at     timestamptz NOT NULL DEFAULT now()
);
