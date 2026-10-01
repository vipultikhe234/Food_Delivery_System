-- Idempotency-Key records (REQ-PLAT-006, docs/06 §1.3, docs/07 §5).
-- response_headers keeps the headers a client needs on replay (Location, ETag).
CREATE TABLE idempotency_keys (
    user_id          uuid         NOT NULL,
    idem_key         varchar(128) NOT NULL,
    endpoint         varchar(128) NOT NULL,
    request_hash     char(64)     NOT NULL,
    status           varchar(16)  NOT NULL,
    response_status  int          NULL,
    response_body    jsonb        NULL,
    response_headers jsonb        NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    expires_at       timestamptz  NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (user_id, endpoint, idem_key),
    CONSTRAINT ck_idempotency_keys_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
);

CREATE INDEX ix_idempotency_keys_expires_at ON idempotency_keys (expires_at);
