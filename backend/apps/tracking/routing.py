from django.urls import re_path

from . import consumers

websocket_urlpatterns = [
    re_path(r"^ws/orders/(?P<order_id>[0-9a-f-]{36})/tracking/$", consumers.OrderTrackingConsumer.as_asgi()),
    re_path(r"^ws/rider/$", consumers.RiderConsumer.as_asgi()),
    re_path(r"^ws/partner/$", consumers.PartnerConsumer.as_asgi()),
    re_path(r"^ws/admin/live/$", consumers.AdminLiveConsumer.as_asgi()),
]
