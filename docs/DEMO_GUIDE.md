# LocalRush — end-to-end demo on a real phone

The dev server runs on this machine (LAN IP `50.50.50.14`). Phone and server must be on the same network (or expose the server with a tunnel/domain).

## 1. Backend
```bash
cd /home/oneshot/Desktop/LocalRush
docker compose up -d                      # api on :8001, worker, beat, db, redis
docker compose exec api python manage.py seed --demo
```
Admin dashboard: http://50.50.50.14:8001/dashboard/ (`+919999999999` / `admin12345`). Dev OTP for every app login is `123456`.

## 2. Build APKs pointing at the server
```bash
cd android
LOCALRUSH_API_BASE_URL=http://50.50.50.14:8001/api/v1/ LOCALRUSH_WS_BASE_URL=ws://50.50.50.14:8001/ws/ \
  ./BUILD.sh :app-customer:assembleDebug :app-rider:assembleDebug :app-partner:assembleDebug
```
APKs: `android/app-customer/build/outputs/apk/debug/app-customer-debug.apk`, `android/app-rider/build/outputs/apk/debug/app-rider-debug.apk`, `android/app-partner/build/outputs/apk/debug/app-partner-debug.apk`.
Copy them to the phone (USB, or `python3 -m http.server 8080` inside that folder and download from the phone) and install (allow "unknown sources"). Three different apps can be installed on one phone; use two phones for the most realistic demo (customer + rider).

## 3. Demo flow (10 minutes)
| Step | App | What to do | What to watch |
|---|---|---|---|
| 1 | Partner | Login `+919000000001` (OTP `123456` or password `partner12345`) → Dashboard → toggle **Open** | Restaurant appears in customer app |
| 2 | Rider | Login `+919100000001` → accept location permissions → **Go online** | Foreground notification "LocalRush Rider is tracking"; rider dot on admin Live tracking map |
| 3 | Customer | Login `+919200000001` → allow location → Home → "Spice Villa" → add Paneer Tikka + Dal Makhani → Cart → coupon `FREEDEL` → Checkout → address "Lajamni Chowk" → **Cash on delivery** → Place order | Order confirmation; admin dashboard "Orders today" increments |
| 4 | Partner | New order alert (sound) → **Accept** (15 min) → **Preparing** | Customer timeline moves to "Restaurant accepted" / "Preparing" |
| 5 | Rider | Offer screen with countdown → **Accept** | Customer sees rider card (name, rating); partner sees rider assigned |
| 6 | Partner | **Food ready** (pickup OTP shown large) | Rider gets "Pickup ready" |
| 7 | Rider | Navigate to restaurant (turn-by-turn) → Arrived → enter pickup OTP → Picked up | Customer status "On the way", scooter marker starts moving on the map with the dashed route and ETA |
| 8 | Rider | Navigate to customer → Call / Chat (optional) | Customer receives chat message instantly; admin sees live route |
| 9 | Customer | Tracking screen shows **delivery OTP** when rider is near | Status "Near your location" auto-triggers within 300 m |
| 10 | Rider | Enter delivery OTP → Delivered | Customer gets "Delivered"; rider earnings toast; partner finance updates; admin can generate the settlement |
| 11 | Customer | Rate restaurant + rider, download invoice, reorder | Ratings update on the restaurant card |

Online payment demo needs Razorpay test keys in `backend/.env` (`RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`); use Razorpay's test UPI/cards. Push notifications need Firebase (`secrets/firebase.json` + `google-services.json` per app); without them the apps still receive everything over WebSockets while open.

## 4. Admin side during the demo
Orders → row → detail (timeline, OTPs, map); Live tracking (rider marker + route); Riders/Restaurants approval queues (register a new rider/restaurant from the apps to see them appear as "pending"); Finance → Generate settlements → Mark paid; Promotions → create a coupon and use it in the customer app immediately.
