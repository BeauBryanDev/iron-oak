-- Iron & Oak database schema, migration V3
-- Adds the tables behind the Piper tools (see Piper_tools.md) and the indexes for their lookups.

-- CUSTOMER: address and unique email

ALTER TABLE customer ADD COLUMN address TEXT;

-- Case-insensitive uniqueness. Postgres allows several NULL emails; the backend should store emails in lowercase.
CREATE UNIQUE INDEX uq_customer_email ON customer (lower(email));

-- ORDERS: idempotency key for create_order

ALTER TABLE customer_order ADD COLUMN idempotency_key VARCHAR(100) UNIQUE;

-- Lets warranty_claim prove that an order item belongs to the order it names.
ALTER TABLE order_item ADD CONSTRAINT uq_order_item_id_order UNIQUE (id, order_id);

-- ENUMS

CREATE TYPE booking_status AS ENUM ('REQUESTED', 'CONFIRMED', 'COMPLETED', 'CANCELLED');
CREATE TYPE claim_status   AS ENUM ('OPEN', 'IN_REVIEW', 'APPROVED', 'REJECTED', 'RESOLVED');
CREATE TYPE payment_status AS ENUM ('PENDING', 'PAID', 'FAILED');
CREATE TYPE ticket_status  AS ENUM ('OPEN', 'IN_PROGRESS', 'CLOSED');

-- SERVICE BOOKINGS (a scheduled visit for one service_offering; cancel = status CANCELLED, rows are never deleted)

CREATE TABLE service_booking (
    id                  BIGSERIAL PRIMARY KEY,
    customer_id         BIGINT NOT NULL REFERENCES customer(id),
    service_offering_id BIGINT NOT NULL REFERENCES service_offering(id),
    order_id            BIGINT REFERENCES customer_order(id),
    location_address    TEXT NOT NULL,                  -- where the machine is, the technician goes here
    scheduled_at        TIMESTAMPTZ NOT NULL,           -- updated on reschedule
    status              booking_status NOT NULL DEFAULT 'REQUESTED',
    machine_model       VARCHAR(100),
    notes               TEXT,
    cancel_reason       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- WARRANTY CLAIMS

CREATE TABLE warranty_claim (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT NOT NULL REFERENCES customer(id),
    order_id        BIGINT NOT NULL REFERENCES customer_order(id),
    order_item_id   BIGINT NOT NULL,
    description     TEXT NOT NULL,
    status          claim_status NOT NULL DEFAULT 'OPEN',
    resolution_note TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at     TIMESTAMPTZ,
    CONSTRAINT fk_claim_order_item FOREIGN KEY (order_item_id, order_id)
        REFERENCES order_item(id, order_id)
);

-- PAYMENTS (written by the checkout flow, never by Piper)

CREATE TABLE payment (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES customer_order(id),
    amount              NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    status              payment_status NOT NULL DEFAULT 'PENDING',
    provider            VARCHAR(50),
    provider_reference  VARCHAR(100),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    paid_at             TIMESTAMPTZ
);

-- SUPPORT TICKETS (escalate_to_human)

CREATE TABLE support_ticket (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT REFERENCES customer(id),
    customer_email  VARCHAR(200),
    chat_session_id BIGINT REFERENCES chat_session(id),
    reason          VARCHAR(200) NOT NULL,
    summary         TEXT NOT NULL,
    status          ticket_status NOT NULL DEFAULT 'OPEN',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- INDEXES
-- Tool calls are mostly primary-key lookups plus an email check, which need no extra index.
-- These cover the foreign keys, the admin queues and the duplicate guards.

CREATE INDEX idx_customer_order_customer ON customer_order(customer_id);
CREATE INDEX idx_customer_order_status_created ON customer_order(status, created_at DESC);

CREATE INDEX idx_order_item_product ON order_item(product_id) WHERE product_id IS NOT NULL;
CREATE INDEX idx_order_item_service ON order_item(service_offering_id) WHERE service_offering_id IS NOT NULL;
CREATE INDEX idx_order_item_machine ON order_item(milling_machine_id) WHERE milling_machine_id IS NOT NULL;

CREATE INDEX idx_service_booking_customer ON service_booking(customer_id);
CREATE INDEX idx_service_booking_order ON service_booking(order_id) WHERE order_id IS NOT NULL;
CREATE INDEX idx_service_booking_upcoming ON service_booking(scheduled_at) WHERE status IN ('REQUESTED', 'CONFIRMED');

CREATE INDEX idx_warranty_claim_order ON warranty_claim(order_id);
CREATE INDEX idx_warranty_claim_customer ON warranty_claim(customer_id);
CREATE INDEX idx_warranty_claim_queue ON warranty_claim(status, created_at DESC);
-- One active claim per order item
CREATE UNIQUE INDEX uq_warranty_claim_active_item ON warranty_claim(order_item_id) WHERE status IN ('OPEN', 'IN_REVIEW');

CREATE INDEX idx_payment_order ON payment(order_id);
CREATE UNIQUE INDEX uq_payment_provider_ref ON payment(provider, provider_reference) WHERE provider_reference IS NOT NULL;

CREATE INDEX idx_support_ticket_queue ON support_ticket(status, created_at DESC);
CREATE INDEX idx_support_ticket_customer ON support_ticket(customer_id) WHERE customer_id IS NOT NULL;
