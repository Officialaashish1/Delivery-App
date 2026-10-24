from django.contrib.gis.db.models.functions import Distance
from django.contrib.gis.geos import Point
from django.contrib.gis.measure import D
from django.db import transaction
from django.db.models import F, Q
from django.utils import timezone
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import generics, mixins, permissions, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import NotFound
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.core.audit import audit
from apps.core.exceptions import BusinessError
from apps.core.models import PlatformSettings
from apps.core.permissions import IsPartner

from . import serializers as s
from .filters import RestaurantFilter
from .models import (
    Banner,
    Cuisine,
    Restaurant,
    RestaurantCategory,
    RestaurantDocument,
    RestaurantHoliday,
    RestaurantStatus,
)

LOCATION_PARAMS = [
    OpenApiParameter("lat", float, description="Customer latitude"),
    OpenApiParameter("lng", float, description="Customer longitude"),
    OpenApiParameter("radius_km", float, description="Search radius (default: platform max)"),
    OpenApiParameter("sort", str, description="relevance | rating | delivery_time | distance | cost_low | cost_high"),
    OpenApiParameter("open_only", bool),
    OpenApiParameter("q", str), OpenApiParameter("cuisine", str), OpenApiParameter("category", str),
    OpenApiParameter("veg", bool), OpenApiParameter("min_rating", float),
]


def parse_latlng(request):
    lat, lng = request.query_params.get("lat"), request.query_params.get("lng")
    if lat is None or lng is None:
        return None
    try:
        lat, lng = float(lat), float(lng)
    except ValueError:
        raise BusinessError("Invalid lat/lng.", code="validation_error")
    if not (-90 <= lat <= 90 and -180 <= lng <= 180):
        raise BusinessError("Invalid lat/lng.", code="validation_error")
    return Point(lng, lat, srid=4326)


def discoverable_restaurants(request, annotate_distance=True):
    qs = Restaurant.objects.filter(status=RestaurantStatus.APPROVED)
    point = parse_latlng(request)
    if point is not None:
        radius = request.query_params.get("radius_km")
        radius = float(radius) if radius else float(PlatformSettings.load().max_delivery_radius_km)
        qs = qs.filter(location__dwithin=(point, D(km=radius)))
        if annotate_distance:
            qs = qs.annotate(distance=Distance("location", point))
    return qs, point


