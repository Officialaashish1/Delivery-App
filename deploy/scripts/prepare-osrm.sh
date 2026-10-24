#!/usr/bin/env bash
# Build the OSRM routing graph for a region. Run once (and again whenever you refresh OSM data).
#   deploy/scripts/prepare-osrm.sh https://download.geofabrik.de/asia/india/western-zone-latest.osm.pbf
# Smaller = faster. For one city, clip the PBF first with osmium (see docs/DEPLOYMENT.md).
set -euo pipefail
PBF_URL="${1:?usage: prepare-osrm.sh <osm.pbf URL or local path>}"
DIR="$(cd "$(dirname "$0")/../osrm/data" && pwd)"
IMG=ghcr.io/project-osrm/osrm-backend:v5.27.1
mkdir -p "$DIR"
if [[ "$PBF_URL" == http* ]]; then
  echo "Downloading $PBF_URL ..."
  docker run --rm -v "$DIR:/data" alpine:3.20 sh -c "apk add --no-cache wget >/dev/null && wget -q -O /data/region.osm.pbf '$PBF_URL'"
else
  cp "$PBF_URL" "$DIR/region.osm.pbf"
fi
# Two-wheeler friendly profile: start from the bicycle/car profile. Car profile is a good default for scooters in Indian cities.
docker run --rm -t -v "$DIR:/data" $IMG osrm-extract -p /opt/car.lua /data/region.osm.pbf
docker run --rm -t -v "$DIR:/data" $IMG osrm-partition /data/region.osrm
docker run --rm -t -v "$DIR:/data" $IMG osrm-customize /data/region.osrm
echo "OSRM graph ready in $DIR. Start with: docker compose --profile maps up -d osrm   (dev)  or the prod compose."
