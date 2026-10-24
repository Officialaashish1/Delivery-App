#!/usr/bin/env bash
# Zero-downtime-ish redeploy: build new image, run migrations, roll services.
set -euo pipefail
cd "$(dirname "$0")/.."
git pull --ff-only
docker compose -f docker-compose.prod.yml --env-file ../backend/.env build api
docker compose -f docker-compose.prod.yml --env-file ../backend/.env up -d migrate
docker compose -f docker-compose.prod.yml --env-file ../backend/.env up -d --no-deps api ws worker beat
docker image prune -f >/dev/null
docker compose -f docker-compose.prod.yml ps