@extend_schema(tags=["restaurants"])
class RestaurantViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """Customer-facing discovery. Pass lat/lng for distance-aware results."""

    permission_classes = [permissions.AllowAny]
    filterset_class = RestaurantFilter
    lookup_field = "slug"

    def get_serializer_class(self):
        return s.RestaurantDetailSerializer if self.action == "retrieve" else s.RestaurantListSerializer

    def get_queryset(self):
        qs, point = discoverable_restaurants(self.request)
        qs = qs.prefetch_related("cuisines", "hours", "holidays")
        if self.action == "retrieve":
            return qs.prefetch_related("categories")
        if self.request.query_params.get("open_only") in ("1", "true", "True"):
            qs = qs.filter(is_open=True, is_accepting_orders=True).filter(
                Q(temporarily_closed_until__isnull=True) | Q(temporarily_closed_until__lt=timezone.now()))
        sort = self.request.query_params.get("sort", "relevance")
        if sort == "rating":
            qs = qs.order_by("-rating_avg", "-rating_count")
        elif sort == "distance" and point is not None:
            qs = qs.order_by("distance")
        elif sort == "delivery_time" and point is not None:
            qs = qs.order_by("distance", "avg_preparation_minutes")
        elif sort == "delivery_time":
            qs = qs.order_by("avg_preparation_minutes")
        elif sort == "cost_low":
            qs = qs.order_by("cost_for_two")
        elif sort == "cost_high":
            qs = qs.order_by("-cost_for_two")
        elif point is not None:
            qs = qs.order_by("-is_promoted", "-is_featured", "distance")
        return qs

    @extend_schema(parameters=LOCATION_PARAMS)
    def list(self, request, *args, **kwargs):
        return super().list(request, *args, **kwargs)

    def get_object(self):
        qs, _ = discoverable_restaurants(self.request)
        lookup = self.kwargs[self.lookup_field]
        obj = qs.filter(Q(slug=lookup) | Q(id=lookup) if _is_uuid(lookup) else Q(slug=lookup)).first()
        if obj is None:
            raise NotFound("Restaurant not found or outside delivery range.")
        return obj

    @extend_schema(parameters=LOCATION_PARAMS[:3], responses={200: dict})
    @action(detail=False, methods=["get"])
    def home(self, request):
        """Aggregated home-screen payload: banners, categories, cuisines, sections."""
        from django.core.cache import cache

        qs, point = discoverable_restaurants(request)
        qs = qs.prefetch_related("cuisines", "hours", "holidays")
        ctx = {"request": request}
        city = request.query_params.get("city", "")
        now = timezone.now()
        banners = Banner.objects.filter(is_active=True).filter(
            Q(starts_at__isnull=True) | Q(starts_at__lte=now)).filter(Q(ends_at__isnull=True) | Q(ends_at__gte=now))
        if city:
            banners = banners.filter(Q(city="") | Q(city__iexact=city))
        static_key = f"home:static:{city.lower()}"
        static = cache.get(static_key)
        if static is None:
            static = {
                "banners": s.BannerSerializer(banners[:10], many=True, context=ctx).data,
                "categories": s.RestaurantCategorySerializer(RestaurantCategory.objects.filter(is_active=True),
                                                             many=True, context=ctx).data,
                "cuisines": s.CuisineSerializer(Cuisine.objects.filter(is_active=True), many=True, context=ctx).data,
            }
            cache.set(static_key, static, 120)
        open_qs = qs.filter(is_open=True, is_accepting_orders=True)
        sections = []
        if point is not None:
            sections.append({"key": "nearby", "title": "Nearby restaurants",
                             "restaurants": s.RestaurantListSerializer(open_qs.order_by("distance")[:10], many=True,
                                                                       context=ctx).data})
            sections.append({"key": "fast", "title": "Fast delivery",
                             "restaurants": s.RestaurantListSerializer(
                                 open_qs.order_by("avg_preparation_minutes", "distance")[:10], many=True,
                                 context=ctx).data})
        sections.append({"key": "popular", "title": "Popular near you",
                         "restaurants": s.RestaurantListSerializer(open_qs.order_by("-total_orders")[:10], many=True,
                                                                   context=ctx).data})
        sections.append({"key": "top_rated", "title": "Top rated",
                         "restaurants": s.RestaurantListSerializer(
                             open_qs.filter(rating_count__gte=1).order_by("-rating_avg", "-rating_count")[:10],
                             many=True, context=ctx).data})
        featured = open_qs.filter(is_featured=True)[:10]
        if featured:
            sections.append({"key": "featured", "title": "Featured",
                             "restaurants": s.RestaurantListSerializer(featured, many=True, context=ctx).data})
        recent = []
        recommended = []
        if request.user.is_authenticated:
            from apps.menu.serializers import MenuItemSerializer
            from apps.orders.models import Order, OrderItem

            recent_orders = Order.objects.filter(customer=request.user).order_by("-created_at").values_list(
                "restaurant_id", flat=True)[:20]
            seen, ids = set(), []
            for rid in recent_orders:
                if rid not in seen:
                    seen.add(rid)
                    ids.append(rid)
            if ids:
                by_id = {r.id: r for r in qs.filter(id__in=ids)}
                recent = s.RestaurantListSerializer([by_id[i] for i in ids if i in by_id][:10], many=True,
                                                    context=ctx).data
            item_ids = list(OrderItem.objects.filter(order__customer=request.user).order_by("-order__created_at")
                            .values_list("menu_item_id", flat=True)[:30])
            from apps.menu.models import MenuItem

            items = MenuItem.objects.filter(id__in=item_ids, is_available=True, restaurant__in=open_qs)[:10]
            recommended = MenuItemSerializer(items, many=True, context=ctx).data
        return Response({
            **static,
            "sections": sections,
            "recently_ordered": recent,
            "recommended_items": recommended,
        })

    @extend_schema(parameters=LOCATION_PARAMS[:3] + [OpenApiParameter("q", str, required=True)], responses={200: dict})
    @action(detail=False, methods=["get"])
    def search(self, request):
        """Unified search across restaurants, dishes and cuisines."""
        from apps.menu.models import MenuItem
        from apps.menu.serializers import MenuItemSerializer

        q = (request.query_params.get("q") or "").strip()
        if len(q) < 2:
            return Response({"restaurants": [], "items": [], "cuisines": []})
        qs, _ = discoverable_restaurants(request)
        ctx = {"request": request}
        restaurants = RestaurantFilter({"q": q}, queryset=qs.prefetch_related("cuisines", "hours", "holidays")).qs[:20]
        items = MenuItem.objects.filter(restaurant__in=qs, is_available=True).filter(
            Q(name__icontains=q) | Q(description__icontains=q) | Q(category__name__icontains=q)
        ).select_related("restaurant", "category")[:30]
        cuisines = Cuisine.objects.filter(is_active=True, name__icontains=q)[:10]
        return Response({
            "restaurants": s.RestaurantListSerializer(restaurants, many=True, context=ctx).data,
            "items": MenuItemSerializer(items, many=True, context=ctx).data,
            "cuisines": s.CuisineSerializer(cuisines, many=True, context=ctx).data,
        })


