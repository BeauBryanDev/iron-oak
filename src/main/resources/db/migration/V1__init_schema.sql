-- Iron & Oak database schema
-- PostgreSQL 16+ (plain, no extensions)

-- PRODUCT CATALOG (fed by the vision model's 87 classes)
-- tool_category.model_label is the exact class name from ml/idx_to_class.json.
-- product.vision_name repeats that label on each product so a vision prediction
-- can be matched to products without a join.

CREATE TABLE tool_category (
    id              BIGSERIAL PRIMARY KEY,
    model_label     VARCHAR(100) NOT NULL UNIQUE,
    display_name    VARCHAR(150) NOT NULL,
    synonyms        TEXT[] DEFAULT '{}',
    description     TEXT,
    -- Target for product's composite foreign key (id alone is already unique).
    CONSTRAINT uq_tool_category_id_label UNIQUE (id, model_label)
);

CREATE TABLE product (
    id                  BIGSERIAL PRIMARY KEY,
    tool_category_id    BIGINT NOT NULL,
    sku                 VARCHAR(50) NOT NULL UNIQUE,
    vision_name         VARCHAR(100) NOT NULL,   -- CNN class label, same value as tool_category.model_label
    name                VARCHAR(200) NOT NULL,
    brand               VARCHAR(100),
    category            VARCHAR(50) NOT NULL,    -- storefront category, e.g. 'Hand Tools'
    description         TEXT,
    price               NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    stock_quantity      INTEGER NOT NULL DEFAULT 0 CHECK (stock_quantity >= 0),
    warranty_months     INTEGER NOT NULL DEFAULT 0 CHECK (warranty_months >= 0),
    image_url           VARCHAR(500),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- vision_name can never drift from the label of the category it points to.
    CONSTRAINT fk_product_tool_category FOREIGN KEY (tool_category_id, vision_name)
        REFERENCES tool_category (id, model_label)
);

CREATE INDEX idx_product_tool_category ON product(tool_category_id);
CREATE INDEX idx_product_vision_name ON product(vision_name);
CREATE INDEX idx_product_category ON product(category);

-- MILLING MACHINES (four machine types, sold by model, not tracked by stock)

CREATE TABLE milling_machine (
    id                  BIGSERIAL PRIMARY KEY,
    model_code          VARCHAR(30) NOT NULL UNIQUE,   -- 'BM-200', 'TM-450', 'VMC-650', 'HB-900'
    name                VARCHAR(200) NOT NULL,         -- 'Benchtop Mill'
    description         TEXT,
    power_kw            NUMERIC(5,1) NOT NULL CHECK (power_kw > 0),
    spindle_min_rpm     INTEGER NOT NULL CHECK (spindle_min_rpm > 0),
    spindle_max_rpm     INTEGER NOT NULL,
    table_length_mm     INTEGER NOT NULL CHECK (table_length_mm > 0),
    table_width_mm      INTEGER NOT NULL CHECK (table_width_mm > 0),
    price               NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    warranty_months     INTEGER NOT NULL DEFAULT 12 CHECK (warranty_months >= 0),
    image_url           VARCHAR(500),
    is_active           BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_spindle_range CHECK (spindle_max_rpm >= spindle_min_rpm)
);

-- SERVICE OFFERINGS (technical service on CNC and milling machines, unrelated to the vision model)
-- Named service_offering, not service, to avoid clashing with Spring's @Service stereotype.
-- Prices are starting prices ("From $240 / visit"); price_unit says what the price covers.

CREATE TABLE service_offering_category (
    id      BIGSERIAL PRIMARY KEY,
    name    VARCHAR(100) NOT NULL UNIQUE   -- 'Maintenance & Repair', 'Installation & Training', 'Diagnostics & Audit'
);

CREATE TYPE pricing_type AS ENUM ('FIXED', 'HOURLY', 'QUOTE');

CREATE TABLE service_offering (
    id                          BIGSERIAL PRIMARY KEY,
    service_offering_category_id BIGINT NOT NULL REFERENCES service_offering_category(id),
    code                        VARCHAR(50) NOT NULL UNIQUE,   -- 'PREVENTIVE_MAINTENANCE'
    name                        VARCHAR(200) NOT NULL,
    description                 TEXT,
    pricing_type                pricing_type NOT NULL,
    fixed_price                 NUMERIC(10,2),      -- set when pricing_type = FIXED
    hourly_rate                 NUMERIC(10,2),      -- set when pricing_type = HOURLY
    estimated_min_hours         NUMERIC(4,1),       -- set when pricing_type = HOURLY
    estimated_max_hours         NUMERIC(4,1),
    price_unit                  VARCHAR(30),        -- 'visit', 'hour', 'machine', 'job', 'day'; null for QUOTE
    is_active                   BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT chk_pricing CHECK (
        (pricing_type = 'FIXED'  AND fixed_price IS NOT NULL AND price_unit IS NOT NULL) OR
        (pricing_type = 'HOURLY' AND hourly_rate IS NOT NULL
            AND estimated_min_hours IS NOT NULL AND estimated_max_hours IS NOT NULL
            AND price_unit IS NOT NULL) OR
        (pricing_type = 'QUOTE'  AND fixed_price IS NULL AND hourly_rate IS NULL)
    )
);

CREATE INDEX idx_service_offering_category ON service_offering(service_offering_category_id);

-- CUSTOMERS AND ORDERS

-- No login/password here on purpose - "customer" is just contact info
-- captured at checkout (guest identity), never an authenticated account.
-- The MVP does not require buyers to register.
CREATE TABLE customer (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    email       VARCHAR(200),  -- must be unique at V2 schema 
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
CREATE TYPE order_item_type AS ENUM ('PRODUCT', 'SERVICE', 'MACHINE');
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
-- to reason about a mixed cart of parts, labor, and machines.
-- quantity is a whole number of units (products, machines, service units such as visits or days).
CREATE TABLE order_item (
    id                  BIGSERIAL PRIMARY KEY,
    order_id            BIGINT NOT NULL REFERENCES customer_order(id) ON DELETE CASCADE,
    item_type           order_item_type NOT NULL,
    product_id          BIGINT REFERENCES product(id),
    service_offering_id BIGINT REFERENCES service_offering(id),
    milling_machine_id  BIGINT REFERENCES milling_machine(id),
    quantity            INTEGER NOT NULL DEFAULT 1 CHECK (quantity > 0),
    estimated_hours     NUMERIC(4,1),           -- filled only for HOURLY service items
    unit_price          NUMERIC(10,2) NOT NULL,
    subtotal            NUMERIC(10,2) NOT NULL,
    CONSTRAINT chk_item_reference CHECK (
        (item_type = 'PRODUCT' AND product_id IS NOT NULL
            AND service_offering_id IS NULL AND milling_machine_id IS NULL) OR
        (item_type = 'SERVICE' AND service_offering_id IS NOT NULL
            AND product_id IS NULL AND milling_machine_id IS NULL) OR
        (item_type = 'MACHINE' AND milling_machine_id IS NOT NULL
            AND product_id IS NULL AND service_offering_id IS NULL)
    )
);

CREATE INDEX idx_order_item_order ON order_item(order_id);

-- COMPLAINTS
-- Free-text intake like customer_order's guest identity: customer_name and product
-- are plain text as the customer gave them, not foreign keys.

CREATE TYPE complaint_status AS ENUM ('PENDING', 'RESOLVED', 'REJECTED');

CREATE TABLE complaint (
    id                  BIGSERIAL PRIMARY KEY,
    customer_name       VARCHAR(200) NOT NULL,
    complaint_datetime  TIMESTAMPTZ NOT NULL,          -- when the incident happened, as reported
    product             VARCHAR(200) NOT NULL,
    description         TEXT NOT NULL,
    status              complaint_status NOT NULL DEFAULT 'PENDING',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_complaint_status ON complaint(status);

-- CHAT / AGENT SESSION HISTORY
-- The RAG vector store lives outside PostgreSQL (Milvus or Elasticsearch).

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
