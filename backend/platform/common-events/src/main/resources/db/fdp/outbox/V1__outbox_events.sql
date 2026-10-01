-- Transactional outbox (ADR-005, REQ-PLAT-006 AC1, docs/06 §1.3).
-- payload is the complete event envelope; headers are the Kafka headers to send with it.
CREATE TABLE outbox_events (
    id             uuid         NOT NULL,
    aggregate_type varchar(64)  NOT NULL,
    aggregate_id   uuid         NOT NULL,
    event_type     varchar(128) NOT NULL,
    event_version  int          NOT NULL,
    topic          varchar(128) NOT NULL,
    partition_key  varchar(128) NOT NULL,
    payload        jsonb        NOT NULL,
    headers        jsonb        NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT clock_timestamp(),
    published_at   timestamptz  NULL,
    attempts       int          NOT NULL DEFAULT 0,
    last_error     text         NULL,
    CONSTRAINT pk_outbox_events PRIMARY KEY (id)
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (created_at, id) WHERE published_at IS NULL;
CREATE INDEX ix_outbox_events_published_at ON outbox_events (published_at) WHERE published_at IS NOT NULL;