def _is_uuid(value: str) -> bool:
    import uuid

    try:
        uuid.UUID(str(value))
        return True
    except ValueError:
        return False


class CuisineListView(generics.ListAPIView):
    permission_classes = [permissions.AllowAny]
    serializer_class = s.CuisineSerializer
    queryset = Cuisine.objects.filter(is_active=True)
    pagination_class = None


class CategoryListView(generics.ListAPIView):
    permission_classes = [permissions.AllowAny]
    serializer_class = s.RestaurantCategorySerializer
    queryset = RestaurantCategory.objects.filter(is_active=True)
    pagination_class = None


# ---------------------------------------------------------------- partner

def get_own_restaurant(request) -> Restaurant:
    r = Restaurant.objects.filter(owner=request.user).order_by("created_at").first()
    if r is None:
        raise NotFound("No restaurant registered for this account.")
    return r


@extend_schema(tags=["partner"])
class PartnerRestaurantView(APIView):
    """Partner app: register (POST), view (GET) and update (PATCH) the owner's restaurant."""

    permission_classes = [IsPartner]

    @extend_schema(responses=s.PartnerRestaurantSerializer)
    def get(self, request):
        return Response(s.PartnerRestaurantSerializer(get_own_restaurant(request), context={"request": request}).data)

    @extend_schema(request=s.PartnerRestaurantSerializer, responses=s.PartnerRestaurantSerializer)
    def post(self, request):
        if Restaurant.objects.filter(owner=request.user).exists():
            raise BusinessError("A restaurant is already registered for this account.", code="already_registered",
                                status_code=409)
        ser = s.PartnerRestaurantSerializer(data=request.data, context={"request": request})
        ser.is_valid(raise_exception=True)
        with transaction.atomic():
            r = ser.save()
            if not request.user.full_name:
                request.user.full_name = r.owner_name
                request.user.save(update_fields=["full_name"])
        audit("restaurant.registered", target=r)
        return Response(s.PartnerRestaurantSerializer(r, context={"request": request}).data,
                        status=status.HTTP_201_CREATED)

    @extend_schema(request=s.PartnerRestaurantSerializer, responses=s.PartnerRestaurantSerializer)
    def patch(self, request):
        r = get_own_restaurant(request)
        locked = {"bank_account_number", "bank_ifsc", "pan_number", "gst_number", "fssai_number"}
        if r.status == RestaurantStatus.APPROVED and locked & set(request.data.keys()):
            raise BusinessError("Compliance and bank details are locked after approval. Contact support.",
                                code="locked_fields")
        ser = s.PartnerRestaurantSerializer(r, data=request.data, partial=True, context={"request": request})
        ser.is_valid(raise_exception=True)
        ser.save()
        if r.status == RestaurantStatus.REJECTED:
            r.status = RestaurantStatus.PENDING
            r.rejection_reason = ""
            r.save(update_fields=["status", "rejection_reason"])
        return Response(ser.data)


