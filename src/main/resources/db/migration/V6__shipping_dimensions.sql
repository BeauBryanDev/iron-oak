-- Shipping cost is computed from weight and volume (see ShippingCalculator). Both are
-- nullable for now: fill in the real values per product and machine in the admin panel.
-- Until a row has them, the calculator treats the missing value as zero (see
-- FAIL_ON_MISSING_DIMENSIONS there) so only the distance and base fee are charged for it.
ALTER TABLE product
    ADD COLUMN weight_kg  NUMERIC(8,3),
    ADD COLUMN volume_m3  NUMERIC(8,4),
    ADD CONSTRAINT chk_product_shipping_dimensions
        CHECK ((weight_kg IS NULL OR weight_kg > 0) AND (volume_m3 IS NULL OR volume_m3 > 0));

ALTER TABLE milling_machine
    ADD COLUMN weight_kg  NUMERIC(9,3),
    ADD COLUMN volume_m3  NUMERIC(8,4),
    ADD CONSTRAINT chk_machine_shipping_dimensions
        CHECK ((weight_kg IS NULL OR weight_kg > 0) AND (volume_m3 IS NULL OR volume_m3 > 0));
