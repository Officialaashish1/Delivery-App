import uuid

from django.conf import settings
from django.core.validators import MaxValueValidator, MinValueValidator
from django.db import models

from apps.core.models import TimeStampedModel


class Review(TimeStampedModel):
    """One review per order: a restaurant rating and an optional rider rating."""

    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    order = models.OneToOneField("orders.Order", on_delete=models.CASCADE, related_name="review")
    customer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name="reviews")
    restaurant = models.ForeignKey("restaurants.Restaurant", on_delete=models.CASCADE, related_name="reviews")
    rider = models.ForeignKey("riders.RiderProfile", null=True, blank=True, on_delete=models.SET_NULL,
                              related_name="reviews")
    restaurant_rating = models.PositiveSmallIntegerField(validators=[MinValueValidator(1), MaxValueValidator(5)])
    restaurant_comment = models.TextField(blank=True, default="")
    rider_rating = models.PositiveSmallIntegerField(null=True, blank=True,
                                                    validators=[MinValueValidator(1), MaxValueValidator(5)])
    rider_comment = models.TextField(blank=True, default="")
    item_ratings = models.JSONField(default=dict, blank=True, help_text="{menu_item_id: 1-5}")
    tags = models.JSONField(default=list, blank=True)
    is_visible = models.BooleanField(default=True)
    partner_reply = models.TextField(blank=True, default="")
    partner_replied_at = models.DateTimeField(null=True, blank=True)

    class Meta:
        ordering = ["-created_at"]
        indexes = [models.Index(fields=["restaurant", "-created_at"])]
