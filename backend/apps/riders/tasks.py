from datetime import timedelta

from celery import shared_task
from django.utils import timezone

from .models import RiderProfile


@shared_task(name="apps.riders.tasks.mark_stale_riders_offline")
def mark_stale_riders_offline():
    """Riders whose app stopped sending locations for 10 minutes are marked offline (not while delivering)."""
    cutoff = timezone.now() - timedelta(minutes=10)
    return RiderProfile.objects.filter(is_online=True, is_on_delivery=False, location_updated_at__lt=cutoff).update(
        is_online=False)
