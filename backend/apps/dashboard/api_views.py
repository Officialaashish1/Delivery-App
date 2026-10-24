"""Admin REST API consumed by the web dashboard (and usable by any future admin client)."""
from django.db import transaction
from django.db.models import Q
from django.utils import timezone
from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import mixins, status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.accounts.models import User, UserRole
from apps.core.audit import audit
from apps.core.exceptions import BusinessError
from apps.core.models import AuditLog, PlatformSettings
from apps.core.permissions import IsAdminRole
from apps.coupons.models import Coupon
from apps.finance.models import RestaurantSettlement
from apps.finance.serializers import SettlementSerializer
from apps.finance.services import apply_refund_deduction, build_settlement, mark_settlement_paid
from apps.notifications.models import PromoCampaign
from apps.notifications.services import notify_user
from apps.orders.models import ACTIVE_STATUSES, Order, OrderStatus
from apps.orders.services import admin_assign_rider, cancel_order
from apps.payments.models import Payment, Refund, RefundStatus
from apps.payments.services import initiate_refund
from apps.payments.views import PaymentSerializer, RefundSerializer
from apps.restaurants.models import Restaurant, RestaurantDocument, RestaurantStatus
from apps.riders.models import PayoutStatus, RiderDocument, RiderPayout, RiderProfile, RiderStatus
from apps.riders.serializers import RiderPayoutSerializer
from apps.support.models import SupportTicket, TicketMessage
from apps.support.serializers import SupportTicketSerializer, TicketMessageSerializer

from . import serializers as s
from . import stats


@extend_schema(tags=["admin"], responses={200: dict})
class OverviewView(APIView):
    permission_classes = [IsAdminRole]

    def get(self, request):
        return Response(stats.overview())


@extend_schema(tags=["admin"], parameters=[OpenApiParameter("days", int)], responses={200: dict})
class SalesSeriesView(APIView):
    permission_classes = [IsAdminRole]

    def get(self, request):
        days = min(365, max(7, int(request.query_params.get("days", 30))))
        return Response(stats.sales_series(days))


@extend_schema(tags=["admin"], parameters=[OpenApiParameter("days", int)], responses={200: dict})
class ReportsView(APIView):
    permission_classes = [IsAdminRole]

    def get(self, request, kind):
        days = min(365, max(1, int(request.query_params.get("days", 30))))
        if kind == "restaurants":
            return Response(stats.restaurant_performance(days))
        if kind == "riders":
            return Response(stats.rider_performance(days))
        if kind == "cancellations":
            return Response(stats.cancellation_report(days))
        if kind == "retention":
            return Response(stats.retention_report())
        if kind == "revenue":
            from apps.finance.models import PlatformLedger

            return Response(list(PlatformLedger.objects.order_by("-date")[:days].values()))
        raise BusinessError("Unknown report.", code="not_found", status_code=404)


@extend_schema(tags=["admin"])
class AdminUserViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, mixins.UpdateModelMixin,
                       viewsets.GenericViewSet):
    permission_classes = [IsAdminRole]
    serializer_class = s.AdminUserSerializer

    def get_queryset(self):
        qs = User.objects.all().select_related("customer_profile").order_by("-created_at")
        role = self.request.query_params.get("role")
        if role:
            qs = qs.filter(role=role)
        q = self.request.query_params.get("q")
        if q:
            qs = qs.filter(Q(phone__icontains=q) | Q(full_name__icontains=q) | Q(email__icontains=q))
        return qs

    @extend_schema(request=s.SuspendSerializer)
    @action(detail=True, methods=["post"])
    def suspend(self, request, pk=None):
        ser = s.SuspendSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        user = self.get_object()
        if user.is_superuser:
            raise BusinessError("Cannot suspend a superuser.", code="permission_denied", status_code=403)
        user.is_suspended = ser.validated_data["suspend"]
        user.suspension_reason = ser.validated_data["reason"]
        user.save(update_fields=["is_suspended", "suspension_reason"])
        if user.is_suspended:
            user.revoke_all_tokens()
            RiderProfile.objects.filter(user=user).update(is_online=False)
        audit("user.suspend" if user.is_suspended else "user.unsuspend", target=user, reason=user.suspension_reason)
        return Response(self.get_serializer(user).data)

    @action(detail=True, methods=["get"])
    def orders(self, request, pk=None):
        orders = Order.objects.filter(customer_id=pk).prefetch_related("items")[:50]
        return Response(s.AdminOrderSerializer(orders, many=True).data)


