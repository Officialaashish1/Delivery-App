#!/bin/sh
# Daily PostgreSQL dump with retention. Runs inside the `backup` service; also usable manually:
#   docker compose -f deploy/docker-compose.prod.yml exec db sh /backups/../backup-db.sh
set -e
STAMP=$(date +%Y%m%d-%H%M%S)
OUT=/backups/localrush-$STAMP.dump
pg_dump -h db -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Fc -f "$OUT"
gzip -f "$OUT"
find /backups -name 'localrush-*.dump.gz' -mtime +"${BACKUP_KEEP_DAYS:-14}" -delete
echo "backup written: $OUT.gz"
# Optional off-site copy: uncomment and configure rclone/aws cli
# aws s3 cp "$OUT.gz" "s3://$BACKUP_BUCKET/db/" --storage-class STANDARD_IA
