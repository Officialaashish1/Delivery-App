# LocalRush — APK/AAB generation & Play Store release

## Build release artifacts (no local Android SDK needed — Docker)
```bash
# 1. signing keys (keep outside git; one per app or one shared upload key)
keytool -genkeypair -v -keystore ~/keys/localrush-upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
# 2. put credentials in android/keystore.properties (git-ignored):
#    storeFile=/keys/localrush-upload.jks  storePassword=...  keyAlias=upload  keyPassword=...
# 3. set the production API in android/local.properties or gradle.properties: API_BASE_URL=https://api.localrush.in/api/v1/  WS_BASE_URL=wss://api.localrush.in/ws/
# 4. build
android/BUILD.sh :app-customer:bundleRelease :app-rider:bundleRelease :app-partner:bundleRelease   # .aab for Play
android/BUILD.sh :app-customer:assembleRelease                                                     # .apk for sideload/testing
```
Outputs: `android/app-*/build/outputs/bundle/release/*.aab` and `.../apk/release/*.apk`.

## Firebase
Create one Firebase project with three Android apps (package names `in.localrush.customer`, `in.localrush.rider`, `in.localrush.partner`); download each `google-services.json` into the matching module folder; put the service-account JSON on the server (`secrets/firebase.json`).

## Play Console checklist (per app)
1. App content: privacy policy URL (host at `https://localrush.in/privacy`), data safety form — collected: phone, name, precise location (customer: delivery; rider: background location for live tracking), photos (uploads), financial info (orders; payments handled by Razorpay). Account deletion: in-app (`DELETE /auth/account/`) + a web URL.
2. **Rider app background location**: Play requires a prominent in-app disclosure before requesting `ACCESS_BACKGROUND_LOCATION`, a declaration form with a video showing the foreground-service notification, and a justification ("live tracking of active deliveries"). The rider app only tracks while online/on delivery.
3. Foreground service types declared in the manifest (`location`) with the Play declaration.
4. Target API level ≥ 35; 64-bit (MapLibre ships arm64 + x86_64 natives); App Bundle; `minSdk 24`.
5. Store listing: title "LocalRush – Food Delivery", short/long description, 1024×500 feature graphic, ≥4 phone screenshots per app, 512×512 icon, category Food & Drink (customer), Business (partner/rider).
6. Internal testing track → closed testing (≥20 testers for 14 days for new developer accounts) → production with staged rollout.
7. Versioning: bump `versionCode` on every upload; keep `versionName` aligned with the backend `min_app_version_*` settings (admin → Settings) to force updates when APIs change.
8. Razorpay live keys only in release builds; test keys in debug.
