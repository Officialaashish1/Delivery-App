from datetime import timedelta

from celery import shared_task
from django.utils import timezone

from apps.restaurants.models import Restaurant

from .services import build_settlement, rollup_platform_ledger


@shared_task(name="apps.finance.tasks.generate_daily_settlements")
def generate_daily_settlements():
    """Runs hourly; on Mondays builds last week's settlement per restaurant. Also rolls up yesterday's ledger."""
    today = timezone.localdate()
    rollup_platform_ledger(today - timedelta(days=1))
    rollup_platform_ledger(today)
    if today.weekday() != 0:
        return 0
    period_end = today - timedelta(days=1)
    period_start = period_end - timedelta(days=6)
    n = 0
    for r in Restaurant.objects.filter(settlement_lines__settlement__isnull=True).distinct():
        if build_settlement(r, period_start, period_end):
            n += 1
    return n
