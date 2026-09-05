-- Iron & Oak database schema
-- PostgreSQL 16+ with pgvector extension

CREATE EXTENSION IF NOT EXISTS vector;

-- PRODUCT CATALOG (fed by the vision model's 86 classes)

CREATE TABLE tool_category (
    id              BIGSERIAL PRIMARY KEY,
    model_label     VARCHAR(100) NOT NULL UNIQUE,  
    display_name    VARCHAR(150) NOT NULL,      
    synonyms        TEXT[] DEFAULT '{}',
    description     TEXT
);

CREATE TABLE product (
    id                  BIGSERIAL PRIMARY KEY,
    tool_category_id    BIGINT REFERENCES tool_category(id),
    sku                 VARCHAR(50) NOT NULL UNIQUE,
    name                VARCHAR(200) NOT NULL,
    brand               VARCHAR(100),
    description         TEXT,
    price               NUMERIC(10,2) NOT NULL,
    stock_quantity      INTEGER NOT NULL DEFAULT 0,
    warranty_months     INTEGER DEFAULT 0,
    image_url           VARCHAR(500),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_product_tool_category ON product(tool_category_id);

-- SURFACE MATERIALS (sold by area, not by unit - flooring/wall cladding)

CREATE TABLE material_category (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL UNIQUE  -- 'Flooring', 'Wall Cladding'
);

CREATE TABLE surface_material (
    id                  BIGSERIAL PRIMARY KEY,
    material_category_id BIGINT NOT NULL REFERENCES material_category(id),
    name                VARCHAR(200) NOT NULL,
    material_type       VARCHAR(50) NOT NULL,   -- 'Aluminum', 'Wood', 'Plastic'
    price_per_sqm       NUMERIC(10,2) NOT NULL,
    stock_sqm           NUMERIC(10,2) NOT NULL DEFAULT 0,
    description         TEXT,
    image_url           VARCHAR(500),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_surface_material_category ON surface_material(material_category_id);

-- SERVICE CATALOG (labor, unrelated to the vision model)

CREATE TABLE service_category (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL UNIQUE  -- 'Plumbing', 'Electrical', 'Gardening', 'Auto Mechanic', 'DIY / Handyman'
);

CREATE TYPE pricing_type AS ENUM ('FIXED', 'HOURLY');

CREATE TABLE service (
    id                  BIGSERIAL PRIMARY KEY,
    service_category_id BIGINT NOT NULL REFERENCES service_category(id),
    name                VARCHAR(200) NOT NULL,
    description         TEXT,
    pricing_type        pricing_type NOT NULL,
    fixed_price         NUMERIC(10,2),      -- set when pricing_type = FIXED
    hourly_rate         NUMERIC(10,2),      -- set when pricing_type = HOURLY
    estimated_min_hours NUMERIC(4,1),       -- set when pricing_type = HOURLY
    estimated_max_hours NUMERIC(4,1),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_pricing CHECK (
        (pricing_type = 'FIXED'  AND fixed_price IS NOT NULL) OR
        (pricing_type = 'HOURLY' AND hourly_rate IS NOT NULL
            AND estimated_min_hours IS NOT NULL AND estimated_max_hours IS NOT NULL)
    )
);

CREATE INDEX idx_service_category ON service(service_category_id);

-- CUSTOMERS AND ORDERS

-- No login/password here on purpose - "customer" is just contact info
-- captured at checkout (guest identity), never an authenticated account.
-- The MVP does not require buyers to register.
CREATE TABLE customer (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    email       VARCHAR(200),
    phone       VARCHAR(50),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Separate, real authentication - only for the business owner/staff
-- who log in to see the dashboard. Unrelated to the customer table above.
CREATE TABLE admin_user (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    email           VARCHAR(200) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    full_name       VARCHAR(200),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_login_at   TIMESTAMPTZ
);

CREATE TYPE order_status AS ENUM ('DRAFT', 'CONFIRMED', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED');
CREATE TYPE order_item_type AS ENUM ('PRODUCT', 'SERVICE', 'MATERIAL');
CREATE TYPE order_channel AS ENUM ('AGENT_CHAT', 'ADMIN_MANUAL');

CREATE TABLE customer_order (
    id              BIGSERIAL PRIMARY KEY,
    customer_id     BIGINT REFERENCES customer(id),  -- nullable: guest checkout is the default
    status          order_status NOT NULL DEFAULT 'DRAFT',
    channel         order_channel NOT NULL DEFAULT 'AGENT_CHAT',  -- lets the dashboard show what Piper closed on its own
    total_amount    NUMERIC(10,2) NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Single order_item table with a type discriminator, instead of three
-- parallel item tables. Simpler for JPA mapping and for the OrderTool
-- to reason about a mixed cart of parts, labor, and surface materials.
-- quantity is NUMERIC (not INTEGER) so it can represent either whole
-- units (products, services) or fractional square meters (materials).
CREATE TABLE order_item (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    item_type           order_item_type NOT NULL,
    product_id          BIGINT REFERENCES product(id),
    service_id          BIGINT REFERENCES service(id),
    surface_material_id BIGINT REFERENCES surface_material(id),
    quantity            NUMERIC(10,2) NOT NULL DEFAULT 1,  -- units, or m2 for MATERIAL
    estimated_hours     NUMERIC(4,1),           -- filled only for HOURLY service items
    unit_price          NUMERIC(10,2) NOT NULL,
    subtotal            NUMERIC(10,2) NOT NULL,
    CONSTRAINT chk_item_reference CHECK (
        (item_type = 'PRODUCT'  AND product_id IS NOT NULL AND service_id IS NULL AND surface_material_id IS NULL) OR
        (item_type = 'SERVICE'  AND service_id IS NOT NULL AND product_id IS NULL AND surface_material_id IS NULL) OR
        (item_type = 'MATERIAL' AND surface_material_id IS NOT NULL AND product_id IS NULL AND service_id IS NULL)
    )
);

CREATE INDEX idx_order_item_order ON order_item(order_id);

-- RAG: policy document + the two "For Dummies" books

CREATE TABLE document_chunk (
    id          BIGSERIAL PRIMARY KEY,
    source      VARCHAR(100) NOT NULL,   -- 'iron_oak_policies' | 'home_maintenance_dummies' | 'home_wiring_dummies'
    chapter     VARCHAR(300),
    section     VARCHAR(300),
    content     TEXT NOT NULL,           -- chunk text, WITH the chapter/section header prepended
    embedding   VECTOR(1024),            -- dimension depends on the Chosen  Embedding Model
    metadata    JSONB DEFAULT '{}'
);

CREATE INDEX idx_document_chunk_embedding
    ON document_chunk USING hnsw (embedding vector_cosine_ops);

-- CHAT / AGENT SESSION HISTORY

CREATE TYPE message_role AS ENUM ('USER', 'ASSISTANT', 'TOOL');

CREATE TABLE chat_session (
    id          BIGSERIAL PRIMARY KEY,
    customer_id BIGINT REFERENCES customer(id),  -- nullable: anonymous visitors can chat too
    started_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE chat_message (
    id              BIGSERIAL PRIMARY KEY,
    chat_session_id BIGINT NOT NULL REFERENCES chat_session(id) ON DELETE CASCADE,
    role            message_role NOT NULL,
    content         TEXT NOT NULL,
    tool_name       VARCHAR(100),        -- set when role = TOOL
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_message_session ON chat_message(chat_session_id);



