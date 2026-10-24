# LocalRush API contracts (shared by Customer, Rider, Partner apps and Admin dashboard)

Base URL: `https://<host>/api/v1/` · Full schema: `docs/api/openapi.yml` · Swagger UI: `/api/docs/`

## Auth
- Phone OTP: `POST auth/otp/request/ {phone (E.164 or 10-digit IN), role: customer|rider|partner}` → `{message, expires_in, is_existing_user}`
  then `POST auth/otp/verify/ {phone, code, role, full_name?, email?, referral_code?}` → `{access, refresh, user, is_new_user, profile_complete}`
  Dev OTP is fixed to `123456` when `OTP_DEV_FIXED_CODE` is set (DEBUG only).
- Password login (partners/admins): `POST auth/login/ {phone, password, role?}` → same payload.
- Refresh: `POST auth/token/refresh/ {refresh}` → `{access, refresh}` (rotating; old refresh blacklisted).
- Logout: `POST auth/logout/ {refresh, fcm_token?}`; `POST auth/logout-all/`.
- `GET/PATCH auth/me/`, `POST auth/devices/ {fcm_token, platform, app: customer|rider|partner, device_id, app_version}`, `DELETE auth/account/`.
- Header: `Authorization: Bearer <access>`. Access TTL 30 min, refresh 30 days. 401 with code `token_revoked`/`account_suspended` → force logout.
- Public runtime config: `GET config/` → map style URL, attribution, Razorpay key id, COD rules, min app versions, support contact.

## Error envelope (every non-2xx)
```json
{"error": {"code": "cart_restaurant_conflict", "message": "Human readable", "fields": {"field": ["msg"]}, "...extra"}}
```
Common codes: `validation_error`, `not_found`, `permission_denied`, `otp_invalid` (+`attempts_left`), `otp_expired`, `otp_cooldown` (429),
`wrong_app` (409), `cart_restaurant_conflict` (409, +`current_restaurant`), `restaurant_closed`, `coupon_*`, `invalid_transition` (409, +`current_status`),
`offer_expired`/`offer_unavailable` (409), `not_ready`, `not_cancellable`, `payment_invalid_signature`, `payment_provider_unavailable` (503).

## Pagination
List endpoints return `{count, next, previous, results}`; `?page=&page_size=` (max 100).

## Customer
- Home: `GET restaurants/home/?lat&lng&city` → `{banners[], categories[], cuisines[], sections[{key,title,restaurants[]}], recently_ordered[], recommended_items[]}`
- List/search: `GET restaurants/?lat&lng&q&cuisine&category&veg&min_rating&open_only&sort=relevance|rating|delivery_time|distance|cost_low|cost_high`
- `GET restaurants/search/?q&lat&lng` → `{restaurants[], items[], cuisines[]}`; `GET restaurants/{slug}/?lat&lng` → detail incl. `menu[{id,name,items[]}]` with `variant_groups`/`addon_groups`.
- `GET menu/items/{id}/`; `GET restaurants/cuisines/`, `GET restaurants/categories/`.
- Cart (server side, one restaurant): `GET cart/`, `POST cart/ {menu_item_id, quantity, variant_id?, addon_ids[], special_instructions?, replace_cart?}`,
  `PATCH cart/ {delivery_address_id?, coupon_code?, special_instructions?}`, `DELETE cart/`, `PATCH/DELETE cart/items/{id}/ {quantity}`.
  Cart response has `pricing {item_total, packaging_charge, delivery_fee, delivery_discount, platform_fee, tax_percent, tax_amount, coupon_code, coupon_discount, distance_km, total, min_order_amount, meets_min_order, errors[]}`.
