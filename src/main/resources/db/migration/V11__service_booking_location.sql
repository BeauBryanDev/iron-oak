-- V11: where a technician visit happens. Services are only offered in Bogota and Medellin,
-- Colombia (ServiceArea); the service checks the city. Nullable because bookings made
-- before V11 only have the free-text location_address.

ALTER TABLE service_booking
    ADD COLUMN country VARCHAR(2),
    ADD COLUMN city    VARCHAR(100);
