CREATE TABLE widgets (
    id         uuid          NOT NULL,
    name       varchar(100)  NOT NULL,
    amount     numeric(12,2) NOT NULL,
    currency   char(3)       NOT NULL,
    created_at timestamptz   NOT NULL,
    updated_at timestamptz   NOT NULL,
    created_by uuid          NULL,
    updated_by uuid          NULL,
    version    bigint        NOT NULL,
    CONSTRAINT pk_widgets PRIMARY KEY (id)
);