- Addresses: CRUD `customers/addresses/` (`latitude`,`longitude` on write; `lat`,`lng` on read; `label home|work|other`, `is_default`). Profile: `GET/PATCH customers/profile/`.
- Geocoding: `GET config/geocode/search/?q&lat&lng`, `GET config/geocode/reverse/?lat&lng`.
- Coupons: `GET coupons/?restaurant=<id>&subtotal=` → `[{coupon, applicable, discount, reason}]`.
- Orders: `POST orders/ {address_id, payment_method: cod|upi|card|netbanking, idempotency_key, is_contactless, delivery_instructions, customer_note}`
  → `{order, payment}`; `payment` is null for COD, else Razorpay checkout payload `{key_id, provider_order_id, amount_paise, currency, name, description, prefill, notes, theme_color}`.
  After Razorpay Checkout success: `POST payments/verify/ {order_id, razorpay_order_id, razorpay_payment_id, razorpay_signature}` → order detail.
  Retry: `POST payments/orders/{id}/retry/` → `{payment}` (checkout payload only). `GET orders/?scope=active|past` (active includes `pending_payment`/`payment_failed` so the customer can retry), `GET orders/{id}/` (has `timeline[]`, `delivery_otp` while rider en route, `rider{}`, `pricing{}`, `refund_status`),
  `POST orders/{id}/cancel/ {reason}`, `POST orders/{id}/reorder/`, `GET orders/{id}/invoice/` (HTML), `GET orders/{id}/rider-contact/`.
- Tracking: `GET tracking/orders/{id}/` → `{order, rider_location{latitude,longitude,bearing,speed,recorded_at}, route{geometry(polyline5),distance_m,duration_s,duration_min,leg,eta_at}, restaurant_location, delivery_location, chat[]}`.
  Chat: `GET/POST tracking/orders/{id}/chat/`. Routing proxy: `GET tracking/route/?from_lat&from_lng&to_lat&to_lng&steps=1`.
- Reviews: `POST reviews/ {order_id, restaurant_rating, restaurant_comment, rider_rating, rider_comment, item_ratings{}, tags[]}`, `GET reviews/restaurant/?restaurant=`, `GET reviews/mine/`.
- Notifications: `GET notifications/`, `GET notifications/unread-count/`, `POST notifications/mark-read/ {ids?}`.
- Support: `GET support/faqs/?audience=`, `GET support/contact/`, `support/tickets/` (list/create `{order?, category, subject, description}`, `POST .../{id}/reply/ {text, attachment?}` → the created message, `.../close/`).
- Payments/refunds: `GET payments/orders/{id}/`, `GET payments/refunds/`.

Order statuses: `pending_payment, placed, restaurant_accepted, preparing, ready_for_pickup, rider_assigned, picked_up, out_for_delivery, near_destination, delivered, rejected, cancelled, payment_failed, delivery_failed`.
Timeline keys (customer UI): `placed, accepted, preparing, ready, rider_assigned, picked_up, on_the_way, near, delivered`.

## Rider
- `GET/PATCH riders/profile/` (multipart for `photo`), `riders/documents/` (list/create multipart `{doc_type, file, number}`/delete),
  `POST riders/online/ {is_online, latitude?, longitude?}`, `GET riders/dashboard/` → earnings summary + `current_order` + `pending_offers[]` + `incentives[]`.
