-- Road distances from the Bogota warehouse, one row per destination city. Quotes read this
-- table and never call Google; a missing city is fetched from the Google Routes API only when a
-- real order is placed (or when staff run a refresh), then kept here. Air destinations (US, MX,
-- CR, PA: no road across the Darien Gap) use fixed prices instead and have no rows.
CREATE TABLE shipping_route (
    id           BIGSERIAL PRIMARY KEY,
    country      VARCHAR(2)   NOT NULL,
    city_key     VARCHAR(100) NOT NULL,      -- lower case, no accents: how lookups match
    city_name    VARCHAR(100) NOT NULL,
    distance_km  NUMERIC(8,1) NOT NULL,
    source       VARCHAR(20)  NOT NULL,      -- GOOGLE or MANUAL (staff)
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_shipping_route UNIQUE (country, city_key),
    CONSTRAINT chk_shipping_route_distance CHECK (distance_km >= 0),
    CONSTRAINT chk_shipping_route_source CHECK (source IN ('GOOGLE', 'MANUAL'))
);

-- How each order's shipping was priced, kept for audits and disputes.
ALTER TABLE customer_order
    ADD COLUMN shipping_status       VARCHAR(20) NOT NULL DEFAULT 'QUOTED',
    ADD COLUMN shipping_mode         VARCHAR(10),
    ADD COLUMN shipping_distance_km  NUMERIC(8,1),
    ADD COLUMN shipping_source       VARCHAR(20),
    ADD CONSTRAINT chk_customer_order_shipping_status
        CHECK (shipping_status IN ('QUOTED', 'ON_REQUEST')),
    ADD CONSTRAINT chk_customer_order_shipping_mode
        CHECK (shipping_mode IS NULL OR shipping_mode IN ('NONE', 'ROAD', 'AIR')),
    ADD CONSTRAINT chk_customer_order_shipping_source
        CHECK (shipping_source IS NULL OR shipping_source IN ('GOOGLE', 'MANUAL', 'ESTIMATE', 'FIXED', 'STAFF'));
