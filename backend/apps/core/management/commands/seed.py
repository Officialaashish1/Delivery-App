"""
Seed reference data (idempotent): platform settings, food categories, cuisines, FAQs, and in DEBUG a dev admin.
    python manage.py seed            # reference data only
    python manage.py seed --demo     # + demo restaurants/menus/riders/customer for local testing (DEBUG only)
"""
from datetime import time
from decimal import Decimal

from django.conf import settings
from django.contrib.gis.geos import Point
from django.core.management.base import BaseCommand
from django.db import transaction

from apps.accounts.models import Address, CustomerProfile, User, UserRole
from apps.core.models import PlatformSettings
from apps.coupons.models import Coupon, DiscountType
from apps.menu.models import (
    AddonGroup,
    FoodType,
    MenuAddon,
    MenuCategory,
    MenuItem,
    MenuVariant,
    VariantGroup,
)
from apps.restaurants.models import Cuisine, Restaurant, RestaurantCategory, RestaurantHours, RestaurantStatus
from apps.riders.models import RiderProfile, RiderStatus
from apps.support.models import FAQ

CATEGORIES = ["Pizza", "Burger", "Biryani", "Chinese", "South Indian", "North Indian", "Fast Food", "Desserts",
              "Beverages", "Thali", "Bakery", "Healthy Food"]
CUISINES = ["North Indian", "South Indian", "Chinese", "Italian", "Continental", "Mughlai", "Gujarati", "Punjabi",
            "Street Food", "Desserts", "Beverages", "Bakery", "Healthy", "Biryani", "Pizza", "Burgers"]
FAQS = {
    "customer": [
        ("How do I track my order?", "Open the order from the Orders tab. You will see live rider location, ETA and each stage of your order."),
        ("Can I cancel my order?", "You can cancel for free until the restaurant accepts it. After acceptance a cancellation fee may apply as shown on the cancel screen."),
        ("When will I get my refund?", "Online refunds are initiated immediately and reach your account in 5-7 business days depending on your bank."),
        ("What is the delivery OTP?", "A 4-digit code shown on your tracking screen. Share it with the rider only when you receive the order."),
    ],
    "rider": [
        ("When do I get paid?", "Earnings are added to your wallet after each delivery. Request a payout any time from Earnings; payouts are processed within 2 business days."),
        ("Customer is not answering", "Call the customer twice and wait 5 minutes at the location. Then raise 'Customer unavailable' from the order screen so support can help."),
    ],
    "partner": [
        ("When are settlements paid?", "Settlements are generated every Monday for the previous week and paid to your bank account within 3 business days."),
        ("How do I pause orders?", "Use the Open/Closed toggle on the dashboard, or set a temporary closure time."),
    ],
}


