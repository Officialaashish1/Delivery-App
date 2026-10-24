#!/usr/bin/env bash
# Download the OSM extract that Nominatim imports on first start. Import of a state-sized extract takes 30-90 min.
#   deploy/scripts/prepare-nominatim.sh https://download.geofabrik.de/asia/india/western-zone-latest.osm.pbf
set -euo pipefail
PBF_URL="${1:?usage: prepare-nominatim.sh <osm.pbf URL>}"
DIR="$(cd "$(dirname "$0")/../nominatim/data" && pwd)"
mkdir -p "$DIR"
docker run --rm -v "$DIR:/data" alpine:3.20 sh -c "apk add --no-cache wget >/dev/null && wget -q -O /data/region.osm.pbf '$PBF_URL'"
echo "Placed $DIR/region.osm.pbf. First 'docker compose up nominatim' will import it (watch logs). Set REPLICATION_URL for updates."