@extend_schema(tags=["admin"])
class AdminRestaurantViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, mixins.UpdateModelMixin,
                             viewsets.GenericViewSet):
    permission_classes = [IsAdminRole]
    serializer_class = s.AdminRestaurantSerializer

    def get_queryset(self):
        qs = Restaurant.objects.select_related("owner").prefetch_related("documents").order_by("-created_at")
        st = self.request.query_params.get("status")
        if st:
            qs = qs.filter(status=st)
        q = self.request.query_params.get("q")
        if q:
            qs = qs.filter(Q(name__icontains=q) | Q(owner__phone__icontains=q) | Q(city__icontains=q))
        return qs

    @extend_schema(request=s.DecisionSerializer)
    @action(detail=True, methods=["post"])
    def decide(self, request, pk=None):
        ser = s.DecisionSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        r = self.get_object()
        if ser.validated_data["approve"]:
            r.status = RestaurantStatus.APPROVED
            r.approved_at = timezone.now()
            r.rejection_reason = ""
            msg = ("Restaurant approved 🎉", f"{r.name} is now live on LocalRush. Open your restaurant to start receiving orders.")
        else:
            r.status = RestaurantStatus.REJECTED
            r.rejection_reason = ser.validated_data["reason"]
            r.is_open = False
            msg = ("Application needs attention", f"{r.name}: {r.rejection_reason or 'Please review your details.'}")
        r.save()
        notify_user(r.owner_id, *msg, {"type": "restaurant.status", "status": r.status}, category="account")
        audit("restaurant.decide", target=r, approved=ser.validated_data["approve"], reason=r.rejection_reason)
        return Response(self.get_serializer(r).data)

    @extend_schema(request=s.SuspendSerializer)
    @action(detail=True, methods=["post"])
    def suspend(self, request, pk=None):
        ser = s.SuspendSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        r = self.get_object()
        if ser.validated_data["suspend"]:
            r.status = RestaurantStatus.SUSPENDED
            r.is_open = False
            r.rejection_reason = ser.validated_data["reason"]
        else:
            r.status = RestaurantStatus.APPROVED
            r.rejection_reason = ""
        r.save()
        audit("restaurant.suspend", target=r, suspend=ser.validated_data["suspend"])
        return Response(self.get_serializer(r).data)

    @extend_schema(request=s.DecisionSerializer)
    @action(detail=True, methods=["post"], url_path="documents/(?P<doc_id>[^/.]+)/verify")
    def verify_document(self, request, pk=None, doc_id=None):
        ser = s.DecisionSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        doc = RestaurantDocument.objects.filter(pk=doc_id, restaurant_id=pk).first()
        if doc is None:
            raise BusinessError("Document not found.", code="not_found", status_code=404)
        doc.status = "verified" if ser.validated_data["approve"] else "rejected"
        doc.rejection_reason = ser.validated_data["reason"]
        doc.verified_by = request.user
        doc.verified_at = timezone.now()
        doc.save()
        return Response(s.AdminRestaurantDocSerializer(doc).data)


