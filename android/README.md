# LocalRush Android

One Gradle workspace (Kotlin DSL + version catalog) containing the three LocalRush apps and the shared
core modules they are built on.

| Module | What it is |
|---|---|
| `:app-customer` | `in.localrush.customer` — "LocalRush" (ordering app) |
| `:app-rider` | `in.localrush.rider` — "LocalRush Rider" (delivery partner app, foreground location service) |
| `:app-partner` | `in.localrush.partner` — "LocalRush Partner" (restaurant app, password login) |
| `:feature:auth` | Shared Phone → OTP (+ password) login flow used by all three apps |
| `:core:common` | `Result<T, AppError>`, error-envelope parser, `Money` (₹, Indian grouping), date/time utils, `OrderStatus`/`TimelineKey`, polyline5 codec, haversine, `Constants` |
| `:core:network` | Retrofit + OkHttp + kotlinx.serialization, `AuthInterceptor`, refresh `TokenAuthenticator` (single-flight), DTOs + APIs for shared endpoints, multipart helpers, `WebSocketClient` |
| `:core:auth` | `TokenStore` (EncryptedSharedPreferences), `SessionManager` (`StateFlow<SessionState>`), role-aware `AuthRepository` |
| `:core:designsystem` | Material 3 theme (orange `#F26B1D` / charcoal `#23272F`), light + dark, `LRButton`, `LRTextField`, `OtpInput`, `StatusChip`, `RatingBar`, `VegNonVegDot`, `PriceText`, `LoadingOverlay`, `EmptyState`, `ErrorState`, `OfflineBanner`, shimmer, `LRBottomSheet`, `StepProgressBar`, brand `ImageVector` icons |
| `:core:location` | Fused location helpers, permission state, `locationUpdates()` Flow, `ForegroundLocationService` base, battery-aware interval policy |
| `:core:maps` | MapLibre Native 11 Compose wrapper: `LRMap`, `Marker`, `RoutePolyline`, `CameraState` (follow / fit bounds / padding), `MarkerAnimator` |
| `:core:push` | FCM base service (`LRMessagingService`), notification channels per app, payload parser, deep-link intents, `PushTokenManager` |
| `:core:database` | Room database with sample DAOs (`cached_json`, `pending_locations`, `recent_searches`) |

Tech: Kotlin 2.0.21, AGP 8.7.3, Gradle 8.10.2, compileSdk 36, minSdk 24, targetSdk 35, Compose BOM 2024.12.01,
Hilt 2.53.1 (KSP), Navigation-Compose 2.8 (type-safe routes), Coroutines/Flow, Coil, Room, DataStore, MapLibre 11.8.

## Building

### With Docker (no JDK/Android SDK on the host)

```bash
./BUILD.sh                      # assembleDebug for the 3 apps + core unit tests (the default task set)
./BUILD.sh :app-rider:assembleDebug
./BUILD.sh :core:network:testDebugUnitTest
./BUILD.sh clean
```

`BUILD.sh` runs `./gradlew` inside `ghcr.io/cirruslabs/android-sdk:36` (JDK 21, build-tools 36.0.0, platform 36)
as the host user, with the Gradle cache in the named volume `localrush-gradle` so repeated builds are fast
(first build downloads ~1 GB of dependencies; incremental builds take 1–3 min). Outputs:

```
app-customer/build/outputs/apk/debug/app-customer-debug.apk
app-rider/build/outputs/apk/debug/app-rider-debug.apk
app-partner/build/outputs/apk/debug/app-partner-debug.apk
```

Environment variables `LOCALRUSH_API_BASE_URL`, `LOCALRUSH_WS_BASE_URL` and the `LOCALRUSH_KEYSTORE_*` variables
are forwarded into the container (see below).

Notes:
- The image's SDK directory is root-owned, so every module pins `buildToolsVersion = "36.0.0"` (the only version
  installed) — AGP would otherwise try to download build-tools 34 and fail.
- `gradle/wrapper/gradle-wrapper.jar` is committed; `./gradlew` works anywhere with a JDK 17+.

### With Android Studio (Ladybug or newer)

Open the `android/` folder. Studio uses the committed wrapper (Gradle 8.10.2) and JDK 17/21. Nothing else to configure;
`local.properties` with `sdk.dir` is created automatically. Run configurations: `app-customer`, `app-rider`, `app-partner`.

## API base URL

