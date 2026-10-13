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

------------

The finding that shapes the backend

  The confusion matrix covers the 40 validation images: the column totals 6, 4,
  7, 10, 11 and 2 match the validation counts. It shows:
  - The bottom "background" row is empty, so the model never misses a tool.
    That's the recall of 1.0.
  - But it often draws a second box with a neighbouring class on the same tool,
    most often fishtail 1 mm alongside ball nose 1 mm. Those extra boxes fill
    the right-hand "background" column.

  mAP is scored per class, so it rewards the correct box and doesn't penalise
  the extra one much. The shop needs a single answer per photo. So in the
  backend I'd:
  1. Merge overlapping boxes regardless of class (class-agnostic NMS).
  2. Keep the highest-confidence box.
    names, and the input size from the file.
  - Preprocessing: letterbox to 512 × 512 (keep the aspect ratio, pad with
    gray 114), RGB, 0–1, NCHW. Ultralytics does the same at training and
    inference.
  - Decoding: [10, 5376] → box (cx, cy, w, h) + 6 class scores per candidate,
    then a confidence threshold (configurable, default 0.25), then
    class-agnostic NMS (IoU 0.45), then the top box → vision_label →
    EndMillService.byVisionLabel → the end mill to sell.
  - Endpoint: POST /api/vision/end-mills (multipart image), public and
    rate-limited like /api/vision/classify. It returns the detected class,
    confidence and box, plus the matching end mill with price and stock. Piper
    can call the same service in-process.
  - Test: one plain unit test with a real end-mill photo from your dataset, no
    Spring needed.



----------------

The chat with Piper is built: ChatOrchestrationService, ChatController and the
  supporting pieces, on your existing chat_session and chat_message tables.
  There's no schema change. It compiles, the new unit test passes 4 of 4, and
  VisionPipelineTest (2 of 2) shows the app still starts with the new beans. I
  haven't sent a real message to OpenAI, since that needs the app running; the
  steps to try it are below.

  How it works

  ┌───────────────────────────┬─────────────────────────────────────────────┐
  │         Endpoint          │                What it does                 │
  ├───────────────────────────┼─────────────────────────────────────────────┤
  │ POST /api/chat/sessions   │ Starts a chat and returns sessionId,        │
  │                           │ sessionToken and startedAt                  │
  ├───────────────────────────┼─────────────────────────────────────────────┤
  │ GET /api/chat/messages    │ The conversation so far, oldest first (to   │
  │                           │ redraw the chat after a page reload)        │
  ├───────────────────────────┼─────────────────────────────────────────────┤
  │ POST /api/chat/messages   │ One message to Piper; returns your stored   │
  │ {content}                 │ message and Piper's reply                   │
  └───────────────────────────┴─────────────────────────────────────────────┘

  The browser sends the token in an X-Chat-Session header, not in the URL, so it
  doesn't end up in Apache's access logs.

  Why a signed token: chat_session.id is a plain counter (1, 2, 3…) on a public
  route, so anyone could read other visitors' chats by trying ids. The token is
  id.signature, an HMAC-SHA256 made with your existing JWT_SECRET_KEY. A guessed
  or edited token comes back as "not found". No new column was needed, so V13
  stays the last migration.

  Each message, in three steps:
  1. A quick database read: check the token, check the limit, load the last 20
     messages.
  2. Piper answers through Spring AI and OpenAI, with no database transaction
     open, so a slow model never ties up a connection.
  3. One write saves your message and Piper's answer together. If OpenAI fails,
     nothing is saved; the visitor gets a 503 "Piper is not available right now"
     and can send the same message again.

  Limits:
  - 2,000 characters per message.
  - 100 messages per chat; after that the visitor starts a new chat.
  - 30 POSTs per minute per IP, through your existing rate limiter.

  Together these keep the OpenAI cost per visitor under control.

  Files

  - New:
    - security/ChatSessionTokens.java
    - agent/PiperAssistant.java (the one call to the model)
    - exceptions/AssistantUnavailableException.java
    - dto/response/ChatSessionResponse.java and ChatReplyResponse.java
    - ChatOrchestrationServiceTest.java
  - Filled in (previously only a package line):
    - ChatOrchestrationService.java
    - ChatController.java
    - ChatMessageRepository.java
    - ChatMessageRequest.java, ChatMessageResponse.java
    - agent/PiperAgentConfig.java: the ChatClient with Piper's system prompt.
      Tomorrow's tools plug in here.
  - Small edits:
    - ChatMessage.java: two getters
    - GlobalExceptionHandler: the 503
    - CorsConfig: the X-Chat-Session header
    - CLAUDE.md

  Two decisions for you

  1. Piper_Sys_Prompt.md is empty. Until you write it, Piper uses a short
     built-in prompt: Iron & Oak assistant in Bogotá, answers in the customer's
     language, prices in USD, never invents prices or stock. Write your own in
     that file and restart; it's picked up automatically.
  2. Chats aren't linked to a customer (customer_id stays null). They could be
     linked later, for example when Piper creates an order with the customer's
     email.

  Try it (backend running)

  TOKEN=$(curl -s -X POST localhost:8080/api/chat/sessions | python3 -c "import 
  sys,json;print(json.load(sys.stdin)['sessionToken'])")
  curl -s -X POST localhost:8080/api/chat/messages -H "X-Chat-Session: $TOKEN" \
    -H "Content-Type: application/json" -d '{"content":"Hola Piper, ¿qué fresas 
  venden?"}'
  curl -s localhost:8080/api/chat/messages -H "X-Chat-Session: $TOKEN"
  Today Piper answers from the model and its system prompt only, with no tools
  yet. So it can talk about end mills in general, but won't know your catalog
  until the knowledge-search and catalog tools are added.

  Next: the Piper tools (KnowledgeSearchTool over Milvus, the two vision models,
  machine health, catalog, orders), then wiring the storefront PiperChat to
  these three endpoints.


### isuses with Zills

This is now a Zilliz/Milvus permission issue—not Piper or OpenAI.

  The token is connecting successfully, but it lacks Search permission for:

  - Database: default
  - Collection: piper_vector_store

  Fix it in Zilliz Cloud:

  1. Open the cluster security/roles settings.
  2. Grant the backend token read permissions, including Search and Query, for
     piper_vector_store.

  3. Ensure the collection is actually in the default database.
  4. If it uses another database, configure:

  spring:
    ai:
      vectorstore:
        milvus:
          database-name: ${MILVUS_DATABASE:default}

  Then restart the backend:

  ./mvnw clean spring-boot:run

  The Java namespace filter was also corrected to use:

  metadata["namespace"]

  So after granting permission and restarting, Piper should be able to retrieve the RAG
  passages.

  References: Zilliz cluster roles, Milvus privileges.