@extend_schema(tags=["admin"])
class AdminRiderViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    permission_classes = [IsAdminRole]
    serializer_class = s.AdminRiderSerializer

    def get_queryset(self):
        qs = RiderProfile.objects.select_related("user").prefetch_related("documents").order_by("-created_at")
        st = self.request.query_params.get("status")
        if st:
            qs = qs.filter(status=st)
        if self.request.query_params.get("online") in ("1", "true"):
            qs = qs.filter(is_online=True)
        q = self.request.query_params.get("q")
        if q:
            qs = qs.filter(Q(full_name__icontains=q) | Q(user__phone__icontains=q) | Q(vehicle_number__icontains=q))
        return qs

    @extend_schema(request=s.DecisionSerializer)
    @action(detail=True, methods=["post"])
    def decide(self, request, pk=None):
        ser = s.DecisionSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        rp = self.get_object()
        if ser.validated_data["approve"]:
            rp.status = RiderStatus.APPROVED
            rp.approved_at = timezone.now()
            rp.rejection_reason = ""
            msg = ("You're approved! 🛵", "Go online in the LocalRush Rider app to start delivering.")
        else:
            rp.status = RiderStatus.REJECTED
            rp.rejection_reason = ser.validated_data["reason"]
            rp.is_online = False
            msg = ("Application needs attention", rp.rejection_reason or "Please review your documents.")
        rp.save()
        notify_user(rp.user_id, *msg, {"type": "rider.status", "status": rp.status}, category="account")
        audit("rider.decide", target=rp, approved=ser.validated_data["approve"])
        return Response(self.get_serializer(rp).data)

    @extend_schema(request=s.SuspendSerializer)
    @action(detail=True, methods=["post"])
    def suspend(self, request, pk=None):
        ser = s.SuspendSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        rp = self.get_object()
        rp.status = RiderStatus.SUSPENDED if ser.validated_data["suspend"] else RiderStatus.APPROVED
        rp.rejection_reason = ser.validated_data["reason"]
        if ser.validated_data["suspend"]:
            rp.is_online = False
        rp.save()
        audit("rider.suspend", target=rp, suspend=ser.validated_data["suspend"])
        return Response(self.get_serializer(rp).data)

    @extend_schema(request=s.DecisionSerializer)
    @action(detail=True, methods=["post"], url_path="documents/(?P<doc_id>[^/.]+)/verify")
    def verify_document(self, request, pk=None, doc_id=None):
        ser = s.DecisionSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        doc = RiderDocument.objects.filter(pk=doc_id, rider_id=pk).first()
        if doc is None:
            raise BusinessError("Document not found.", code="not_found", status_code=404)
        doc.status = "verified" if ser.validated_data["approve"] else "rejected"
        doc.rejection_reason = ser.validated_data["reason"]
        doc.verified_by = request.user
        doc.verified_at = timezone.now()
        doc.save()
        return Response(s.AdminRiderDocSerializer(doc).data)

    @action(detail=True, methods=["get"])
    def deliveries(self, request, pk=None):
        return Response(s.AdminOrderSerializer(Order.objects.filter(rider_id=pk).prefetch_related("items")[:50],
                                               many=True).data)

    @action(detail=True, methods=["get"])
    def earnings(self, request, pk=None):
        from apps.riders.serializers import RiderEarningSerializer
        from apps.riders.services import earnings_summary

        rp = self.get_object()
        return Response({"summary": earnings_summary(rp),
                         "entries": RiderEarningSerializer(rp.earnings.all()[:100], many=True).data})

    @action(detail=False, methods=["get"])
    def live(self, request):
        from apps.tracking.services import admin_live_snapshot

        return Response(admin_live_snapshot())


