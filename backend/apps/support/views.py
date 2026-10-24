from drf_spectacular.utils import OpenApiParameter, extend_schema
from rest_framework import generics, mixins, permissions, status, viewsets
from rest_framework.decorators import action
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.core.models import PlatformSettings
from apps.notifications.services import notify_admins

from .models import FAQ, SupportTicket, TicketCategory, TicketMessage
from .serializers import FAQSerializer, SupportTicketSerializer, TicketMessageSerializer


@extend_schema(tags=["support"])
class TicketViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, mixins.CreateModelMixin,
                    viewsets.GenericViewSet):
    serializer_class = SupportTicketSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return SupportTicket.objects.none()
        return SupportTicket.objects.filter(user=self.request.user).select_related("order").prefetch_related(
            "messages__sender")

    def perform_create(self, serializer):
        ticket = serializer.save(user=self.request.user)
        TicketMessage.objects.create(ticket=ticket, sender=self.request.user, text=ticket.description)
        urgent = ticket.category in (TicketCategory.EMERGENCY, TicketCategory.CUSTOMER_UNAVAILABLE)
        notify_admins(f"{'🚨 ' if urgent else ''}New support ticket {ticket.ticket_number}",
                      f"{ticket.get_category_display()}: {ticket.subject}", {"ticket_id": str(ticket.id)})

    @extend_schema(request=TicketMessageSerializer, responses=TicketMessageSerializer)
    @action(detail=True, methods=["post"])
    def reply(self, request, pk=None):
        ticket = self.get_object()
        ser = TicketMessageSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        msg = ser.save(ticket=ticket, sender=request.user)
        if ticket.status in ("waiting", "resolved"):
            ticket.status = "open"
            ticket.save(update_fields=["status"])
        return Response(TicketMessageSerializer(msg).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["post"])
    def close(self, request, pk=None):
        ticket = self.get_object()
        ticket.status = "closed"
        ticket.save(update_fields=["status"])
        return Response(self.get_serializer(ticket).data)


@extend_schema(tags=["support"], parameters=[OpenApiParameter("audience", str)])
class FAQListView(generics.ListAPIView):
    permission_classes = [permissions.AllowAny]
    serializer_class = FAQSerializer
    pagination_class = None

    def get_queryset(self):
        return FAQ.objects.filter(is_active=True, audience=self.request.query_params.get("audience", "customer"))


@extend_schema(tags=["support"])
class SupportContactView(APIView):
    permission_classes = [permissions.AllowAny]

    def get(self, request):
        ps = PlatformSettings.load()
        return Response({"phone": ps.support_phone, "email": ps.support_email,
                         "categories": [{"key": k, "label": v} for k, v in TicketCategory.choices]})
