import math

from django.contrib.gis.geos import Point

EARTH_RADIUS_KM = 6371.0088


def make_point(lat: float, lng: float) -> Point:
    return Point(float(lng), float(lat), srid=4326)


def haversine_km(lat1, lng1, lat2, lng2) -> float:
    p1, p2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dl = math.radians(lng2 - lng1)
    a = math.sin(dphi / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
    return 2 * EARTH_RADIUS_KM * math.asin(math.sqrt(a))


def point_to_latlng(point):
    if point is None:
        return None
    return {"latitude": point.y, "longitude": point.x}