@extend_schema(tags=["admin"])
class AdminOrderViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    permission_classes = [IsAdminRole]
    serializer_class = s.AdminOrderSerializer

    def get_queryset(self):
        qs = Order.objects.select_related("customer", "rider", "restaurant").prefetch_related("items").order_by(
            "-created_at")
        p = self.request.query_params
        if p.get("status"):
            qs = qs.filter(status=p["status"])
        if p.get("scope") == "active":
            qs = qs.filter(status__in=ACTIVE_STATUSES)
        if p.get("restaurant"):
            qs = qs.filter(restaurant_id=p["restaurant"])
        if p.get("rider"):
            qs = qs.filter(rider_id=p["rider"])
        if p.get("date_from"):
            qs = qs.filter(created_at__date__gte=p["date_from"])
        if p.get("date_to"):
            qs = qs.filter(created_at__date__lte=p["date_to"])
        if p.get("q"):
            q = p["q"]
            qs = qs.filter(Q(order_number__icontains=q) | Q(customer__phone__icontains=q)
                           | Q(restaurant_name__icontains=q))
        return qs

    @action(detail=True, methods=["get"])
    def detail_full(self, request, pk=None):
        from apps.orders.serializers import OrderDetailSerializer

        order = self.get_object()
        data = OrderDetailSerializer(order, context={"request": request}).data
        data["payments"] = PaymentSerializer(order.payments.all(), many=True).data
        data["refunds"] = RefundSerializer(order.refunds.all(), many=True).data
        data["assignments"] = list(order.assignments.values("id", "rider_id", "rider__full_name", "status",
                                                            "offered_at", "responded_at", "rejection_reason"))
        data["tickets"] = SupportTicketSerializer(order.tickets.all(), many=True).data
        data["pickup_otp"] = order.pickup_otp
        data["delivery_otp"] = order.delivery_otp
        data["customer_phone"] = order.customer.phone
        data["rider_phone"] = order.rider.user.phone if order.rider else None
        return Response(data)

    @extend_schema(request=s.SuspendSerializer)
    @action(detail=True, methods=["post"])
    def cancel(self, request, pk=None):
        reason = request.data.get("reason") or "Cancelled by admin"
        order = cancel_order(pk, request.user, reason, by="admin", force=bool(request.data.get("force")))
        return Response(self.get_serializer(order).data)

    @action(detail=True, methods=["post"], url_path="assign-rider")
    def assign_rider(self, request, pk=None):
        rider = RiderProfile.objects.filter(pk=request.data.get("rider_id"), status=RiderStatus.APPROVED).first()
        if rider is None:
            raise BusinessError("Rider not found or not approved.", code="not_found", status_code=404)
        order = admin_assign_rider(pk, rider, request.user)
        return Response(self.get_serializer(order).data)

    @action(detail=True, methods=["post"])
    def refund(self, request, pk=None):
        order = self.get_object()
        amount = request.data.get("amount") or str(order.total_amount)
        reason = request.data.get("reason") or "Refund by admin"
        if not order.is_paid:
            raise BusinessError("Order has no captured payment.", code="not_paid")
        if order.payment_method == "cod":
            # COD refunds are recorded as manual refunds for finance to settle (no gateway call).
            refund = Refund.objects.create(order=order, amount=amount, reason=reason, status=RefundStatus.PENDING,
                                           initiated_by=request.user)
        else:
            refund = initiate_refund(str(order.id), reason, amount, request.user.id)
        if order.status == OrderStatus.DELIVERED and request.data.get("deduct_restaurant"):
            apply_refund_deduction(order, refund.amount)
        audit("order.admin_refund", target=order, amount=str(amount), reason=reason)
        return Response(RefundSerializer(refund).data if refund else {"detail": "nothing to refund"})

    @action(detail=True, methods=["get"], url_path="candidate-riders")
    def candidate_riders(self, request, pk=None):
        from apps.orders.services import find_candidate_riders

        order = self.get_object()
        riders = find_candidate_riders(order, limit=15)
        return Response([{"id": str(r.id), "full_name": r.full_name, "phone": r.user.phone,
                          "distance_km": round(r.distance.km, 2), "rating_avg": str(r.rating_avg)} for r in riders])


@extend_schema(tags=["admin"])
class AdminCouponViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAdminRole]
    serializer_class = s.AdminCouponSerializer
    queryset = Coupon.objects.all()


