#!/usr/bin/env bash
# Restore a dump into the running db container. DESTROYS current data. Usage: restore-db.sh deploy/backups/localrush-XXXX.dump.gz
set -euo pipefail
FILE="${1:?dump file}"
cd "$(dirname "$0")/.."
docker compose -f docker-compose.prod.yml stop api ws worker beat
gunzip -c "$FILE" | docker compose -f docker-compose.prod.yml exec -T db sh -c 'pg_restore -U "$POSTGRES_USER" -d "$POSTGRES_DB" --clean --if-exists --no-owner'
docker compose -f docker-compose.prod.yml start api ws worker beat
echo "restored $FILE"
