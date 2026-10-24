from django.apps import AppConfig


class RestaurantsConfig(AppConfig):
    name = "apps.restaurants"

    def ready(self):
        from django.core.cache import cache
        from django.db.models.signals import post_delete, post_save

        from .models import Banner, Cuisine, RestaurantCategory

        def _clear(**kwargs):
            cache.delete_pattern("lr:home:static:*") if hasattr(cache, "delete_pattern") else cache.clear()

        for model in (Banner, Cuisine, RestaurantCategory):
            post_save.connect(_clear, sender=model, weak=False, dispatch_uid=f"home_static_{model.__name__}_s")
            post_delete.connect(_clear, sender=model, weak=False, dispatch_uid=f"home_static_{model.__name__}_d")