@extend_schema(tags=["admin"])
class AdminSettlementViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    permission_classes = [IsAdminRole]
    serializer_class = SettlementSerializer

    def get_queryset(self):
        qs = RestaurantSettlement.objects.select_related("restaurant")
        if self.request.query_params.get("status"):
            qs = qs.filter(status=self.request.query_params["status"])
        return qs

    @action(detail=False, methods=["post"])
    def generate(self, request):
        """Build settlements for all restaurants for a period (default: last 7 days)."""
        from datetime import date, timedelta

        end = date.fromisoformat(request.data.get("period_end")) if request.data.get("period_end") else (
            timezone.localdate())
        start = date.fromisoformat(request.data.get("period_start")) if request.data.get("period_start") else (
            end - timedelta(days=6))
        n = 0
        for r in Restaurant.objects.filter(settlement_lines__settlement__isnull=True).distinct():
            if build_settlement(r, start, end):
                n += 1
        return Response({"generated": n})

    @action(detail=True, methods=["post"], url_path="mark-paid")
    def mark_paid(self, request, pk=None):
        st = self.get_object()
        mark_settlement_paid(st, request.user, request.data.get("reference", ""), request.data.get("note", ""))
        audit("settlement.paid", target=st, amount=str(st.net_payable))
        notify_user(st.restaurant.owner_id, "Settlement paid", f"₹{st.net_payable} for {st.period_start} to "
                    f"{st.period_end} has been transferred.", {"type": "settlement"}, category="payment")
        return Response(self.get_serializer(st).data)

    @action(detail=True, methods=["get"])
    def lines(self, request, pk=None):
        from apps.finance.serializers import SettlementLineSerializer

        return Response(SettlementLineSerializer(self.get_object().lines.select_related("order"), many=True).data)


@extend_schema(tags=["admin"])
class AdminRiderPayoutViewSet(mixins.ListModelMixin, viewsets.GenericViewSet):
    permission_classes = [IsAdminRole]
    serializer_class = RiderPayoutSerializer

    def get_queryset(self):
        qs = RiderPayout.objects.select_related("rider")
        if self.request.query_params.get("status"):
            qs = qs.filter(status=self.request.query_params["status"])
        return qs

    @action(detail=True, methods=["post"])
    @transaction.atomic
    def process(self, request, pk=None):
        from django.db.models import F

        from apps.riders.models import RiderEarning

        payout = RiderPayout.objects.select_for_update().get(pk=pk)
        if payout.status not in (PayoutStatus.REQUESTED, PayoutStatus.PROCESSING):
            raise BusinessError("Payout already processed.", code="conflict", status_code=409)
        decision = request.data.get("status")
        if decision not in (PayoutStatus.PAID, PayoutStatus.REJECTED, PayoutStatus.FAILED):
            raise BusinessError("status must be paid, rejected or failed.", code="validation_error")
        payout.status = decision
        payout.reference = request.data.get("reference", "")
        payout.note = request.data.get("note", "")
        payout.processed_by = request.user
        payout.processed_at = timezone.now()
        payout.save()
        if decision == PayoutStatus.PAID:
            RiderProfile.objects.filter(pk=payout.rider_id).update(wallet_balance=F("wallet_balance") - payout.amount)
            remaining, ids = payout.amount, []
            for e in RiderEarning.objects.filter(rider=payout.rider, is_settled=False).order_by("created_at"):
                if remaining <= 0:
                    break
                ids.append(e.id)
                remaining -= e.amount
            RiderEarning.objects.filter(id__in=ids).update(is_settled=True, payout=payout)
            notify_user(payout.rider.user_id, "Payout processed", f"₹{payout.amount} has been sent to your account.",
                        {"type": "payout"}, category="payment")
        audit("rider.payout", target=payout, status=decision, amount=str(payout.amount))
        return Response(self.get_serializer(payout).data)


@extend_schema(tags=["admin"])
class AdminTicketViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, mixins.UpdateModelMixin,
                         viewsets.GenericViewSet):
    permission_classes = [IsAdminRole]
    serializer_class = SupportTicketSerializer

    def get_queryset(self):
        qs = SupportTicket.objects.select_related("user", "order").prefetch_related("messages__sender")
        if self.request.query_params.get("status"):
            qs = qs.filter(status=self.request.query_params["status"])
        return qs

    @action(detail=True, methods=["post"])
    def reply(self, request, pk=None):
        t = self.get_object()
        msg = TicketMessage.objects.create(ticket=t, sender=request.user, is_staff_reply=True,
                                           text=request.data.get("text", ""))
        new_status = request.data.get("status") or "waiting"
        t.status = new_status
        if new_status == "resolved":
            t.resolved_at = timezone.now()
            t.resolution = request.data.get("text", "")[:2000]
        t.assigned_to = request.user
        t.save()
        notify_user(t.user_id, f"Support replied · {t.ticket_number}", msg.text[:120],
                    {"type": "ticket.reply", "ticket_id": str(t.id)}, category="account")
        return Response(TicketMessageSerializer(msg).data)


