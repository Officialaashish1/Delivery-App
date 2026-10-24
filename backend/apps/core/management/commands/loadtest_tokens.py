"""
DEBUG-only: create N load-test customers and riders (approved, online, located near the demo city) and write their
JWT access tokens to a JSON file for backend/loadtests/locustfile.py. Never runs when DEBUG is false.
    python manage.py loadtest_tokens --customers 200 --riders 50 --out loadtests/tokens.json
"""
import json

from django.conf import settings
from django.contrib.gis.geos import Point
from django.core.management.base import BaseCommand, CommandError
from django.utils import timezone

from apps.accounts.authentication import issue_tokens
from apps.accounts.models import Address, CustomerProfile, User, UserRole
from apps.riders.models import RiderProfile, RiderStatus


class Command(BaseCommand):
    def add_arguments(self, parser):
        parser.add_argument("--customers", type=int, default=100)
        parser.add_argument("--riders", type=int, default=20)
        parser.add_argument("--out", default="loadtests/tokens.json")
        parser.add_argument("--lat", type=float, default=21.23)
        parser.add_argument("--lng", type=float, default=72.87)

    def handle(self, *args, **o):
        if not settings.DEBUG:
            raise CommandError("loadtest_tokens is only available with DEBUG=true")
        customers, riders = [], []
        for i in range(o["customers"]):
            phone = f"+9196{i:08d}"
            u, created = User.objects.get_or_create(phone=phone, defaults={
                "role": UserRole.CUSTOMER, "full_name": f"Load Customer {i}", "is_phone_verified": True})
            if created:
                CustomerProfile.objects.create(user=u)
                Address.objects.create(user=u, address_line1=f"Load street {i}", city="Surat", is_default=True,
                                       location=Point(o["lng"] + (i % 20) * 0.001, o["lat"] + (i % 13) * 0.001,
                                                      srid=4326))
            customers.append(issue_tokens(u)["access"])
        for i in range(o["riders"]):
            phone = f"+9197{i:08d}"
            u, _ = User.objects.get_or_create(phone=phone, defaults={
                "role": UserRole.RIDER, "full_name": f"Load Rider {i}", "is_phone_verified": True})
            RiderProfile.objects.update_or_create(user=u, defaults={
                "full_name": f"Load Rider {i}", "city": "Surat", "vehicle_number": f"GJ05LT{i:04d}",
                "status": RiderStatus.APPROVED, "is_online": True, "location_updated_at": timezone.now(),
                "current_location": Point(o["lng"] + (i % 10) * 0.002, o["lat"] - (i % 7) * 0.002, srid=4326)})
            riders.append(issue_tokens(u)["access"])
        with open(o["out"], "w") as f:
            json.dump({"customers": customers, "riders": riders}, f)
        self.stdout.write(self.style.SUCCESS(f"wrote {len(customers)} customer + {len(riders)} rider tokens to {o['out']}"))
