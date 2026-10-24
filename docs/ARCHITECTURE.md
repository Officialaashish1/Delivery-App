# LocalRush — System architecture

```mermaid
flowchart LR
  subgraph Apps["Android apps (Kotlin · Jetpack Compose · MapLibre)"]
    C[Customer app<br/>in.localrush.customer]
    R[Rider app<br/>in.localrush.rider<br/>foreground GPS service]
    P[Partner app<br/>in.localrush.partner]
  end
  A[Admin dashboard<br/>Django templates + JS SPA]
  N[Nginx · TLS · rate limits]
  subgraph Backend["Django backend"]
    API[REST API<br/>gunicorn · DRF · JWT]
    WS[WebSockets<br/>daphne · Django Channels]
    W[Celery workers<br/>dispatch · push · settlements]
    B[Celery beat]
  end
  PG[(PostgreSQL + PostGIS)]
  RD[(Redis<br/>cache · channel layer · broker · live rider positions)]
  OSRM[OSRM<br/>self-hosted routing]
  NOM[Nominatim<br/>self-hosted geocoding]
  TILES[OpenFreeMap tiles<br/>(or self-hosted)]
  RZP[Razorpay]
  FCM[Firebase Cloud Messaging]
  SMS[SMS provider<br/>MSG91 / Twilio]

  C & R & P & A --> N
  N -->|/api/v1| API
  N -->|/ws| WS
  API --> PG & RD
  WS --> RD & PG
  W --> PG & RD
  B --> RD
  API --> OSRM & NOM
  WS --> OSRM
  W --> FCM & SMS
  API <-->|checkout order / verify / webhook| RZP
  C & R & P -.map tiles.-> TILES
```

## Request flows

**Order placement**: app `POST /orders/` → pricing engine (`apps/orders/pricing.py`, single source of truth shared with the cart preview) → `Order` + `OrderItem` snapshot rows inside one transaction with an idempotency key → COD: `PLACED`; online: `PENDING_PAYMENT` + Razorpay order → app runs Razorpay Checkout → `POST /payments/verify/` (HMAC signature + amount check) and/or the webhook (idempotent by event id) → `PLACED`.

**State machine**: every change goes through `apps/orders/services.transition()` which enforces `TRANSITIONS`, timestamps, writes `OrderStatusHistory`, and after commit fans out to WebSocket groups (`order_<id>`, `restaurant_<id>`, `rider_<id>`, `admin_live`) and push notifications (deduplicated by `(user, dedupe_key)`).

**Rider dispatch**: restaurant accepts → Celery `dispatch_order` → PostGIS query for approved, online, fresh-location riders within `rider_search_radius_km` sorted by distance → one `RiderAssignment` offer with a TTL → push + WS to that rider → accept/reject/expire → beat tasks `expire_stale_rider_offers` (15 s) and `retry_unassigned_orders` (30 s) keep the loop going; admins are alerted after repeated failures and can assign manually.

**Live tracking**: rider app foreground service → `POST /rider/location/` (or the `/ws/rider/` socket) every 3–5 s while on delivery → validation (bounds, accuracy, monotonic time, max speed) → Redis latest position (`lr:rider:<id>:loc`) → throttled PostGIS write on the rider profile → sampled history buffer flushed to `RiderLocation` every 60 s → broadcast `rider.location` to the order group → OSRM route recomputed when the rider is off-route (>120 m) or the cached route is older than 45 s; ETA pushed with the route → auto `NEAR_DESTINATION` within 300 m.

**Money**: all amounts are `Decimal`. At placement the order stores commission %, commission amount, restaurant payout and rider payout. On delivery: COD marked collected (rider cash-in-hand), rider earnings ledger rows, restaurant `SettlementLine`. Weekly `RestaurantSettlement` batches (admin marks paid), rider payouts on request, daily `PlatformLedger` roll-up for reports. Refunds go through the provider and adjust settlements.

## Code map
```
backend/
  config/            settings (base/dev/prod/test), asgi (Channels), celery, urls
  apps/core          PlatformSettings, AuditLog, exceptions, permissions, validators, OSRM + Nominatim clients
  apps/accounts      User (phone, roles), OTP, JWT with revocation, addresses, FCM devices
  apps/restaurants   Restaurant, hours, holidays, documents, categories, cuisines, banners, discovery API, partner API
  apps/menu          Menu categories, items, variants, add-ons, partner menu management
  apps/cart          Server-side single-restaurant cart
  apps/coupons       Coupons + usage, validation service
  apps/orders        Pricing engine, Order models, state machine, placement, dispatch, rider workflow, cancellation, tasks
  apps/payments      Razorpay provider, verify, webhooks, refunds, COD collection
  apps/riders        Rider profile/documents, earnings ledger, payouts, incentives, rider API
  apps/tracking      Redis store, WebSocket consumers + JWT middleware, location ingest, route refresh, chat
  apps/notifications In-app notifications, FCM sender, notification copy, campaigns
  apps/reviews       Reviews + rating aggregation
  apps/support       Tickets, messages, FAQs
  apps/finance       Settlements, settlement lines, platform ledger, partner finance API
  apps/dashboard     Admin REST API + web dashboard (templates/dashboard, static/dashboard)
  tests/             pytest suite (42 tests)  ·  scripts/smoke_e2e.py (live-stack E2E)  ·  loadtests/locustfile.py
android/             Gradle workspace: core modules + app-customer / app-rider / app-partner
deploy/              production compose, nginx, OSRM/Nominatim prep, backups, TLS, monitoring
docs/                this folder; docs/api/openapi.yml is generated from the code
```
