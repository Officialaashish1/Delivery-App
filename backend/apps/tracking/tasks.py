from celery import shared_task
from django.contrib.gis.geos import Point
from django.utils.dateparse import parse_datetime

from apps.riders.models import RiderLocation

from . import store


@shared_task(name="apps.tracking.tasks.flush_location_history")
def flush_location_history():
    """Move sampled GPS points from the Redis buffer into PostgreSQL in one bulk insert."""
    entries = store.drain_history()
    if not entries:
        return 0
    rows = [RiderLocation(rider_id=e["rider_id"], order_id=e.get("order_id"),
                          location=Point(e["lng"], e["lat"], srid=4326), accuracy_m=e.get("accuracy", 0),
                          speed_kmph=e.get("speed", 0), bearing=e.get("bearing", 0),
                          recorded_at=parse_datetime(e["recorded_at"])) for e in entries]
    RiderLocation.objects.bulk_create(rows, batch_size=1000, ignore_conflicts=True)
    return len(rows)
