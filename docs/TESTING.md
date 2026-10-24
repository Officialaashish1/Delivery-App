# LocalRush — Testing

## Backend (pytest, 42 tests)
```bash
docker compose up -d db redis api
docker compose exec api pytest            # uses config.settings.test (eager Celery, in-memory channel layer, local cache)
docker compose exec api pytest --cov=apps --cov-report=term-missing
docker compose exec api ruff check .
```
Coverage by file:
- `tests/test_auth.py` — OTP request/verify/attempt lock, cooldown, wrong-app, refresh rotation + blacklist, token revocation (`logout-all`), suspension, password login, role permissions, address CRUD/default.
- `tests/test_cart_pricing.py` — delivery fee tiers, cart math with variants/add-ons/packaging/GST, required variants + add-on limits, single-restaurant cart conflict + replace, quantity merge, coupons (percent cap, free delivery, invalid, first-order, applicability), discovery radius/sort/search/home/open-only.
- `tests/test_order_lifecycle.py` — full COD flow (placement → accept → dispatch → rider accept → ready → OTP pickup → OTP delivery → earnings/settlement/stats/review/notifications), idempotent placement, stock decrement/restock, customer cancel rules, restaurant cancel releases rider, rider rejection re-offers to the next rider + offer expiry sweep, state machine invalid transitions, closed restaurant, auto-reject timeout, object-level authorization.
- `tests/test_payments.py` — Razorpay order creation (fake client), signature verification, amount tamper rejection, retry, webhook signature + idempotency, payment failed → retry → captured, restaurant rejection → provider refund → refund status, COD limit.
- `tests/test_tracking.py` — location validation (bounds, accuracy, implausible speed, stale), snapshot + route, offline batch replay → automatic near-destination, history flush to PostGIS, cross-rider isolation; WebSockets: auth/ownership close codes, snapshot, live location push with route, chat round-trip, ping/pong, rider socket streaming, partner new-order push, admin socket authorization.
- `tests/test_admin_partner.py` — restaurant registration + menu CRUD with nested variants/add-ons, availability, hours, admin approve/reject/resubmit, rider approval gate, admin cancel/reassign/overview/reports/audit, suspension, settings → pricing, settlement generation + mark paid + CSV, rider payout request/process, support ticket round-trip.

## Live-stack end-to-end
`backend/scripts/smoke_e2e.py` drives the real containers (REST + Celery + Redis): run after `python manage.py seed --demo`.
```bash
docker run --rm --network localrush_default -v $PWD/backend/scripts:/s:ro python:3.12-slim python -I /s/smoke_e2e.py
```
It exercises: admin cleanup, customer OTP login, home/list/detail/search, cart + coupon, COD order, rider online + GPS, partner accept/preparing/ready, Celery dispatch offer, rider accept, OTP pickup, GPS approach (≤190 m steps, anti-teleport), auto near-destination, OTP delivery, earnings, review, invoice, notifications, partner finance, reorder, admin overview/search/detail/live, customer cancel, rider offline.

## Load / performance
`backend/loadtests/locustfile.py` (customer browsing + rider GPS writes). Create a token pool first so the per-IP OTP throttle does not block the load generator:
```bash
docker compose exec api python manage.py loadtest_tokens --customers 200 --riders 50   # DEBUG only
docker compose run -d --rm --no-deps --name lr-gunicorn -e DB_POOL=true api gunicorn config.wsgi:application -w 4 --threads 4 -b 0.0.0.0:8000
docker run --rm --network localrush_default -v $PWD/backend/loadtests:/mnt/locust:ro locustio/locust:2.32.5 \
  -f /mnt/locust/locustfile.py --host http://lr-gunicorn:8000 --headless -u 60 -r 10 -t 60s --only-summary
```
Result on the dev server (5 vCPU shared with Docker builds, 60 concurrent users, gunicorn 4×4, psycopg pool): **~26 req/s, median 63 ms, p95 210 ms, 0 errors**. Before the N+1 fix on home/list (per-restaurant hours + coupon queries) and without connection pooling the same run produced 2.4 s median and PostgreSQL "too many clients" 500s — keep `DB_POOL=true` in production (default in `.env.example`).

## Android
Screenshot tests: Paparazzi (JVM, no emulator) renders key screens of all three apps — `./BUILD.sh :app-customer:recordPaparazziDebug :app-rider:recordPaparazziDebug :app-partner:recordPaparazziDebug` → PNGs in each module's `src/test/snapshots/images/` (copies in `docs/screenshots/apps/`).
Unit tests live in each module (`./gradlew testDebugUnitTest`), instrumented/UI tests under `src/androidTest` (`./gradlew connectedDebugAndroidTest` with an emulator). Build/test commands are in `android/README.md`.

## Network loss / GPS recovery
Covered by: rider offline queue (batch replay test + app-side queue), WebSocket reconnect with backoff (client), REST fallback `GET /tracking/orders/{id}/`, `mark_stale_riders_offline` beat task, anti-teleport and monotonic timestamp checks.
