# LocalRush — Production deployment guide (single VPS)

Target: Ubuntu 22.04/24.04 VPS (4 vCPU / 8 GB RAM minimum with OSRM + Nominatim for one Indian state; 2 vCPU / 4 GB if maps run elsewhere), Docker Engine + Compose v2, a domain (e.g. `api.localrush.in`) pointing at the server.

## 1. Prepare the server
```bash
sudo apt update && sudo apt install -y docker.io docker-compose-v2 git ufw
sudo usermod -aG docker $USER && newgrp docker
sudo ufw allow OpenSSH && sudo ufw allow 80 && sudo ufw allow 443 && sudo ufw enable
git clone <your repo> /opt/localrush && cd /opt/localrush
```

## 2. Configure secrets (never commit these)
```bash
cp backend/.env.example backend/.env && nano backend/.env
```
Set at least: `SECRET_KEY` (64 random chars), `ALLOWED_HOSTS`, `CSRF_TRUSTED_ORIGINS`, `SITE_URL`, `POSTGRES_PASSWORD`, `REDIS_PASSWORD` and the password in `REDIS_URL`/`CELERY_BROKER_URL` (`redis://:<pw>@redis:6379/0`), `SMS_PROVIDER` + keys, `RAZORPAY_*`, `OSRM_BASE_URL=http://osrm:5000`, `NOMINATIM_BASE_URL=http://nominatim:8080`, `DB_POOL=true` (psycopg pool; size `DB_POOL_MAX` per process — keep workers × DB_POOL_MAX below Postgres `max_connections`), `MAP_STYLE_URL`, `SENTRY_DSN` (optional).
Firebase: download the service-account JSON from Firebase console → `mkdir -p secrets && cp ~/firebase.json secrets/firebase.json` (mounted read-only at `/run/secrets/firebase.json`).

## 3. Map data (self-hosted OSRM + Nominatim)
```bash
# pick the smallest Geofabrik extract covering your service area (state/zone); clip to a city with osmium for speed
deploy/scripts/prepare-osrm.sh https://download.geofabrik.de/asia/india/western-zone-latest.osm.pbf
deploy/scripts/prepare-nominatim.sh https://download.geofabrik.de/asia/india/western-zone-latest.osm.pbf
```
OSRM graph build: ~10–40 min for a zone. Nominatim imports on its first start (30–90 min); the API falls back to straight-line ETA and empty search results until the services are healthy.
Tiles: `MAP_STYLE_URL` defaults to OpenFreeMap (free, no key, OSM attribution required). To self-host, run a tile server (e.g. `planetiler` + `martin`/`tileserver-gl`) and point `MAP_STYLE_URL` at its style JSON.

## 4. TLS + first start
```bash
cd deploy
DOMAIN=api.localrush.in EMAIL=ops@localrush.in ./scripts/init-letsencrypt.sh
docker compose -f docker-compose.prod.yml --env-file ../backend/.env up -d --build
docker compose -f docker-compose.prod.yml exec api python manage.py createsuperuser   # admin login for /dashboard/
```
Check: `https://api.localrush.in/healthz/`, `/api/docs/`, `/dashboard/`.

## 5. Operations
- Redeploy: `deploy/scripts/deploy.sh` (pull, build, migrate, roll services).
- Backups: the `backup` service dumps the DB daily to `deploy/backups/` (retention `BACKUP_KEEP_DAYS`); copy off-site with rclone/S3 (see the script). Restore: `deploy/scripts/restore-db.sh <file>`. Media lives in the `media` volume — back it up with `docker run --rm -v localrush-prod_media:/m -v $PWD:/b alpine tar czf /b/media.tgz /m`.
- Logs: `docker compose -f docker-compose.prod.yml logs -f api worker ws`; JSON logs rotate at 20 MB × 5.
- Monitoring: `docker compose -f deploy/monitoring/docker-compose.monitoring.yml --env-file backend/.env up -d` → Grafana on `127.0.0.1:3000` (tunnel via SSH). Sentry via `SENTRY_DSN`.
- Scheduled jobs are Celery beat tasks (dispatch retries, offer expiry, auto-reject, settlements, location history flush, token cleanup) — no cron needed.
- Scaling: raise `GUNICORN_WORKERS`/`CELERY_CONCURRENCY`; move `db`/`redis` to managed services by changing URLs; run several `ws` replicas behind nginx (Channels uses Redis for groups); OSRM/Nominatim can live on a separate host.

## 6. Admin dashboard
`https://api.localrush.in/dashboard/` (session login for staff users; the page obtains a short-lived JWT for the admin API and WebSocket). Django admin remains at `/django-admin/` for raw data access.

## 7. External services checklist
| Service | Env keys | Where to get |
|---|---|---|
| Razorpay | `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET` | dashboard.razorpay.com → Settings → API keys; Webhooks → add `https://<host>/api/v1/payments/webhooks/razorpay/` with events `payment.captured, payment.failed, refund.processed, refund.failed` |
| SMS OTP | `SMS_PROVIDER=msg91` + `MSG91_AUTH_KEY`, `MSG91_TEMPLATE_ID` (or Twilio keys) | msg91.com (DLT-registered template required in India) |
| FCM | `FIREBASE_CREDENTIALS_FILE` + per-app `google-services.json` | console.firebase.google.com → project settings |
| Sentry | `SENTRY_DSN` | sentry.io |
| S3-compatible media (optional) | `STORAGE_BACKEND=s3`, `AWS_*` | any S3/R2/MinIO |