- Offers: `GET riders/offers/` → `[{id, order{...}, distance_to_restaurant_km, expires_at, seconds_left}]`, `POST riders/offers/{id}/accept/` → RiderOrder, `POST riders/offers/{id}/reject/ {reason}` → 204.
- `riders/dashboard/.current_order` and `riders/orders/?scope=active` include accepted orders still being prepared (`restaurant_accepted`, `preparing`, `ready_for_pickup`) as well as `rider_assigned … near_destination`.
- Orders: `GET riders/orders/?scope=active|completed|today`, `GET riders/orders/{id}/`, `POST .../arrived-restaurant/`, `POST .../pickup/ {otp}` (restaurant's pickup OTP),
  `POST .../deliver/ {otp, proof?}` (multipart; customer's delivery OTP). Wrong codes return 400 `otp_invalid` with `attempts_left`; after 5 wrong codes the order returns 423 `otp_locked` until support intervenes. `POST .../delivery-failed/ {reason}`, `GET .../customer-contact/`, `GET .../route/?lat&lng` → turn-by-turn `{leg, geometry, distance_m, duration_s, steps[{instruction,name,distance_m,duration_s,maneuver_type,modifier,location[lng,lat]}]}`.
- Location: `POST rider/location/ {order_id?, latitude, longitude, accuracy, speed(km/h), bearing, timestamp(ISO8601)}` → `{accepted, reason?, order_tracked, order_id?}`; batch `{locations:[...]}` (offline replay, max 100) → `{results:[{accepted, reason?, detail?, order_tracked?}]}`.
  Rejections: `implausible_speed`, `stale`, `low_accuracy`, `offline`. Also available over WS (below).
- Earnings: `GET riders/earnings/?period=today|week|month|all` → `{summary, entries[]}`, `GET riders/payouts/`, `POST riders/payouts/request/ {amount?}`.

## Partner
- `GET/POST/PATCH partner/restaurant/` (multipart for logo/cover; `latitude`/`longitude` on write), `POST partner/restaurant/toggle/ {is_open, is_accepting_orders, temporarily_closed_until}`,
  `PUT partner/restaurant/hours/ {hours:[{weekday 0-6, open_time, close_time, is_closed}]}`, `partner/holidays/`, `partner/documents/` (multipart), `GET partner/dashboard/`, `GET partner/best-sellers/`.
- Menu: `partner/menu/categories/` CRUD + `POST .../reorder/ {ids[]}`; `partner/menu/items/` CRUD (nested `variant_groups[{name,is_required,options[{name,price,is_default}]}]`, `addon_groups[{name,min_select,max_select,options[{name,price,food_type}]}]`),
  `POST .../{id}/availability/ {is_available, stock_quantity?}`, `POST .../bulk_availability/ {ids[], is_available}`. `?q=&category=&is_available=`.
- Orders: `GET partner/orders/?scope=new|active|preparing|ready|today|past&status=`, `GET partner/orders/{id}/` (includes `pickup_otp`), `POST .../accept/ {preparation_minutes?}`, `.../reject/ {reason}`, `.../preparing/`, `.../ready/`, `.../cancel/ {reason}`.
- Finance: `GET partner/finance/summary/?date_from&date_to`, `GET partner/finance/lines/?unsettled=1`, `GET partner/finance/settlements/`, `GET partner/finance/statement.csv?date_from&date_to`.
- Reviews: `GET reviews/partner/`, `POST reviews/partner/{id}/reply/ {reply}`.

## WebSockets (`wss://<host>/ws/...?token=<access>`; close codes 4401 unauthenticated, 4403 forbidden, 4404 not found)
- Server sends `{"type":"ping"}` every 25s → client replies `{"type":"pong"}`. Reconnect with backoff on close.
- `/ws/orders/{order_id}/tracking/` (customer owner, restaurant owner, assigned rider, admin). First message `{"type":"snapshot", order, rider_location, route, restaurant_location, delivery_location, chat[]}`.
  Then: `{"type":"status.<status>" | "order.placed" | "rider.assigned" | "rider.offered" | "rider.at_restaurant", order_id, status, status_display, estimated_delivery_at, rider{}, timeline[]}`,
  `{"type":"rider.location", order_id, rider_id, latitude, longitude, bearing, speed, accuracy, recorded_at, route?{geometry,duration_s,distance_m,eta_at,leg}}` (route present only when recalculated),
  `{"type":"chat.message", id, sender_id, sender_role, text, created_at}`. Client → `{"type":"chat","text":"..."}`, `{"type":"chat_read"}`.
- `/ws/rider/` (rider app): receives `rider.offered` (+`assignment_id`,`expires_at`), order status events, `order.unassigned`. Client may stream `{"type":"location", ...same as REST}` → `{"type":"location.ack", accepted, ...}`.
- `/ws/partner/`: all order events for the restaurant (new order = `status.placed`).
- `/ws/admin/live/`: snapshot `{riders[], active_orders[]}` then every `rider.location` and order event.

## Push (FCM data payload)
Keys: `type` (event, e.g. `status.out_for_delivery`, `rider.offered`, `chat.message`, `promo`, `ticket.reply`), `order_id`, `order_number`, `status`, `assignment_id`, `expires_at`, `title`, `body`.
Android channels: customer `localrush_orders`, `localrush_promos`; rider `localrush_rider_offers` (high importance, custom sound), `localrush_rider_orders`; partner `localrush_partner_orders` (high importance, loud).

## Maps
MapLibre style URL and attribution come from `GET config/`. Routing is self-hosted OSRM via the backend (never call OSRM directly from apps). Geocoding via `config/geocode/*`.
