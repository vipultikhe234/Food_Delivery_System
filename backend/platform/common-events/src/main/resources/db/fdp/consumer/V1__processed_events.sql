-- Idempotent consumers (REQ-PLAT-005 AC2, docs/06 §1.3, docs/08 §4).
CREATE TABLE processed_events (
    event_id     uuid         NOT NULL,
    consumer     varchar(128) NOT NULL,
    processed_at timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT pk_processed_events PRIMARY KEY (event_id, consumer)
);

CREATE INDEX ix_processed_events_processed_at ON processed_events (processed_at);