@extend_schema(tags=["partner"])
class PartnerToggleView(APIView):
    permission_classes = [IsPartner]

    @extend_schema(request={"application/json": {"type": "object", "properties": {
        "is_open": {"type": "boolean"}, "is_accepting_orders": {"type": "boolean"},
        "temporarily_closed_until": {"type": "string", "format": "date-time", "nullable": True}}}},
        responses=s.PartnerRestaurantSerializer)
    def post(self, request):
        r = get_own_restaurant(request)
        if not r.is_approved and request.data.get("is_open"):
            raise BusinessError("Restaurant is not approved yet.", code="not_approved")
        for f in ("is_open", "is_accepting_orders"):
            if f in request.data:
                setattr(r, f, bool(request.data[f]))
        if "temporarily_closed_until" in request.data:
            v = request.data["temporarily_closed_until"]
            from django.utils.dateparse import parse_datetime

            r.temporarily_closed_until = parse_datetime(v) if v else None
        r.save(update_fields=["is_open", "is_accepting_orders", "temporarily_closed_until"])
        return Response(s.PartnerRestaurantSerializer(r, context={"request": request}).data)


@extend_schema(tags=["partner"])
class PartnerHoursView(APIView):
    permission_classes = [IsPartner]

    @extend_schema(request=s.HoursBulkSerializer, responses=s.RestaurantHoursSerializer(many=True))
    def put(self, request):
        r = get_own_restaurant(request)
        ser = s.HoursBulkSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        with transaction.atomic():
            r.hours.all().delete()
            objs = [r.hours.model(restaurant=r, **h) for h in ser.validated_data["hours"]]
            r.hours.bulk_create(objs)
        return Response(s.RestaurantHoursSerializer(r.hours.all(), many=True).data)


@extend_schema(tags=["partner"])
class PartnerHolidayViewSet(viewsets.ModelViewSet):
    permission_classes = [IsPartner]
    serializer_class = s.RestaurantHolidaySerializer
    pagination_class = None

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return RestaurantHoliday.objects.none()
        return get_own_restaurant(self.request).holidays.all()

    def perform_create(self, serializer):
        serializer.save(restaurant=get_own_restaurant(self.request))


@extend_schema(tags=["partner"])
class PartnerDocumentViewSet(mixins.ListModelMixin, mixins.CreateModelMixin, mixins.DestroyModelMixin,
                             viewsets.GenericViewSet):
    permission_classes = [IsPartner]
    serializer_class = s.RestaurantDocumentSerializer
    pagination_class = None

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return RestaurantDocument.objects.none()
        return RestaurantDocument.objects.filter(restaurant=get_own_restaurant(self.request))

    def perform_create(self, serializer):
        r = get_own_restaurant(self.request)
        RestaurantDocument.objects.filter(restaurant=r, doc_type=serializer.validated_data["doc_type"],
                                          status="rejected").delete()
        serializer.save(restaurant=r)
        if r.status == RestaurantStatus.REJECTED:
            Restaurant.objects.filter(pk=r.pk).update(status=RestaurantStatus.PENDING)

    def perform_destroy(self, instance):
        if instance.status == "verified":
            raise BusinessError("Verified documents cannot be removed.", code="locked")
        instance.delete()


@extend_schema(tags=["partner"], responses={200: dict})
class PartnerDashboardView(APIView):
    permission_classes = [IsPartner]

    def get(self, request):
        from apps.finance.services import partner_dashboard

        r = get_own_restaurant(request)
        return Response(partner_dashboard(r))


@extend_schema(tags=["partner"], responses={200: dict})
class PartnerBestSellersView(APIView):
    permission_classes = [IsPartner]

    def get(self, request):
        from apps.menu.serializers import MenuItemSerializer

        r = get_own_restaurant(request)
        items = r.menu_items.order_by(F("order_count").desc())[:10]
        return Response(MenuItemSerializer(items, many=True, context={"request": request}).data)
