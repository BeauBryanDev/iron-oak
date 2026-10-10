-- V10: quote requests for QUOTE-priced services (e.g. SPINDLE_TOOLING_SERVICE).
-- A customer describes the job; staff either price it, which creates a normal
-- PENDING_PAYMENT order the customer pays by order number, or decline it.

CREATE TABLE service_quote (
    id                   BIGSERIAL PRIMARY KEY,
    service_offering_id  BIGINT NOT NULL REFERENCES service_offering(id),
    customer_id          BIGINT REFERENCES customer(id),
    customer_name        VARCHAR(200) NOT NULL,
    customer_email       VARCHAR(200) NOT NULL,
    customer_phone       VARCHAR(30),
    country              VARCHAR(2) NOT NULL,
    city                 VARCHAR(100),
    description          TEXT NOT NULL,
    status               VARCHAR(20) NOT NULL DEFAULT 'REQUESTED',
    quoted_price         NUMERIC(10, 2),
    staff_note           TEXT,
    order_id             BIGINT UNIQUE REFERENCES customer_order(id),
    quoted_at            TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_service_quote_status CHECK (status IN ('REQUESTED', 'QUOTED', 'DECLINED')),
    -- a priced quote always has its price, time and order; the others have none
    CONSTRAINT chk_service_quote_priced CHECK (
        (status = 'QUOTED' AND quoted_price > 0 AND quoted_at IS NOT NULL AND order_id IS NOT NULL)
        OR (status <> 'QUOTED' AND order_id IS NULL)
    ),
    CONSTRAINT chk_service_quote_declined CHECK (status <> 'DECLINED' OR staff_note IS NOT NULL)
);

CREATE INDEX idx_service_quote_status_created ON service_quote (status, created_at DESC);
CREATE INDEX idx_service_quote_email ON service_quote (lower(customer_email));
