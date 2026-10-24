"""Nominatim client (self-hosted). Provides address search and reverse geocoding."""
import logging

import requests
from django.conf import settings
from django.core.cache import cache

log = logging.getLogger("localrush.geocoding")


class GeocodingService:
    def __init__(self, base_url=None, timeout=4.0):
        self.base_url = (base_url or settings.LOCALRUSH["NOMINATIM_BASE_URL"]).rstrip("/")
        self.timeout = timeout
        self.headers = {"User-Agent": "LocalRush/1.0 (self-hosted nominatim)"}

    def search(self, query: str, lat=None, lng=None, limit=8, country="in") -> list[dict]:
        key = f"geo:s:{country}:{query.lower().strip()}:{lat}:{lng}:{limit}"
        cached = cache.get(key)
        if cached is not None:
            return cached
        params = {"q": query, "format": "jsonv2", "addressdetails": 1, "limit": limit, "countrycodes": country}
        if lat is not None and lng is not None:
            d = 0.25
            params["viewbox"] = f"{lng - d},{lat + d},{lng + d},{lat - d}"
            params["bounded"] = 0
        try:
            r = requests.get(f"{self.base_url}/search", params=params, headers=self.headers, timeout=self.timeout)
            r.raise_for_status()
            results = [self._normalise(x) for x in r.json()]
        except Exception as exc:  # noqa: BLE001
            log.warning("Nominatim search failed: %s", exc)
            results = []
        cache.set(key, results, 600)
        return results

    def reverse(self, lat: float, lng: float) -> dict | None:
        key = f"geo:r:{round(lat, 5)}:{round(lng, 5)}"
        cached = cache.get(key)
        if cached is not None:
            return cached
        params = {"lat": lat, "lon": lng, "format": "jsonv2", "addressdetails": 1, "zoom": 18}
        try:
            r = requests.get(f"{self.base_url}/reverse", params=params, headers=self.headers, timeout=self.timeout)
            r.raise_for_status()
            data = r.json()
            result = self._normalise(data) if "error" not in data else None
        except Exception as exc:  # noqa: BLE001
            log.warning("Nominatim reverse failed: %s", exc)
            result = None
        cache.set(key, result, 3600)
        return result

    @staticmethod
    def _normalise(item: dict) -> dict:
        addr = item.get("address", {}) or {}
        return {
            "place_id": item.get("place_id"),
            "display_name": item.get("display_name", ""),
            "latitude": float(item["lat"]),
            "longitude": float(item["lon"]),
            "name": item.get("name") or addr.get("amenity") or addr.get("road") or "",
            "road": addr.get("road", ""),
            "locality": addr.get("suburb") or addr.get("neighbourhood") or addr.get("village") or "",
            "city": addr.get("city") or addr.get("town") or addr.get("state_district") or "",
            "state": addr.get("state", ""),
            "postcode": addr.get("postcode", ""),
            "country": addr.get("country", ""),
        }


geocoding_service = GeocodingService()