@extend_schema(tags=["admin"], responses={200: dict})
class PlatformSettingsView(APIView):
    permission_classes = [IsAdminRole]

    def get(self, request):
        return Response(s.PlatformSettingsSerializer(PlatformSettings.load()).data)

    def patch(self, request):
        ps = PlatformSettings.load()
        ser = s.PlatformSettingsSerializer(ps, data=request.data, partial=True)
        ser.is_valid(raise_exception=True)
        ser.save()
        audit("settings.update", target=ps, changes=request.data)
        return Response(ser.data)


@extend_schema(tags=["admin"], responses={200: dict})
class AuditLogView(APIView):
    permission_classes = [IsAdminRole]

    def get(self, request):
        qs = AuditLog.objects.select_related("actor")[:200]
        return Response([{"id": a.id, "actor": str(a.actor) if a.actor else "system", "action": a.action,
                          "target_type": a.target_type, "target_id": a.target_id, "metadata": a.metadata,
                          "ip": a.ip_address, "created_at": a.created_at} for a in qs])


@extend_schema(tags=["admin"], responses={200: dict})
class CampaignView(APIView):
    permission_classes = [IsAdminRole]

    def get(self, request):
        return Response(list(PromoCampaign.objects.order_by("-created_at").values()[:50]))

    def post(self, request):
        from apps.notifications.tasks import send_campaign

        c = PromoCampaign.objects.create(title=request.data.get("title", "")[:120], body=request.data.get("body", "")[:500],
                                         deep_link=request.data.get("deep_link", ""),
                                         audience=request.data.get("audience", "customer"),
                                         city=request.data.get("city", ""), created_by=request.user)
        send_campaign.delay(c.id)
        audit("campaign.sent", target=c)
        return Response({"id": c.id}, status=status.HTTP_201_CREATED)


@extend_schema(tags=["admin"], responses={200: dict})
class AdminPaymentsView(APIView):
    permission_classes = [IsAdminRole]

    def get(self, request):
        kind = request.query_params.get("kind", "payments")
        if kind == "refunds":
            qs = Refund.objects.select_related("order").order_by("-created_at")[:200]
            return Response(RefundSerializer(qs, many=True).data)
        qs = Payment.objects.select_related("order").order_by("-created_at")
        if request.query_params.get("status"):
            qs = qs.filter(status=request.query_params["status"])
        data = PaymentSerializer(qs[:200], many=True).data
        for d, p in zip(data, qs[:200], strict=False):
            d["order_number"] = p.order.order_number
        return Response(data)


@extend_schema(tags=["admin"])
class BannerAdminViewSet(viewsets.ModelViewSet):
    permission_classes = [IsAdminRole]
    serializer_class = s.AdminBannerSerializer

    def get_queryset(self):
        from apps.restaurants.models import Banner

        return Banner.objects.all()


@extend_schema(tags=["admin"], responses={200: dict})
class AdminUsersCreateView(APIView):
    """Create staff/admin or partner accounts with a password (for onboarding by ops)."""

    permission_classes = [IsAdminRole]

    def post(self, request):
        if not request.user.is_superuser and request.data.get("role") == UserRole.ADMIN:
            raise BusinessError("Only a superuser can create admins.", code="permission_denied", status_code=403)
        from apps.accounts.serializers import normalise_phone

        phone = normalise_phone(request.data.get("phone", ""))
        if User.objects.filter(phone=phone).exists():
            raise BusinessError("Phone already registered.", code="conflict", status_code=409)
        user = User.objects.create_user(phone=phone, password=request.data.get("password"),
                                        role=request.data.get("role", UserRole.PARTNER),
                                        full_name=request.data.get("full_name", ""), is_phone_verified=True,
                                        is_staff=request.data.get("role") == UserRole.ADMIN)
        audit("user.created_by_admin", target=user, role=user.role)
        return Response(s.AdminUserSerializer(user).data, status=status.HTTP_201_CREATED)
