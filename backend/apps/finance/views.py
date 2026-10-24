import csv

from django.http import HttpResponse
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import generics
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.core.permissions import IsPartner
from apps.restaurants.views import get_own_restaurant

from .models import RestaurantSettlement, SettlementLine
from .serializers import SettlementLineSerializer, SettlementSerializer
from .services import partner_finance_summary


@extend_schema(tags=["partner"], parameters=[OpenApiParameter("date_from", str), OpenApiParameter("date_to", str)],
               responses={200: dict})
class PartnerFinanceSummaryView(APIView):
    permission_classes = [IsPartner]

    def get(self, request):
        r = get_own_restaurant(request)
        return Response(partner_finance_summary(r, request.query_params.get("date_from"),
                                                request.query_params.get("date_to")))


@extend_schema(tags=["partner"])
class PartnerSettlementLineListView(generics.ListAPIView):
    permission_classes = [IsPartner]
    serializer_class = SettlementLineSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return SettlementLine.objects.none()
        qs = SettlementLine.objects.filter(restaurant=get_own_restaurant(self.request)).select_related("order")
        if self.request.query_params.get("unsettled") in ("1", "true"):
            qs = qs.filter(settlement__isnull=True)
        sid = self.request.query_params.get("settlement")
        if sid:
            qs = qs.filter(settlement_id=sid)
        return qs


@extend_schema(tags=["partner"])
class PartnerSettlementListView(generics.ListAPIView):
    permission_classes = [IsPartner]
    serializer_class = SettlementSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return RestaurantSettlement.objects.none()
        return RestaurantSettlement.objects.filter(restaurant=get_own_restaurant(self.request))


@extend_schema(tags=["partner"])
class PartnerStatementCSVView(APIView):
    """Download order-wise statement as CSV for the given period."""

    permission_classes = [IsPartner]

    def get(self, request):
        r = get_own_restaurant(request)
        qs = SettlementLine.objects.filter(restaurant=r).select_related("order")
        df, dt = request.query_params.get("date_from"), request.query_params.get("date_to")
        if df:
            qs = qs.filter(delivered_at__date__gte=df)
        if dt:
            qs = qs.filter(delivered_at__date__lte=dt)
        resp = HttpResponse(content_type="text/csv")
        resp["Content-Disposition"] = f'attachment; filename="statement-{r.slug}-{df or "all"}-{dt or "all"}.csv"'
        w = csv.writer(resp)
        w.writerow(["Order", "Delivered at", "Gross", "Commission %", "Commission", "Coupon deduction",
                    "Refund deduction", "GST collected", "Net", "Payment", "Settlement"])
        for ln in qs:
            w.writerow([ln.order.order_number, ln.delivered_at.isoformat(), ln.gross, ln.commission_percent,
                        ln.commission, ln.coupon_deduction, ln.refund_deduction, ln.tax_collected, ln.net,
                        "COD" if ln.is_cod else "Online", ln.settlement_id or "unsettled"])
        return resp
