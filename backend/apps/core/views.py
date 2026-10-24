from django.conf import settings
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import permissions, serializers
from rest_framework.response import Response
from rest_framework.throttling import ScopedRateThrottle
from rest_framework.views import APIView

from .models import PlatformSettings
from .services.geocoding import geocoding_service


class AppConfigSerializer(serializers.Serializer):
    map_style_url = serializers.CharField()
    map_attribution = serializers.CharField()
    currency = serializers.CharField()
    cod_enabled = serializers.BooleanField()
    cod_max_order_value = serializers.DecimalField(max_digits=12, decimal_places=2)
    min_app_version_customer = serializers.CharField()
    min_app_version_rider = serializers.CharField()
    min_app_version_partner = serializers.CharField()
    support_phone = serializers.CharField()
    support_email = serializers.CharField()
    payment_provider = serializers.CharField()
    razorpay_key_id = serializers.CharField()
    location_interval_seconds = serializers.IntegerField()


class AppConfigView(APIView):
    """Public runtime configuration consumed by all three Android apps on launch."""

    permission_classes = [permissions.AllowAny]
    authentication_classes = []

    @extend_schema(tags=["config"], responses=AppConfigSerializer)
    def get(self, request):
        ps = PlatformSettings.load()
        lr = settings.LOCALRUSH
        return Response({
            "map_style_url": lr["MAP_STYLE_URL"],
            "map_attribution": lr["MAP_ATTRIBUTION"],
            "currency": lr["CURRENCY"],
            "cod_enabled": ps.cod_enabled,
            "cod_max_order_value": str(ps.cod_max_order_value),
            "min_app_version_customer": ps.min_app_version_customer,
            "min_app_version_rider": ps.min_app_version_rider,
            "min_app_version_partner": ps.min_app_version_partner,
            "support_phone": ps.support_phone,
            "support_email": ps.support_email,
            "payment_provider": lr["PAYMENT_PROVIDER"],
            "razorpay_key_id": lr["RAZORPAY_KEY_ID"],  # public key id only; the secret never leaves the server
            "location_interval_seconds": 4,
        })


class GeocodeResultSerializer(serializers.Serializer):
    place_id = serializers.IntegerField(allow_null=True)
    display_name = serializers.CharField()
    latitude = serializers.FloatField()
    longitude = serializers.FloatField()
    name = serializers.CharField()
    road = serializers.CharField()
    locality = serializers.CharField()
    city = serializers.CharField()
    state = serializers.CharField()
    postcode = serializers.CharField()
    country = serializers.CharField()


class GeocodeSearchView(APIView):
    permission_classes = [permissions.AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "geo"

    @extend_schema(tags=["config"], parameters=[
        OpenApiParameter("q", str, required=True), OpenApiParameter("lat", float), OpenApiParameter("lng", float)],
        responses=GeocodeResultSerializer(many=True))
    def get(self, request):
        q = (request.query_params.get("q") or "").strip()
        if len(q) < 3:
            return Response([])
        lat = request.query_params.get("lat")
        lng = request.query_params.get("lng")
        results = geocoding_service.search(q, float(lat) if lat else None, float(lng) if lng else None)
        return Response(results)


class GeocodeReverseView(APIView):
    permission_classes = [permissions.AllowAny]
    throttle_classes = [ScopedRateThrottle]
    throttle_scope = "geo"

    @extend_schema(tags=["config"], parameters=[
        OpenApiParameter("lat", float, required=True), OpenApiParameter("lng", float, required=True)],
        responses=GeocodeResultSerializer)
    def get(self, request):
        try:
            lat, lng = float(request.query_params["lat"]), float(request.query_params["lng"])
        except (KeyError, ValueError):
            return Response({"error": {"code": "validation_error", "message": "lat and lng are required"}}, status=400)
        result = geocoding_service.reverse(lat, lng)
        if result is None:
            return Response({"error": {"code": "not_found", "message": "No address found"}}, status=404)
        return Response(result)
