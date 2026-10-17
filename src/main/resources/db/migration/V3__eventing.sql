-- Eventing schema (M3): transactional outbox + proyecciones/consumidores.

CREATE TABLE outbox (
    id           uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id uuid        NOT NULL,
    event_type   text        NOT NULL,
    payload      text        NOT NULL,
    occurred_at  timestamptz NOT NULL,
    published_at timestamptz,
    attempts     integer     NOT NULL DEFAULT 0,
    created_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_outbox_unpublished ON outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    consumer     text        NOT NULL,
    event_id     uuid        NOT NULL,
    processed_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer, event_id)
);

CREATE TABLE sales_projection (
    launch_id       uuid        PRIMARY KEY,
    confirmed_units bigint      NOT NULL DEFAULT 0,
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE audit_log (
    id           bigserial   PRIMARY KEY,
    event_id     uuid        NOT NULL,
    event_type   text        NOT NULL,
    aggregate_id uuid        NOT NULL,
    occurred_at  timestamptz NOT NULL,
    recorded_at  timestamptz NOT NULL DEFAULT now(),
    payload      text        NOT NULL
);

CREATE TABLE notifications (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    buyer_id   uuid,
    type       text        NOT NULL,
    message    text        NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
