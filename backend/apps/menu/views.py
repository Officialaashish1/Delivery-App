from django.db import transaction
from drf_spectacular.utils import extend_schema
from rest_framework import generics, permissions, status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response

from apps.core.exceptions import BusinessError
from apps.core.permissions import IsPartner
from apps.restaurants.views import get_own_restaurant

from . import serializers as s
from .models import MenuCategory, MenuItem


@extend_schema(tags=["menu"])
class MenuItemDetailView(generics.RetrieveAPIView):
    permission_classes = [permissions.AllowAny]
    serializer_class = s.MenuItemSerializer
    queryset = MenuItem.objects.select_related("restaurant", "category").prefetch_related(
        "variant_groups__options", "addon_groups__options")


@extend_schema(tags=["partner"])
class PartnerMenuCategoryViewSet(viewsets.ModelViewSet):
    permission_classes = [IsPartner]
    serializer_class = s.PartnerMenuCategorySerializer
    pagination_class = None

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return MenuCategory.objects.none()
        return MenuCategory.objects.filter(restaurant=get_own_restaurant(self.request))

    def perform_create(self, serializer):
        serializer.save(restaurant=get_own_restaurant(self.request))

    def perform_destroy(self, instance):
        if instance.items.exists():
            raise BusinessError("Move or delete the items in this category first.", code="category_not_empty")
        instance.delete()

    @action(detail=False, methods=["post"])
    def reorder(self, request):
        ids = request.data.get("ids", [])
        with transaction.atomic():
            for i, cid in enumerate(ids):
                self.get_queryset().filter(id=cid).update(sort_order=i)
        return Response(self.get_serializer(self.get_queryset(), many=True).data)


@extend_schema(tags=["partner"])
class PartnerMenuItemViewSet(viewsets.ModelViewSet):
    permission_classes = [IsPartner]
    serializer_class = s.PartnerMenuItemSerializer
    filterset_fields = ("category", "is_available", "food_type")
    search_fields = ("name",)

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return MenuItem.objects.none()
        qs = MenuItem.objects.filter(restaurant=get_own_restaurant(self.request)).select_related("category")
        q = self.request.query_params.get("q")
        if q:
            qs = qs.filter(name__icontains=q)
        return qs.prefetch_related("variant_groups__options", "addon_groups__options")

    def get_serializer_context(self):
        ctx = super().get_serializer_context()
        ctx["restaurant"] = get_own_restaurant(self.request)
        return ctx

    @action(detail=True, methods=["post"])
    def availability(self, request, pk=None):
        item = self.get_object()
        item.is_available = bool(request.data.get("is_available", not item.is_available))
        if "stock_quantity" in request.data:
            v = request.data["stock_quantity"]
            item.stock_quantity = int(v) if v not in (None, "") else None
        item.save(update_fields=["is_available", "stock_quantity"])
        return Response(self.get_serializer(item).data)

    @action(detail=False, methods=["post"])
    def bulk_availability(self, request):
        ids = request.data.get("ids", [])
        available = bool(request.data.get("is_available", True))
        n = self.get_queryset().filter(id__in=ids).update(is_available=available)
        return Response({"updated": n}, status=status.HTTP_200_OK)
