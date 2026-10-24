from django.db import transaction
from django.db.models import Avg, Count

from apps.core.exceptions import BusinessError, ConflictError
from apps.core.money import q
from apps.menu.models import MenuItem
from apps.orders.models import Order, OrderStatus
from apps.restaurants.models import Restaurant
from apps.riders.models import RiderProfile

from .models import Review


@transaction.atomic
def create_review(user, order_id, **data) -> Review:
    order = Order.objects.select_for_update().filter(pk=order_id, customer=user).first()
    if order is None:
        raise BusinessError("Order not found.", code="not_found", status_code=404)
    if order.status != OrderStatus.DELIVERED:
        raise BusinessError("You can review an order after it is delivered.", code="not_delivered")
    if hasattr(order, "review"):
        raise ConflictError("This order is already reviewed.", code="already_reviewed")
    item_ratings = data.pop("item_ratings", {}) or {}
    valid_ids = set(str(i) for i in order.items.values_list("menu_item_id", flat=True) if i)
    item_ratings = {k: int(v) for k, v in item_ratings.items() if k in valid_ids and 1 <= int(v) <= 5}
    review = Review.objects.create(order=order, customer=user, restaurant=order.restaurant, rider=order.rider,
                                   item_ratings=item_ratings, **data)
    order.is_reviewed = True
    order.save(update_fields=["is_reviewed"])
    recompute_restaurant_rating(order.restaurant_id)
    if order.rider_id and review.rider_rating:
        recompute_rider_rating(order.rider_id)
    for mid, rating in item_ratings.items():
        _bump_item_rating(mid, rating)
    return review


def recompute_restaurant_rating(restaurant_id):
    agg = Review.objects.filter(restaurant_id=restaurant_id, is_visible=True).aggregate(
        avg=Avg("restaurant_rating"), n=Count("id"))
    Restaurant.objects.filter(pk=restaurant_id).update(rating_avg=q(agg["avg"] or 0), rating_count=agg["n"])


def recompute_rider_rating(rider_id):
    agg = Review.objects.filter(rider_id=rider_id, rider_rating__isnull=False).aggregate(
        avg=Avg("rider_rating"), n=Count("id"))
    RiderProfile.objects.filter(pk=rider_id).update(rating_avg=q(agg["avg"] or 0), rating_count=agg["n"])


def _bump_item_rating(menu_item_id, rating):
    item = MenuItem.objects.filter(pk=menu_item_id).first()
    if item is None:
        return
    total = item.rating_avg * item.rating_count + rating
    item.rating_count += 1
    item.rating_avg = q(total / item.rating_count)
    item.save(update_fields=["rating_avg", "rating_count"])
