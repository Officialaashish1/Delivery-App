# LocalRush — Security notes

- **Auth**: phone OTP (hashed with HMAC-SHA256 + SECRET_KEY, 5 attempts, 5-min expiry, 30-s cooldown, 8/hour), JWT access 30 min / refresh 30 days with rotation + blacklist, per-user `token_version` for global revocation (logout-all, suspension, password reset), Argon2 password hashing for password accounts.
- **Authorization**: role-based permissions (`IsCustomer`, `IsRider`, `IsApprovedRider`, `IsPartner`, `IsAdminRole`) plus object-level checks in every service (`_assert_restaurant_owner`, `_assert_rider`, customer order ownership, WebSocket group authorization with close codes 4401/4403/4404). Rider phone numbers are not exposed in order payloads; call/chat go through scoped endpoints that only work during delivery.
- **Payments**: no card data stored; Razorpay Checkout on-device; server verifies HMAC signature + fetches the payment + checks the amount; webhooks verified with the webhook secret and processed idempotently (`WebhookEvent`). Refunds only through provider APIs; COD limits configurable.
- **Server-side validation of rider actions**: pickup and delivery require OTPs known only to restaurant/customer; state transitions enforced centrally; GPS updates validated (bounds, accuracy, monotonic time, max speed) and only broadcast for the rider's own active order.
- **Input/uploads**: DRF serializers everywhere, file MIME sniffing with libmagic + size limits, images re-encoded server-side (strips metadata), uploads served from `/media/` without execution.
- **Transport/headers**: HTTPS only (HSTS preload), secure cookies, `X-Frame-Options DENY`, nosniff, CSRF for the session-based dashboard, CORS allow-list, nginx rate limits (auth 5 r/s, API 30 r/s) + DRF throttles (OTP 5/min, location 60/min, payment 30/min).
- **Secrets**: all from environment (`backend/.env`, mounted `/run/secrets/firebase.json`); `.gitignore` excludes them; production refuses to start with a dev OTP code set.
- **Audit**: `AuditLog` records logins, cancellations, refunds, approvals, settings changes, payouts, suspensions with actor/IP; order status history is immutable and timestamped.
- **Data protection**: account deletion endpoint anonymises PII (Play policy), soft deletes for business data, daily encrypted-at-rest backups recommended (enable disk encryption on the VPS or use a managed Postgres).
- **Recommended hardening before launch**: enable Sentry, set `SECURE_SSL_REDIRECT`, use a managed Postgres with PITR, restrict `/django-admin/` by IP in nginx, rotate `SECRET_KEY`/Razorpay keys on staff changes, run `pip-audit` in CI.

## Independent review (2026-10-24) — findings and fixes
An independent code review of the backend found 3 high, 6 medium and 8 low issues; all high/medium and 7 low were fixed the same day (regression tests in `backend/tests/test_security_fixes.py`):
- **Pickup/delivery OTP brute force** → per-order attempt counter persisted outside the mutation transaction, lock after 5 wrong codes (HTTP 423 `otp_locked`, admins notified), `rider_otp` throttle 10/min.
- **Late/duplicate payment captures kept** (retry race, pay-after-cancel) → captures that cannot be applied to the order are refunded against that specific payment; webhook event rows are committed before processing so failures are recorded for replay.
- **Throttle keys spoofable via X-Forwarded-For** → DRF `NUM_PROXIES=1`, nginx overwrites `X-Forwarded-For` with `$remote_addr`, audit IP from `X-Real-IP` only.
- Coupon limits now count every live order (not only confirmed usages); first-order coupons check real orders; coupon row locked during placement.
- Stock is row-locked and quantity-checked at placement (no oversell); cart row locked against concurrent placements; idempotency key unique per customer.
- Money fields reject negatives (menu prices, add-ons, variants); pricing rejects non-positive lines.
- Settlements: closed (paid) settlements are immutable; late lines create a supplementary settlement; totals recomputed from lines.
- Rider payouts cannot be processed twice; earnings settled only up to the payout amount.
- Tracking snapshots are role-specific (partners never receive the customer's phone).
- Admin user PATCH can no longer flip `is_active`/`is_suspended` (use the audited suspend action); cancelling an already-cancelled order is rejected (no double restock); OTP attempt counter updated under row lock; logout only deactivates the caller's own device; account deletion blocked while orders are active; approved riders cannot change payout details themselves; geocode/route proxies throttled.
- Not changed (by design, documented): `delivery_failed` orders are not auto-refunded — support decides case by case from the admin dashboard.
