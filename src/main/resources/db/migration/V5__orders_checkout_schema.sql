
-- Iron & Oak: web checkout, order snapshots and Stripe payments.
-- CUSTOMER ORDER / ORDER HEADER

ALTER TABLE customer_order
    RENAME COLUMN status TO order_status;

ALTER TABLE customer_order
    RENAME COLUMN total_amount TO grand_total;

ALTER TABLE customer_order
    ALTER COLUMN grand_total TYPE NUMERIC(12,2);

ALTER TABLE customer_order
    ADD COLUMN order_number VARCHAR(40),
    ADD COLUMN customer_name VARCHAR(200),
    ADD COLUMN customer_email VARCHAR(254),
    ADD COLUMN phone_number VARCHAR(30),
    ADD COLUMN country VARCHAR(2),
    ADD COLUMN province VARCHAR(100),
    ADD COLUMN city VARCHAR(100),
    ADD COLUMN shipping_address TEXT,
    ADD COLUMN subtotal NUMERIC(12,2),
    ADD COLUMN shipping_cost NUMERIC(12,2)
        NOT NULL DEFAULT 0,
    ADD COLUMN taxes NUMERIC(12,2)
        NOT NULL DEFAULT 0,
    ADD COLUMN currency VARCHAR(3)
        NOT NULL DEFAULT 'USD',
    ADD COLUMN updated_at TIMESTAMPTZ,
    -- TRUE while the order holds product stock taken from inventory and not yet returned
    -- (set at checkout, cleared when the order is cancelled or expires).
    ADD COLUMN stock_reserved BOOLEAN
        NOT NULL DEFAULT FALSE,
    -- A PENDING_PAYMENT order holds its stock only until this moment.
    ADD COLUMN reservation_expires_at TIMESTAMPTZ;

-- Backfill legacy orders without discarding their totals.
UPDATE customer_order
SET
    order_number =
        'IO-' ||
        TO_CHAR(created_at AT TIME ZONE 'UTC', 'YYYYMMDD') ||
        '-' ||
        LPAD(id::TEXT, GREATEST(6, LENGTH(id::TEXT)), '0'),
    subtotal = grand_total,
    updated_at = created_at,
    -- legacy orders took stock at checkout unless they were cancelled (which restocked)
    stock_reserved = (order_status IN ('DRAFT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED'));

-- Legacy AGENT_CHAT rows came from the public checkout, which had no web value until V4.
UPDATE customer_order
SET channel = 'WEB_CHECKOUT'
WHERE channel = 'AGENT_CHAT';

ALTER TABLE customer_order
    ALTER COLUMN channel SET DEFAULT 'WEB_CHECKOUT',
    ALTER COLUMN order_status SET DEFAULT 'PENDING_PAYMENT';

ALTER TABLE customer_order
    ALTER COLUMN order_number SET NOT NULL,
    ALTER COLUMN subtotal SET NOT NULL,
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE customer_order
    ADD CONSTRAINT uq_customer_order_number
        UNIQUE (order_number),
    ADD CONSTRAINT chk_customer_order_amounts
        CHECK (
            subtotal >= 0
            AND shipping_cost >= 0
            AND taxes >= 0
            AND grand_total >= 0
            AND grand_total = subtotal + shipping_cost + taxes
        ),
    ADD CONSTRAINT chk_customer_order_currency
        CHECK (currency ~ '^[A-Z]{3}$'),
    -- an unpaid order must say when its reserved stock is released
    ADD CONSTRAINT chk_customer_order_reservation
        CHECK (
            order_status <> 'PENDING_PAYMENT'
            OR reservation_expires_at IS NOT NULL
        );

-- idx_customer_order_customer and idx_customer_order_status_created already exist from V3
-- (PostgreSQL keeps the status index when the column is renamed).

CREATE INDEX idx_customer_order_channel_created
    ON customer_order(channel, created_at DESC);

-- customers read an order back with the email it was placed under
CREATE INDEX idx_customer_order_email
    ON customer_order(lower(customer_email));

-- the expiry job scans only unpaid orders
CREATE INDEX idx_customer_order_pending_expiry
    ON customer_order(reservation_expires_at)
    WHERE order_status = 'PENDING_PAYMENT';


