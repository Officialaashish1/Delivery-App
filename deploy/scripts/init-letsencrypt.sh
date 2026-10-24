#!/usr/bin/env bash
# One-time certificate bootstrap. Usage: DOMAIN=api.localrush.in EMAIL=ops@localrush.in deploy/scripts/init-letsencrypt.sh
set -euo pipefail
: "${DOMAIN:?}" "${EMAIL:?}"
cd "$(dirname "$0")/.."
mkdir -p certbot/conf certbot/www
# temporary self-signed cert so nginx can start
docker run --rm -v "$PWD/certbot/conf:/etc/letsencrypt" --entrypoint sh certbot/certbot -c \
  "mkdir -p /etc/letsencrypt/live/$DOMAIN && openssl req -x509 -nodes -newkey rsa:2048 -days 1 -keyout /etc/letsencrypt/live/$DOMAIN/privkey.pem -out /etc/letsencrypt/live/$DOMAIN/fullchain.pem -subj '/CN=localhost'"
docker compose -f docker-compose.prod.yml up -d nginx
rm -rf "certbot/conf/live/$DOMAIN"
docker compose -f docker-compose.prod.yml run --rm --entrypoint certbot certbot certonly --webroot -w /var/www/certbot \
  -d "$DOMAIN" --email "$EMAIL" --agree-tos --no-eff-email --force-renewal
docker compose -f docker-compose.prod.yml exec nginx nginx -s reload
echo "certificate issued for $DOMAIN"
