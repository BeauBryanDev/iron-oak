-- Customer-facing reference numbers, same idea as customer_order.order_number.
-- The numeric id stays internal; Piper and the customer use the reference number.
-- New rows get theirs from the application (ReferenceNumbers); existing rows are
-- backfilled here with an 8-character hex suffix.

ALTER TABLE service_booking ADD COLUMN booking_number VARCHAR(40);
ALTER TABLE warranty_claim  ADD COLUMN claim_number   VARCHAR(40);
ALTER TABLE support_ticket  ADD COLUMN ticket_number  VARCHAR(40);

UPDATE service_booking
SET booking_number = 'SB-'
    || to_char(created_at, 'YYYYMMDD') || '-'
    || upper(substr(md5(id::text || clock_timestamp()::text), 1, 8));

UPDATE warranty_claim
SET claim_number = 'WC-'
    || to_char(created_at, 'YYYYMMDD') || '-'
    || upper(substr(md5(id::text || clock_timestamp()::text), 1, 8));

UPDATE support_ticket
SET ticket_number = 'ST-'
    || to_char(created_at, 'YYYYMMDD') || '-'
    || upper(substr(md5(id::text || clock_timestamp()::text), 1, 8));

ALTER TABLE service_booking ALTER COLUMN booking_number SET NOT NULL;
ALTER TABLE warranty_claim  ALTER COLUMN claim_number   SET NOT NULL;
ALTER TABLE support_ticket  ALTER COLUMN ticket_number  SET NOT NULL;

ALTER TABLE service_booking
    ADD CONSTRAINT uq_service_booking_number UNIQUE (booking_number);
ALTER TABLE warranty_claim
    ADD CONSTRAINT uq_warranty_claim_number UNIQUE (claim_number);
ALTER TABLE support_ticket
    ADD CONSTRAINT uq_support_ticket_number UNIQUE (ticket_number);
