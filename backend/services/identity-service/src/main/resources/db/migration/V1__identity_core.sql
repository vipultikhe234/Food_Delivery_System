-- identity_db core tables for registration, login and refresh (REQ-AUTH-001; docs/06-database-design.md §2.1).
-- The init script creates citext as superuser; IF NOT EXISTS keeps this runnable by the owner role.
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id                 uuid         NOT NULL,
    phone              varchar(16),
    email              citext,
    password_hash      varchar(255),
    status             varchar(32)  NOT NULL,
    phone_verified_at  timestamptz,
    email_verified_at  timestamptz,
    failed_login_count int          NOT NULL DEFAULT 0,
    locked_until       timestamptz,
    last_login_at      timestamptz,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         uuid,
    updated_by         uuid,
    version            bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT ck_users_status
        CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'LOCKED', 'BLOCKED', 'DELETED')),
    CONSTRAINT ck_users_identifier CHECK (phone IS NOT NULL OR email IS NOT NULL)
);
CREATE UNIQUE INDEX uq_users_phone ON users (phone) WHERE phone IS NOT NULL;
CREATE UNIQUE INDEX uq_users_email ON users (email) WHERE email IS NOT NULL;

CREATE TABLE roles (
    id          uuid         NOT NULL,
    code        varchar(64)  NOT NULL,
    description varchar(255),
    system      boolean      NOT NULL DEFAULT false,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code)
);

CREATE TABLE permissions (
    id          uuid         NOT NULL,
    code        varchar(64)  NOT NULL,
    description varchar(255),
    category    varchar(64),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    created_by  uuid,
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uq_permissions_code UNIQUE (code)
);

CREATE TABLE role_permissions (
    role_id       uuid NOT NULL,
    permission_id uuid NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES permissions (id)
);

CREATE TABLE user_roles (
    id         uuid         NOT NULL,
    user_id    uuid         NOT NULL,
    role_id    uuid         NOT NULL,
    scope_type varchar(16)  NOT NULL,
    scope_id   uuid,
    granted_by uuid,
    created_at timestamptz  NOT NULL DEFAULT now(),
    updated_at timestamptz  NOT NULL DEFAULT now(),
    created_by uuid,
    updated_by uuid,
    version    bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_roles PRIMARY KEY (id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT ck_user_roles_scope_type CHECK (scope_type IN ('GLOBAL', 'RESTAURANT', 'BRANCH')),
    CONSTRAINT ck_user_roles_scope_id CHECK ((scope_type = 'GLOBAL') = (scope_id IS NULL))
);
CREATE UNIQUE INDEX uq_user_roles
    ON user_roles (user_id, role_id, scope_type, coalesce(scope_id, '00000000-0000-0000-0000-000000000000'::uuid));
CREATE INDEX ix_user_roles_user ON user_roles (user_id);

-- Only SHA-256 hashes of refresh tokens are stored. family_id groups one rotation chain; reuse of a
-- rotated token revokes the whole family (REQ-AUTH-001 AC4).
CREATE TABLE refresh_tokens (
    id            uuid         NOT NULL,
    user_id       uuid         NOT NULL,
    family_id     uuid         NOT NULL,
    token_hash    char(64)     NOT NULL,
    session_id    uuid         NOT NULL,
    device_info   varchar(255),
    ip            inet,
    issued_at     timestamptz  NOT NULL,
    expires_at    timestamptz  NOT NULL,
    rotated_at    timestamptz,
    revoked_at    timestamptz,
    revoke_reason varchar(32),
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_refresh_tokens_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_refresh_tokens_revoke_reason CHECK (revoke_reason IN
        ('LOGOUT', 'LOGOUT_ALL', 'REUSE_DETECTED', 'PASSWORD_RESET', 'BLOCKED', 'ADMIN'))
);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id) WHERE revoked_at IS NULL;

-- Public halves of the JWT signing keys, served by the JWKS endpoint. Private keys live in the
-- secret manager and never in the database (docs/09-security.md §2.3).
CREATE TABLE signing_keys (
    kid          varchar(64)  NOT NULL,
    public_jwk   jsonb        NOT NULL,
    status       varchar(16)  NOT NULL,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    retire_after timestamptz,
    CONSTRAINT pk_signing_keys PRIMARY KEY (kid),
    CONSTRAINT ck_signing_keys_status CHECK (status IN ('ACTIVE', 'RETIRING', 'RETIRED'))
);

-- Role assigned at self-registration. The remaining roles and the permission matrix arrive with
-- REQ-AUTH-003.
INSERT INTO roles (id, code, description, system)
VALUES ('01920000-0000-7000-8000-000000000001', 'CUSTOMER', 'Customer of the ordering apps', true);
