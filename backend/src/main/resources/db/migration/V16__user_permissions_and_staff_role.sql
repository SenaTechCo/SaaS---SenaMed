-- V16: granular per-user permissions (replaces coarse ADMIN/DOCTOR-only authorization).
-- users.role already accepts any string value (VARCHAR, no CHECK constraint - see V1), so no
-- migration is needed to allow the new STAFF role. No backfill is needed either: ADMIN users'
-- permissions are always computed as "all of them" in application code (see User.effectivePermissions),
-- never read from this table.

CREATE TABLE user_permissions (
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission VARCHAR(50) NOT NULL,
    PRIMARY KEY (user_id, permission)
);
