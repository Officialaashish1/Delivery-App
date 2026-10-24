from drf_spectacular.utils import extend_schema
from rest_framework import generics, status
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Notification
from .serializers import NotificationSerializer


@extend_schema(tags=["notifications"])
class NotificationListView(generics.ListAPIView):
    serializer_class = NotificationSerializer

    def get_queryset(self):
        if getattr(self, "swagger_fake_view", False):
            return Notification.objects.none()
        return Notification.objects.filter(user=self.request.user)


@extend_schema(tags=["notifications"])
class UnreadCountView(APIView):
    def get(self, request):
        return Response({"unread": Notification.objects.filter(user=request.user, is_read=False).count()})


@extend_schema(tags=["notifications"])
class MarkReadView(APIView):
    def post(self, request):
        ids = request.data.get("ids")
        qs = Notification.objects.filter(user=request.user, is_read=False)
        if ids:
            qs = qs.filter(id__in=ids)
        return Response({"updated": qs.update(is_read=True)})


@extend_schema(tags=["notifications"])
class DeleteAllView(APIView):
    def delete(self, request):
        Notification.objects.filter(user=request.user).delete()
        return Response(status=status.HTTP_204_NO_CONTENT)