--  
-- ORDER ITEMS / HISTORICAL PRODUCT SNAPSHOTS
-- 

ALTER TABLE order_item
    RENAME COLUMN unit_price TO price;

ALTER TABLE order_item
    ALTER COLUMN price TYPE NUMERIC(12,2),
    ALTER COLUMN subtotal TYPE NUMERIC(12,2);

-- Every line keeps the name and code the item had when it was bought, for all three item
-- types, so editing the catalog later never rewrites order history. item_code is the product
-- SKU, the machine model code or the service code.
ALTER TABLE order_item
    ADD COLUMN item_name VARCHAR(200),
    ADD COLUMN item_code VARCHAR(50);

UPDATE order_item oi
SET item_name = p.name, item_code = p.sku
FROM product p
WHERE oi.product_id = p.id;

UPDATE order_item oi
SET item_name = m.name, item_code = m.model_code
FROM milling_machine m
WHERE oi.milling_machine_id = m.id;

UPDATE order_item oi
SET item_name = so.name, item_code = so.code
FROM service_offering so
WHERE oi.service_offering_id = so.id;

ALTER TABLE order_item
    ALTER COLUMN item_name SET NOT NULL,
    ALTER COLUMN item_code SET NOT NULL;

ALTER TABLE order_item
    ADD CONSTRAINT chk_order_item_price
        CHECK (price >= 0 AND subtotal >= 0),
    ADD CONSTRAINT chk_order_item_subtotal
        CHECK (subtotal = price * quantity);

-- Keep the existing order_id foreign key, item_type,
-- product/service/machine references and composite UNIQUE
-- constraint used by warranty_claim.

-- 
-- PAYMENT
-- 
ALTER TABLE payment
    ALTER COLUMN amount TYPE NUMERIC(12,2);

ALTER TABLE payment
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    ADD COLUMN checkout_session_id VARCHAR(255),
    ADD COLUMN payment_intent_id VARCHAR(255),
    ADD COLUMN idempotency_key VARCHAR(100),
    ADD COLUMN failure_code VARCHAR(100),
    ADD COLUMN failure_message TEXT,
    ADD COLUMN updated_at TIMESTAMPTZ;

UPDATE payment
SET updated_at = created_at;

ALTER TABLE payment
    ALTER COLUMN updated_at SET DEFAULT now(),
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE payment
    ADD CONSTRAINT chk_payment_currency
        CHECK (currency ~ '^[A-Z]{3}$');

CREATE UNIQUE INDEX uq_payment_checkout_session
    ON payment(checkout_session_id)
    WHERE checkout_session_id IS NOT NULL;

CREATE UNIQUE INDEX uq_payment_intent
    ON payment(payment_intent_id)
    WHERE payment_intent_id IS NOT NULL;

CREATE UNIQUE INDEX uq_payment_idempotency_key
    ON payment(idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_payment_order_created
    ON payment(order_id, created_at DESC);


-- 
-- STRIPE WEBHOOK EVENT LEDGER
-- 

CREATE TABLE payment_webhook_event (
    id                  BIGSERIAL PRIMARY KEY,
    provider_event_id   VARCHAR(255) NOT NULL UNIQUE,
    provider            VARCHAR(50) NOT NULL DEFAULT 'stripe',
    event_type          VARCHAR(150) NOT NULL,
    object_id           VARCHAR(255),
    order_id            BIGINT REFERENCES customer_order(id),
    payment_id          BIGINT REFERENCES payment(id),
    processing_status   VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    attempts            INTEGER NOT NULL DEFAULT 0,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at        TIMESTAMPTZ,
    last_error          TEXT,

    CONSTRAINT chk_webhook_processing_status
        CHECK (
            processing_status IN (
                'RECEIVED',
                'PROCESSING',
                'PROCESSED',
                'FAILED'
            )
        ),

    CONSTRAINT chk_webhook_attempts
        CHECK (attempts >= 0)
);

CREATE INDEX idx_webhook_event_order
    ON payment_webhook_event(order_id);

CREATE INDEX idx_webhook_event_processing
    ON payment_webhook_event(processing_status, received_at);

