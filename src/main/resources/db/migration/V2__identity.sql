-- Identity schema (M2): users, sessions + foreign keys for existing aggregates.

CREATE TABLE users (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    email         text        NOT NULL UNIQUE,
    password_hash text        NOT NULL,
    display_name  text,
    role          text        NOT NULL CHECK (role IN ('BUYER', 'ORGANIZER')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    version       bigint      NOT NULL DEFAULT 0
);

CREATE TABLE sessions (
    id         uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users (id),
    token_hash text        NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_sessions_user    ON sessions (user_id);
CREATE INDEX idx_sessions_expires ON sessions (expires_at);

ALTER TABLE launches
    ADD CONSTRAINT fk_launches_seller FOREIGN KEY (seller_id) REFERENCES users (id);

ALTER TABLE reservations
    ADD CONSTRAINT fk_reservations_buyer FOREIGN KEY (buyer_id) REFERENCES users (id);

ALTER TABLE orders
    ADD CONSTRAINT fk_orders_buyer FOREIGN KEY (buyer_id) REFERENCES users (id);