class Command(BaseCommand):
    help = "Seed reference data (and optional demo data in DEBUG)"

    def add_arguments(self, parser):
        parser.add_argument("--demo", action="store_true")

    @transaction.atomic
    def handle(self, *args, **opts):
        PlatformSettings.load()
        for i, name in enumerate(CATEGORIES):
            RestaurantCategory.objects.get_or_create(name=name, defaults={"sort_order": i})
        for i, name in enumerate(CUISINES):
            Cuisine.objects.get_or_create(name=name, defaults={"sort_order": i})
        for audience, items in FAQS.items():
            for i, (q, a) in enumerate(items):
                FAQ.objects.get_or_create(audience=audience, question=q, defaults={"answer": a, "sort_order": i})
        if settings.DEBUG and not User.objects.filter(is_superuser=True).exists():
            User.objects.create_superuser(phone="+919999999999", password="admin12345", full_name="LocalRush Admin")
            self.stdout.write("Created dev superuser +919999999999 / admin12345")
        self.stdout.write(self.style.SUCCESS("Reference data seeded."))
        if opts["demo"]:
            if not settings.DEBUG:
                self.stderr.write("--demo is only allowed with DEBUG=true")
                return
            self.seed_demo()

    def seed_demo(self):
        # Surat (Gujarat) coordinates — near the user's reference screenshot location.
        base_lat, base_lng = 21.2300, 72.8700
        owner, _ = User.objects.get_or_create(phone="+919000000001", defaults={
            "role": UserRole.PARTNER, "full_name": "Rakesh Patel", "is_phone_verified": True})
        owner.set_password("partner12345")
        owner.save()
        owner2, _ = User.objects.get_or_create(phone="+919000000002", defaults={
            "role": UserRole.PARTNER, "full_name": "Meena Shah", "is_phone_verified": True})
        owner2.set_password("partner12345")
        owner2.save()
        cats = {c.name: c for c in RestaurantCategory.objects.all()}
        cuis = {c.name: c for c in Cuisine.objects.all()}
        demo = [
            (owner, "Spice Villa", "North Indian", ["North Indian", "Punjabi", "Mughlai"], ["North Indian", "Thali"], 0.004, 0.003, False,
             [("Starters", [("Paneer Tikka", 220, FoodType.VEG), ("Chicken Tikka", 260, FoodType.NON_VEG)]),
              ("Main Course", [("Dal Makhani", 190, FoodType.VEG), ("Butter Chicken", 320, FoodType.NON_VEG), ("Paneer Butter Masala", 240, FoodType.VEG)]),
              ("Breads", [("Butter Naan", 45, FoodType.VEG), ("Tandoori Roti", 25, FoodType.VEG)]),
              ("Thali", [("Gujarati Thali", 180, FoodType.VEG)])]),
            (owner2, "Pizza Junction", "Italian", ["Pizza", "Italian"], ["Pizza", "Fast Food"], -0.006, 0.005, True,
             [("Pizzas", [("Margherita", 199, FoodType.VEG), ("Farmhouse", 279, FoodType.VEG), ("Peppy Paneer", 299, FoodType.VEG)]),
              ("Sides", [("Garlic Bread", 99, FoodType.VEG), ("Cheesy Dip", 35, FoodType.VEG)]),
              ("Beverages", [("Cold Coffee", 120, FoodType.VEG), ("Lemon Iced Tea", 90, FoodType.VEG)])]),
        ]
        for own, name, _c, cuisine_names, cat_names, dlat, dlng, veg, menu in demo:
            r, created = Restaurant.objects.get_or_create(owner=own, name=name, defaults={
                "owner_name": own.full_name, "phone": own.phone, "address_line1": f"{name}, Lajamni Chowk",
                "address_line2": "Mota Varachha", "city": "Surat", "state": "Gujarat", "postal_code": "394101",
                "location": Point(base_lng + dlng, base_lat + dlat, srid=4326), "status": RestaurantStatus.APPROVED,
                "is_open": True, "is_pure_veg": veg, "fssai_number": "12345678901234", "cost_for_two": Decimal("400"),
                "avg_preparation_minutes": 20, "packaging_charge": Decimal("10"), "description": "Demo restaurant"})
            if created:
                r.cuisines.set([cuis[n] for n in cuisine_names if n in cuis])
                r.categories.set([cats[n] for n in cat_names if n in cats])
                RestaurantHours.objects.bulk_create([RestaurantHours(restaurant=r, weekday=d, open_time=time(0, 0),
                                                                     close_time=time(23, 59)) for d in range(7)])
                for ci, (cat_name, items) in enumerate(menu):
                    mc = MenuCategory.objects.create(restaurant=r, name=cat_name, sort_order=ci)
                    for ii, (iname, price, ft) in enumerate(items):
                        mi = MenuItem.objects.create(restaurant=r, category=mc, name=iname, price=Decimal(price),
                                                     food_type=ft, sort_order=ii, is_bestseller=ii == 0,
                                                     description=f"Delicious {iname.lower()} made fresh.")
                        if cat_name == "Pizzas":
                            vg = VariantGroup.objects.create(item=mi, name="Size")
                            MenuVariant.objects.bulk_create([
                                MenuVariant(group=vg, name="Regular", price=Decimal(price), is_default=True),
                                MenuVariant(group=vg, name="Medium", price=Decimal(price) + 120, sort_order=1),
                                MenuVariant(group=vg, name="Large", price=Decimal(price) + 240, sort_order=2)])
                            ag = AddonGroup.objects.create(item=mi, name="Extra toppings", max_select=3)
                            MenuAddon.objects.bulk_create([MenuAddon(group=ag, name="Extra cheese", price=Decimal(40)),
                                                           MenuAddon(group=ag, name="Olives", price=Decimal(30)),
                                                           MenuAddon(group=ag, name="Jalapeños", price=Decimal(30))])
        for i, (phone, name) in enumerate([("+919100000001", "Amit Rider"), ("+919100000002", "Suresh Rider")]):
            u, _ = User.objects.get_or_create(phone=phone, defaults={"role": UserRole.RIDER, "full_name": name,
                                                                     "is_phone_verified": True})
            RiderProfile.objects.update_or_create(user=u, defaults={
                "full_name": name, "city": "Surat", "vehicle_type": "bike", "vehicle_number": f"GJ05AB{1000 + i}",
                "driving_licence_number": f"GJ05 2019000{i}", "status": RiderStatus.APPROVED,
                "bank_account_name": name, "bank_account_number": "1234567890", "bank_ifsc": "HDFC0000001",
                "current_location": Point(base_lng + 0.002 * i, base_lat - 0.002, srid=4326)})
        cu, _ = User.objects.get_or_create(phone="+919200000001", defaults={
            "role": UserRole.CUSTOMER, "full_name": "Priya Customer", "is_phone_verified": True})
        CustomerProfile.objects.get_or_create(user=cu)
        Address.objects.get_or_create(user=cu, address_line1="6VQQ+JP2, Lajamni Chowk", defaults={
            "address_line2": "Dwarkesh Nagari, Mota Varachha", "city": "Surat", "state": "Gujarat",
            "postal_code": "394101", "location": Point(base_lng + 0.01, base_lat + 0.008, srid=4326),
            "is_default": True})
        Coupon.objects.get_or_create(code="WELCOME50", defaults={
            "title": "50% off your first order", "discount_type": DiscountType.PERCENT, "value": Decimal("50"),
            "max_discount": Decimal("100"), "min_order_amount": Decimal("199"), "first_order_only": True})
        Coupon.objects.get_or_create(code="FREEDEL", defaults={
            "title": "Free delivery", "discount_type": DiscountType.FREE_DELIVERY, "value": Decimal("0"),
            "min_order_amount": Decimal("299"), "usage_limit_per_user": 5})
        self.stdout.write(self.style.SUCCESS(
            "Demo data: partners +919000000001/+919000000002 (password partner12345), riders +919100000001/2, "
            "customer +919200000001. OTP in dev is 123456."))
