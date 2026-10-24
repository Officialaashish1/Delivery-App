"""
OSRM client. Expects a self-hosted OSRM instance (deploy/osrm). Returns distance in metres,
duration in seconds and an encoded polyline (precision 5) plus turn-by-turn steps.
ETA here is traffic-independent: OSRM uses OSM speed profiles, not live traffic.
"""
import logging
from dataclasses import dataclass, field

import requests
from django.conf import settings
from django.core.cache import cache

from apps.core.geo import haversine_km

log = logging.getLogger("localrush.routing")


@dataclass
class RouteStep:
    instruction: str
    name: str
    distance_m: float
    duration_s: float
    maneuver_type: str
    modifier: str
    location: list  # [lng, lat]


@dataclass
class RouteResult:
    distance_m: float
    duration_s: float
    geometry: str  # encoded polyline5
    steps: list[RouteStep] = field(default_factory=list)
    source: str = "osrm"

    @property
    def distance_km(self):
        return round(self.distance_m / 1000, 2)

    @property
    def duration_min(self):
        return max(1, int(round(self.duration_s / 60)))

    def as_dict(self, include_steps=False):
        d = {
            "distance_m": round(self.distance_m, 1),
            "duration_s": round(self.duration_s, 1),
            "distance_km": self.distance_km,
            "duration_min": self.duration_min,
            "geometry": self.geometry,
            "source": self.source,
        }
        if include_steps:
            d["steps"] = [s.__dict__ for s in self.steps]
        return d


class RoutingService:
    def __init__(self, base_url=None, timeout=4.0):
        self.base_url = (base_url or settings.LOCALRUSH["OSRM_BASE_URL"]).rstrip("/")
        self.timeout = timeout

    def route(self, points: list[tuple[float, float]], steps=False, use_cache=True) -> RouteResult:
        """points: list of (lat, lng). Falls back to a straight-line estimate if OSRM is unreachable."""
        coords = ";".join(f"{lng:.6f},{lat:.6f}" for lat, lng in points)
        cache_key = f"osrm:{coords}:{int(steps)}"
        if use_cache:
            cached = cache.get(cache_key)
            if cached:
                return cached
        url = f"{self.base_url}/route/v1/driving/{coords}"
        params = {"overview": "full", "geometries": "polyline", "steps": "true" if steps else "false"}
        try:
            resp = requests.get(url, params=params, timeout=self.timeout)
            resp.raise_for_status()
            data = resp.json()
            if data.get("code") != "Ok" or not data.get("routes"):
                raise ValueError(data.get("code", "NoRoute"))
            r = data["routes"][0]
            result = RouteResult(distance_m=r["distance"], duration_s=r["duration"], geometry=r["geometry"])
            if steps:
                for leg in r.get("legs", []):
                    for s in leg.get("steps", []):
                        m = s.get("maneuver", {})
                        result.steps.append(
                            RouteStep(
                                instruction=self._instruction(m, s.get("name", "")),
                                name=s.get("name", ""),
                                distance_m=s.get("distance", 0),
                                duration_s=s.get("duration", 0),
                                maneuver_type=m.get("type", ""),
                                modifier=m.get("modifier", ""),
                                location=m.get("location", []),
                            )
                        )
            if use_cache:
                cache.set(cache_key, result, 120)
            return result
        except Exception as exc:  # noqa: BLE001
            log.warning("OSRM unavailable (%s); using straight-line fallback", exc)
            return self.fallback(points)

    @staticmethod
    def fallback(points) -> RouteResult:
        total_km = 0.0
        for (a_lat, a_lng), (b_lat, b_lng) in zip(points, points[1:], strict=False):
            total_km += haversine_km(a_lat, a_lng, b_lat, b_lng)
        road_km = total_km * 1.3  # road winding factor
        duration_s = road_km / 22.0 * 3600  # ~22 km/h urban two-wheeler average
        return RouteResult(distance_m=road_km * 1000, duration_s=duration_s,
                           geometry=encode_polyline(points), source="estimate")

    @staticmethod
    def _instruction(maneuver: dict, name: str) -> str:
        t = maneuver.get("type", "")
        mod = maneuver.get("modifier", "")
        road = f" onto {name}" if name else ""
        mapping = {
            "depart": f"Head {mod}{road}".strip(),
            "arrive": "You have arrived at your destination",
            "turn": f"Turn {mod}{road}",
            "new name": f"Continue {mod}{road}",
            "continue": f"Continue {mod}{road}",
            "merge": f"Merge {mod}{road}",
            "on ramp": f"Take the ramp {mod}{road}",
            "off ramp": f"Take the exit {mod}{road}",
            "fork": f"Keep {mod} at the fork{road}",
            "end of road": f"Turn {mod} at the end of the road{road}",
            "roundabout": f"Enter the roundabout and take exit {maneuver.get('exit', '')}{road}",
            "rotary": f"Enter the rotary{road}",
            "roundabout turn": f"At the roundabout turn {mod}{road}",
            "exit roundabout": f"Exit the roundabout{road}",
        }
        return mapping.get(t, f"{t.title()} {mod}{road}".strip())


def encode_polyline(points: list[tuple[float, float]], precision=5) -> str:
    """Google polyline encoding of (lat, lng) pairs."""
    factor = 10**precision
    output = []
    prev_lat = prev_lng = 0
    for lat, lng in points:
        ilat, ilng = int(round(lat * factor)), int(round(lng * factor))
        for v in (ilat - prev_lat, ilng - prev_lng):
            v = ~(v << 1) if v < 0 else v << 1
            while v >= 0x20:
                output.append(chr((0x20 | (v & 0x1F)) + 63))
                v >>= 5
            output.append(chr(v + 63))
        prev_lat, prev_lng = ilat, ilng
    return "".join(output)


def decode_polyline(encoded: str, precision=5) -> list[tuple[float, float]]:
    factor = 10**precision
    coords, index, lat, lng = [], 0, 0, 0
    while index < len(encoded):
        for which in ("lat", "lng"):
            shift = result = 0
            while True:
                b = ord(encoded[index]) - 63
                index += 1
                result |= (b & 0x1F) << shift
                shift += 5
                if b < 0x20:
                    break
            delta = ~(result >> 1) if result & 1 else result >> 1
            if which == "lat":
                lat += delta
            else:
                lng += delta
        coords.append((lat / factor, lng / factor))
    return coords


routing_service = RoutingService()
