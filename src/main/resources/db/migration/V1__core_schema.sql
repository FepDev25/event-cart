-- EventCart core schema (M1).
-- Fuente de verdad de stock/reservas/pedidos.
-- Invariante: available_units + reserved_units + confirmed_units = total_stock.

CREATE TABLE launches (
    id                      uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    title                   text          NOT NULL,
    description             text,
    product_name            text          NOT NULL,
    product_ref             text,
    unit_price              numeric(12,2) NOT NULL CHECK (unit_price >= 0),
    currency                varchar(3)    NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    total_stock             integer       NOT NULL CHECK (total_stock >= 0),
    available_units         integer       NOT NULL CHECK (available_units >= 0),
    reserved_units          integer       NOT NULL CHECK (reserved_units >= 0),
    confirmed_units         integer       NOT NULL CHECK (confirmed_units >= 0),
    status                  text          NOT NULL CHECK (status IN ('DRAFT', 'SCHEDULED', 'OPEN', 'CLOSED', 'CANCELLED')),
    opens_at                timestamptz   NOT NULL,
    closes_at               timestamptz   NOT NULL,
    reservation_ttl_seconds integer       NOT NULL DEFAULT 600 CHECK (reservation_ttl_seconds > 0),
    per_buyer_limit         integer       CHECK (per_buyer_limit IS NULL OR per_buyer_limit > 0),
    seller_id               uuid          NOT NULL,
    created_at              timestamptz   NOT NULL DEFAULT now(),
    updated_at              timestamptz   NOT NULL DEFAULT now(),
    version                 bigint        NOT NULL DEFAULT 0,
    CONSTRAINT chk_launches_schedule  CHECK (closes_at > opens_at),
    CONSTRAINT chk_launches_stock_sum CHECK (available_units + reserved_units + confirmed_units = total_stock)
);

CREATE INDEX idx_launches_status_opens ON launches (status, opens_at);

CREATE TABLE reservations (
    id           uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    launch_id    uuid          NOT NULL REFERENCES launches (id),
    buyer_id     uuid          NOT NULL,
    quantity     integer       NOT NULL CHECK (quantity > 0),
    unit_price   numeric(12,2) NOT NULL CHECK (unit_price >= 0),
    currency     varchar(3)    NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    total_amount numeric(14,2) NOT NULL CHECK (total_amount >= 0),
    status       text          NOT NULL CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'EXPIRED')),
    expires_at   timestamptz   NOT NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    version      bigint        NOT NULL DEFAULT 0,
    CONSTRAINT chk_reservations_amount CHECK (total_amount = quantity * unit_price)
);

CREATE INDEX idx_reservations_launch_status ON reservations (launch_id, status);
CREATE INDEX idx_reservations_buyer_launch  ON reservations (buyer_id, launch_id);
CREATE INDEX idx_reservations_expiry        ON reservations (status, expires_at);

CREATE TABLE orders (
    id             uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    reservation_id uuid          NOT NULL UNIQUE REFERENCES reservations (id),
    launch_id      uuid          NOT NULL REFERENCES launches (id),
    buyer_id       uuid          NOT NULL,
    quantity       integer       NOT NULL CHECK (quantity > 0),
    unit_price     numeric(12,2) NOT NULL CHECK (unit_price >= 0),
    currency       varchar(3)    NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    total_amount   numeric(14,2) NOT NULL CHECK (total_amount >= 0),
    status         text          NOT NULL CHECK (status IN ('PENDING', 'CONFIRMED', 'FAILED')),
    payment_result text,
    failure_reason text,
    created_at     timestamptz   NOT NULL DEFAULT now(),
    updated_at     timestamptz   NOT NULL DEFAULT now(),
    version        bigint        NOT NULL DEFAULT 0,
    CONSTRAINT chk_orders_amount CHECK (total_amount = quantity * unit_price)
);

CREATE INDEX idx_orders_launch_status ON orders (launch_id, status);
CREATE INDEX idx_orders_buyer         ON orders (buyer_id);

CREATE TABLE buyer_launch_usage (
    buyer_id        uuid        NOT NULL,
    launch_id       uuid        NOT NULL REFERENCES launches (id),
    reserved_units  integer     NOT NULL DEFAULT 0 CHECK (reserved_units >= 0),
    confirmed_units integer     NOT NULL DEFAULT 0 CHECK (confirmed_units >= 0),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (buyer_id, launch_id)
);

CREATE TABLE idempotency_keys (
    id              uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    scope           text        NOT NULL,
    idempotency_key text        NOT NULL,
    request_hash    text,
    resource_id     uuid,
    response_status integer,
    response_body   text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    CONSTRAINT uq_idempotency_scope_key UNIQUE (scope, idempotency_key)
);

CREATE INDEX idx_idempotency_expires ON idempotency_keys (expires_at);
