import django_filters
from django.db.models import Q

from .models import Restaurant


class RestaurantFilter(django_filters.FilterSet):
    q = django_filters.CharFilter(method="filter_q")
    cuisine = django_filters.CharFilter(field_name="cuisines__slug")
    category = django_filters.CharFilter(field_name="categories__slug")
    veg = django_filters.BooleanFilter(field_name="is_pure_veg")
    min_rating = django_filters.NumberFilter(field_name="rating_avg", lookup_expr="gte")
    city = django_filters.CharFilter(field_name="city", lookup_expr="iexact")

    class Meta:
        model = Restaurant
        fields = ["cuisine", "category", "veg", "min_rating", "city"]

    def filter_q(self, qs, name, value):
        value = value.strip()
        if not value:
            return qs
        return qs.filter(
            Q(name__icontains=value) | Q(cuisines__name__icontains=value)
            | Q(menu_items__name__icontains=value, menu_items__is_available=True)
            | Q(categories__name__icontains=value)
        ).distinct()