Each app injects `BuildConfig.API_BASE_URL` / `BuildConfig.WS_BASE_URL` into `NetworkConfig` (see `di/AppModule.kt`).

| Build type | Default | Override (gradle property / `local.properties` / env var) |
|---|---|---|
| debug | `http://10.0.2.2:8001/api/v1/` and `ws://10.0.2.2:8001/ws/` (emulator → host port 8001) | `LOCALRUSH_DEBUG_API_BASE_URL`, `LOCALRUSH_DEBUG_WS_BASE_URL` |
| release | `https://api.localrush.in/api/v1/` and `wss://api.localrush.in/ws/` | `LOCALRUSH_API_BASE_URL`, `LOCALRUSH_WS_BASE_URL` |

Physical device against a dev backend: set `LOCALRUSH_DEBUG_API_BASE_URL=http://<LAN-IP>:8001/api/v1/` and add the IP to
`app-*/src/debug/res/xml/network_security_config.xml`. Cleartext (`http://`) is permitted **only** in debug builds and
only for the listed hosts; release builds use `src/release/res/xml/network_security_config.xml` (HTTPS only).

Start the backend with `docker compose up` from the repo root; in DEBUG the OTP is fixed to `123456`
(`OTP_DEV_FIXED_CODE`), and the OTP screen shows it when the API returns `dev_code`.

## Firebase (push notifications)

Builds work **without** Firebase credentials: the `com.google.gms.google-services` plugin is applied only if
`app-<x>/google-services.json` exists, and `PushTokenManager` no-ops when `FirebaseApp` isn't initialised.

To enable FCM:
1. Create a Firebase project; add three Android apps with package names `in.localrush.customer`, `in.localrush.rider`,
   `in.localrush.partner`.
2. Download each `google-services.json` into the matching module directory (`app-customer/`, …). A template is in
   `google-services.json.example`. The files are git-ignored.
3. Rebuild. Tokens are registered through `POST auth/devices/` after login and on `onNewToken`.
4. Optional sounds: add `res/raw/rider_offer.mp3` (rider) and `res/raw/new_order.mp3` (partner); the channels pick them up
   automatically (`NotificationChannels`). Channel IDs are fixed by the API contract (`localrush_orders`,
   `localrush_promos`, `localrush_rider_offers`, `localrush_rider_orders`, `localrush_partner_orders`).
5. The backend sends **data** messages (`type, order_id, order_number, status, assignment_id, expires_at, title, body`);
   `LRMessagingService` posts the notification and deep-links to `localrush://order/<id>`, `localrush://offer/<id>`, etc.

## Signing (release)

Release signing is read from gradle properties → `local.properties` → environment, in that order:

```
LOCALRUSH_KEYSTORE_FILE=/abs/path/release.jks
LOCALRUSH_KEYSTORE_PASSWORD=...
LOCALRUSH_KEY_ALIAS=localrush
LOCALRUSH_KEY_PASSWORD=...
LOCALRUSH_VERSION_CODE=1
LOCALRUSH_VERSION_NAME=0.1.0
```

If no keystore is configured, `assembleRelease` produces an unsigned APK (R8 minified + resource shrinking are on;
ProGuard rules for Retrofit/OkHttp/kotlinx.serialization/MapLibre/Tink ship as consumer rules in the core modules).
Generate a keystore with `keytool -genkeypair -v -keystore release.jks -alias localrush -keyalg RSA -keysize 4096 -validity 10000`.
Use one keystore per app or one for all; never commit it (`*.jks` is git-ignored).

## Razorpay

Payments are not wired in this foundation. When the customer engineer adds Checkout:
- The key id comes from `GET config/` (`razorpay_key_id`) and from the `payment` object of `POST orders/`
  (`RazorpayCheckoutDto`); do not hard-code a test key in the app.
- Add `com.razorpay:checkout` to the version catalog and the keep rules noted in `app-customer/proguard-rules.pro`.
- Verify server-side via `POST payments/verify/` (`OrderApi.verifyPayment`) — never mark an order paid on the client.

## Maps

MapLibre Native with the style URL and attribution from `GET config/` (defaults to OpenFreeMap Liberty). No Google Maps
dependency. Routing/geocoding go through the backend (`TrackingApi.route`, `ConfigApi.geocodeSearch/reverseGeocode`).

## Where to go next

Read `CONVENTIONS.md` before adding screens. Feature code goes under `feature/<name>/{ui,vm,data}` in each app
(or a shared `:feature:<name>` module when two apps need it, like `:feature:auth`).
