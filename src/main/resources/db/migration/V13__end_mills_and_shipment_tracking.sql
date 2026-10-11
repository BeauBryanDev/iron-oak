-- V13 (final schema change for v1):
--   1. end_mills: a fourth sellable item type (milling cutters / fresas), not tied to the
--      MobileNet vision model; vision_label is the class name of the upcoming YOLOv11 model.
--   2. order_item can reference an end mill. item_type becomes VARCHAR + CHECK (like
--      service_quote.status): a new Postgres enum value could not be used in the same
--      migration that adds it, and V13 must be a single migration.
--   3. Shipment tracking on customer_order (carrier, tracking number, status, dates).
-- Index policy (small shared RDS): end_mills gets only its primary key and the vision_label
-- unique index. sku uniqueness is enforced in code (409), not by an index.

-- 1. END MILLS -------------------------------------------------------------------------------

CREATE TABLE end_mills (
    id                  BIGSERIAL PRIMARY KEY,
    sku                 VARCHAR(50)   NOT NULL,
    name                VARCHAR(255)  NOT NULL,
    description         TEXT,
    flute_type          VARCHAR(100)  NOT NULL,
    cut_direction       VARCHAR(50)   NOT NULL DEFAULT 'Right-Hand',
    cutting_diameter_mm NUMERIC(5,2)  NOT NULL,
    radius_mm           NUMERIC(5,2)  NOT NULL,
    material            VARCHAR(100)  NOT NULL,
    overall_length_mm   NUMERIC(7,2)  NOT NULL,
    weight_g            NUMERIC(7,2),
    price               NUMERIC(10,2) NOT NULL,
    max_rpm             INTEGER,
    stock_quantity      INTEGER       NOT NULL DEFAULT 0,
    vision_label        VARCHAR(100),
    image_url           VARCHAR(500),
    is_active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),

    -- one YOLOv11 class points to exactly one end mill (the only extra index on this table)
    CONSTRAINT uq_end_mills_vision_label UNIQUE (vision_label),
    CONSTRAINT chk_end_mills_cut_direction CHECK (cut_direction IN ('Right-Hand', 'Left-Hand')),
    CONSTRAINT chk_end_mills_dimensions CHECK (
        cutting_diameter_mm > 0 AND overall_length_mm > 0
        AND radius_mm >= 0 AND radius_mm <= cutting_diameter_mm / 2),
    CONSTRAINT chk_end_mills_price CHECK (price >= 0),
    CONSTRAINT chk_end_mills_stock CHECK (stock_quantity >= 0),
    CONSTRAINT chk_end_mills_weight CHECK (weight_g IS NULL OR weight_g > 0),
    CONSTRAINT chk_end_mills_max_rpm CHECK (max_rpm IS NULL OR max_rpm > 0)
);

INSERT INTO end_mills (sku, name, flute_type, cut_direction, cutting_diameter_mm, radius_mm,
                       material, overall_length_mm, weight_g, price, max_rpm, stock_quantity,
                       vision_label) VALUES
('FS-R-001', 'Fishtail End Mill 1mm',  'Fishtail',  'Right-Hand', 1.00, 0.00, 'Solid Carbide', 38.00, 15.00, 27.50, 24000, 25, 'fishtail_end_mill_1mm'),
('FS-R-002', 'Fishtail End Mill 2mm',  'Fishtail',  'Right-Hand', 2.00, 0.00, 'Solid Carbide', 38.00, 15.00, 29.50, 24000, 25, 'fishtail_end_mill_2mm'),
('FS-R-003', 'Fishtail End Mill 3mm',  'Fishtail',  'Right-Hand', 3.00, 0.00, 'Solid Carbide', 38.00, 15.00, 31.50, 24000, 25, 'fishtail_end_mill_3mm'),
('BN-001',   'Ball Nose End Mill 1mm', 'Ball Nose', 'Right-Hand', 1.00, 0.50, 'Solid Carbide', 50.00, 20.00, 33.00, 30000, 25, 'ball_nose_end_mill_1mm'),
('BN-002',   'Ball Nose End Mill 2mm', 'Ball Nose', 'Right-Hand', 2.00, 1.00, 'Solid Carbide', 50.00, 20.00, 35.00, 30000, 25, 'ball_nose_end_mill_2mm'),
('BN-003',   'Ball Nose End Mill 3mm', 'Ball Nose', 'Right-Hand', 3.00, 1.50, 'Solid Carbide', 50.00, 20.00, 37.00, 30000, 25, 'ball_nose_end_mill_3mm');

-- 2. ORDER LINES CAN BE END MILLS -----------------------------------------------------------

ALTER TABLE order_item DROP CONSTRAINT chk_item_reference;

ALTER TABLE order_item ALTER COLUMN item_type TYPE VARCHAR(20) USING item_type::text;

DROP TYPE order_item_type;

ALTER TABLE order_item
    ADD COLUMN end_mill_id BIGINT REFERENCES end_mills(id),
    ADD CONSTRAINT chk_item_reference CHECK (
        (item_type = 'PRODUCT' AND product_id IS NOT NULL
            AND service_offering_id IS NULL AND milling_machine_id IS NULL AND end_mill_id IS NULL) OR
        (item_type = 'SERVICE' AND service_offering_id IS NOT NULL
            AND product_id IS NULL AND milling_machine_id IS NULL AND end_mill_id IS NULL) OR
        (item_type = 'MACHINE' AND milling_machine_id IS NOT NULL
            AND product_id IS NULL AND service_offering_id IS NULL AND end_mill_id IS NULL) OR
        (item_type = 'END_MILL' AND end_mill_id IS NOT NULL
            AND product_id IS NULL AND service_offering_id IS NULL AND milling_machine_id IS NULL)
    );

-- 3. SHIPMENT TRACKING ----------------------------------------------------------------------
-- Set by staff once an order ships. The expected delivery date is computed in code from
-- shipped_at and the shipping mode/distance, not stored. delivered_at also starts warranties.

ALTER TABLE customer_order
    ADD COLUMN carrier          VARCHAR(100),
    ADD COLUMN tracking_number  VARCHAR(100),
    ADD COLUMN shipment_status  VARCHAR(20),
    ADD COLUMN shipped_at       TIMESTAMPTZ,
    ADD COLUMN delivered_at     TIMESTAMPTZ,
    ADD CONSTRAINT chk_customer_order_shipment_status
        CHECK (shipment_status IS NULL OR shipment_status IN ('SHIPPED', 'IN_TRANSIT', 'DELIVERED')),
    ADD CONSTRAINT chk_customer_order_shipped
        CHECK (shipment_status IS NULL OR shipped_at IS NOT NULL),
    ADD CONSTRAINT chk_customer_order_delivered
        CHECK (shipment_status IS DISTINCT FROM 'DELIVERED' OR delivered_at IS NOT NULL);
