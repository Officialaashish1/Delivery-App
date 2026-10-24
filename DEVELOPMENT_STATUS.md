# LocalRush — Development status

_Last updated: 2026-10-24_

| Phase | Scope | Status |
|---|---|---|
| 1 | Architecture, DB schema (PostGIS), Django project, auth (OTP + JWT + revocation), roles & permissions | ✅ Done — `backend/apps/{core,accounts}`, migrations, `docs/ARCHITECTURE.md`, `docs/ER_DIAGRAM.md` |
| 2 | Restaurant onboarding, partner dashboard API, menu management, categories | ✅ Done (backend) — `apps/{restaurants,menu,finance}`; partner Android app done |
| 3 | Customer app: home, search, restaurant details, cart, checkout | ✅ Backend done (`apps/{restaurants,cart,coupons}`); Android app done |
| 4 | Order processing, Razorpay payment integration, order state machine | ✅ Done — `apps/{orders,payments}`, webhook idempotency, refunds, invoices |
| 5 | Rider app: registration, availability, order assignment | ✅ Backend done (`apps/riders`, dispatch + offers + expiry/retry tasks); Android app done |
| 6 | MapLibre integration, OSRM routing, real-time GPS tracking | ✅ Backend done (`apps/tracking`, `core/services/routing.py`, Redis hot store, anti-spoof validation, route refresh); Android `core:maps`/`core:location` done |
| 7 | Customer live tracking screen, rider navigation, WebSocket integration | ✅ Backend done (Channels consumers, JWT WS auth, chat, snapshots); Android screens done |
| 8 | Admin dashboard, reports, commission & settlements | ✅ Done — `apps/dashboard` API + web SPA (`/dashboard/`), settlements, payouts, ledger, reports |
| 9 | Notifications (FCM), support, reviews, promotions | ✅ Backend done — `apps/{notifications,support,reviews}`, coupons, banners, campaigns |
| 10 | Security audit, tests, Docker deployment, performance, release prep | ✅ Backend: 54 pytest tests (incl. 11 security regression tests), live E2E smoke, Locust load test, ruff clean, prod compose + nginx + TLS + backups + monitoring, `docs/SECURITY.md`, `docs/PLAYSTORE_RELEASE.md`. Android: 123 unit tests + 19 Paparazzi screenshot renders, debug APKs built, minified R8 release APKs build successfully (unsigned until a keystore is configured: ~45 MB each) |

## Backend verification log
- `docker compose exec api pytest` → **54 passed** (auth, cart/pricing, lifecycle, payments/webhooks, tracking/WebSockets, admin/partner).
- `backend/scripts/smoke_e2e.py` against the live stack → all steps pass (OTP → cart → COD order → partner accept/ready → Celery dispatch → rider accept → OTP pickup → GPS approach → auto near-destination → OTP delivery → earnings/settlement/review/invoice/notifications → admin → cancel).
- `ruff check .` → clean. OpenAPI: 199 operations (`docs/api/openapi.yml`, `/api/docs/`).
- Load test (Locust, 60 users, gunicorn 4×4 + psycopg pool): ~26 req/s, median 63 ms, p95 210 ms, 0 errors. Fixed during the run: N+1 queries on home/list and Postgres connection exhaustion (now `DB_POOL=true`, `max_connections=200`).
- Dashboard: session login, JWT hand-off, live map/WS, orders/customers/restaurants/riders/finance/promotions/support/reports/settings pages verified via HTTP.

## Android (complete, compiled in Docker)
Workspace `android/`: core modules (`common`, `network`, `auth`, `designsystem`, `maps` (MapLibre), `location`, `push`, `database`), shared `feature/auth`, and three apps:
- **Customer** `in.localrush.customer` — onboarding, location picker, OTP login/guest, home (banners, categories, nearby/fast/popular/top-rated/featured, recently ordered, recommended), search, filters, restaurant details with variants/add-ons, cart + coupons, checkout (COD + Razorpay Checkout UPI/card/netbanking, verify/retry), orders, order detail (cancel/reorder/invoice/rate/help), live tracking (WebSocket, animated scooter marker, dashed route, ETA, OTP, rider call/chat), profile/addresses/notifications/support, FCM deep links. 27 unit tests.
- **Rider** `in.localrush.rider` — registration wizard with documents, pending approval, dashboard with online toggle + foreground GPS service (battery-aware, Room offline queue + batch replay), full-screen offers with countdown, navigation with turn-by-turn + voice, pickup/delivery OTP + proof photo, call/chat, problem reporting, earnings/payouts/history/profile/support. 34 unit tests.
- **Partner** `in.localrush.partner` — registration wizard with map pin + documents, pending approval, dashboard, real-time orders board (WS + FCM, loud alert, accept/reject/preparing/ready, pickup OTP, rider card), menu management (categories, items, variants, add-ons, stock, bulk availability), restaurant settings (hours/holidays/closure), finance (summary, lines, settlements, CSV), reviews with reply, notifications, support. 28 unit tests.
- Core unit tests: 34 (error envelope, money, polyline, token refresh single-flight, WebSocket reconnect).
Build: `android/BUILD.sh <tasks>` (Docker, no host SDK). Phone builds: `LOCALRUSH_API_BASE_URL=http://<server>:8001/api/v1/ LOCALRUSH_WS_BASE_URL=ws://<server>:8001/ws/`. Demo script: `docs/DEMO_GUIDE.md`. Not run on a device/emulator on this server (no KVM); verified by compilation + unit tests + Paparazzi JVM screenshot renders of 19 key screens (`docs/screenshots/apps/`, regenerate with `./BUILD.sh :app-customer:recordPaparazziDebug :app-rider:recordPaparazziDebug :app-partner:recordPaparazziDebug`).

## External configuration required before go-live
Razorpay keys + webhook, SMS provider (MSG91/Twilio), Firebase service account + `google-services.json` per app, OSM extract for OSRM/Nominatim, domain + Let's Encrypt, Sentry (optional). Details: `docs/DEPLOYMENT.md`.

## Security review
Independent backend review completed 2026-10-24; all high/medium findings fixed (see `docs/SECURITY.md`).

## Maps infrastructure verified
OSRM graph built from Geofabrik western-India extract (`deploy/scripts/prepare-osrm.sh`), running via `docker compose --profile maps`; routing API returns real road routes (sample Surat route 2.0 km / 233 s). Nominatim imported from the same extract; address search and reverse geocoding verified live.

## Known limitations / next steps
- ETA is traffic-independent (OSRM speed profiles); no live traffic source is integrated (by design, none claimed).
- Multi-vendor cart intentionally not implemented (single-restaurant cart, as specified).
- Batch deliveries supported via `rider_max_concurrent_orders` setting but the rider UI optimises for one active order.
- Invoice is HTML (print-to-PDF); a server-side PDF renderer can be added (WeasyPrint) if required.
