## Contect Iron Oak Backend to Frotnend

 Done so far (small and easy to undo; nothing touches the backend)
  - Route groups: I moved the storefront pages (page.tsx, about, cart, catalog,
    machines, services) into src/app/(shop)/. URLs are unchanged. This lets
    admin pages have their own layout, without the storefront header and Piper
    chat.
  - API layer (new files):
    - lib/api/types.ts: TypeScript interfaces copied from the Java records.
    - lib/api/http.ts: the fetch wrapper with typed errors.
    - lib/api/catalog.ts and lib/api/orders.ts: the public calls.
  - Env files: frontend/.env.example and .env.local
    (NEXT_PUBLIC_API_URL=http://localhost:8080), plus a frontend/.gitignore.
  - Not yet edited: none of your existing components, and not the root layout.
    Right now the app won't compile, because the moved pages still sit under an
    old layout.

## Plan

  Phase: 1. Foundation
  What: Root layout slimmed to html/body/providers. (shop)/layout.tsx gets
  Header, Footer and Piper (untouched). Cart provider in localStorage. Money
  and slug helpers. Placeholder image, since seeded products have imageUrl:
  null.
  Files: app/layout.tsx, (shop)/layout.tsx, lib/cart/*, lib/format.ts,
  lib/slug.ts
  ────────────────────────────────────────
  Phase: 2. Live catalog
  What: Product card, grid and category sidebar read from the API. Home shows
  featured products. Catalog has search (?q=) and pagination. /catalog/[slug]
  filters by category. /product/[id] is a detail page. Header cart badge and
  search box work.
  Files: ProductCard, ProductGrid, CategorySidebar, CategoryGrid, Header,
  catalog
  and product pages
  ────────────────────────────────────────
  Phase: 3. Machines and services
  What: Both pages switch from hardcoded arrays to the API. Machines can be
  added
  to the cart. Service prices are built from pricing type.
  Files: machines/page.tsx, services/page.tsx
  ────────────────────────────────────────
  Phase: 4. Cart and checkout
  What: Cart page. Checkout form with a live shipping quote. Place the order
  with
  an Idempotency-Key, then redirect to Stripe. Machine orders stay
  payable-later: the shipping quote is "on request".
  Files: cart, checkout, AddToCartButton
  ────────────────────────────────────────
  Phase: 5. After payment
  What: /orders/[orderNumber] status page with a Pay button and expiry time.
  /checkout/success polls until the webhook confirms. /checkout/cancel. /orders
   lookup.
  Files: order pages
  ────────────────────────────────────────
  Phase: 6. Admin auth
  What: Login, silent refresh, logout, change password, forced password change,
  and a guarded /admin dashboard with KPIs.
  Files: lib/auth/*, (admin)/admin/*
  ────────────────────────────────────────
  Phase: 7. Verify
  What: tsc, next build, and a real run against your backend.
  Files: –

---------------------------------------




CREATE TABLE freaser (
    id INTEGER PRIMARY KEY,
    sku VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    flute_type VARCHAR(100) NOT NULL,
    cut_direction VARCHAR(50) DEFAULT 'Right-Hand',
    cutting_diameter_mm DECIMAL(5,2) NOT NULL,
    radius_mm DECIMAL(5,2) NOT NULL,
    material VARCHAR(100) NOT NULL,
    overall_length_mm DECIMAL(7,2) NOT NULL,
    weight_g DECIMAL(7,2),
    price_usd DECIMAL(10,2) NOT NULL,
    max_rpm INTEGER,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO end_mills (sku, name, flute_type, cut_direction, cutting_dia
     meter_mm, radius_mm,
  47                        material, overall_length_mm, weight_g, price, max
     _rpm, stock_quantity,
  48                        vision_label) VALUES
  49 ('FS-R-001', 'Fishtail End Mill 1mm',  'Fishtail',  'Right-Hand', 1.00,
     0.00, 'Solid Carbide', 38.00, 15.00, 27.50, 24000, 25, 'fishtail_end_mil
     l_1mm'),
  50 ('FS-R-002', 'Fishtail End Mill 2mm',  'Fishtail',  'Right-Hand', 2.00,
     0.00, 'Solid Carbide', 38.00, 15.00, 29.50, 24000, 25, 'fishtail_end_mil
     l_2mm'),
  51 ('FS-R-003', 'Fishtail End Mill 3mm',  'Fishtail',  'Right-Hand', 3.00,
     0.00, 'Solid Carbide', 38.00, 15.00, 31.50, 24000, 25, 'fishtail_end_mil
     l_3mm'),
  52 ('BN-001',   'Ball Nose End Mill 1mm', 'Ball Nose', 'Right-Hand', 1.00,
     0.50, 'Solid Carbide', 50.00, 20.00, 33.00, 30000, 25, 'ball_nose_end_mi
     ll_1mm'),
  53 ('BN-002',   'Ball Nose End Mill 2mm', 'Ball Nose', 'Right-Hand', 2.00,
     1.00, 'Solid Carbide', 50.00, 20.00, 35.00, 30000, 25, 'ball_nose_end_mi
     ll_2mm'),
  54 ('BN-003',   'Ball Nose End Mill 3mm', 'Ball Nose', 'Right-Hand', 3.00,
     1.50, 'Solid Carbide', 50.00, 20.00, 37.00, 30000, 25, 'ball_nose_end_mi
     ll_3mm');
  55



