CREATE TABLE notes (
    id           uuid        NOT NULL,
    aggregate_id uuid        NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_notes PRIMARY KEY (id)
);
