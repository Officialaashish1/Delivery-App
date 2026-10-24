import asyncio
import json
import logging
import time

from channels.db import database_sync_to_async
from channels.generic.websocket import AsyncJsonWebsocketConsumer

from .broadcast import ADMIN_GROUP, order_group, restaurant_group, rider_group

log = logging.getLogger("localrush.ws")

CLOSE_UNAUTHORIZED = 4401
CLOSE_FORBIDDEN = 4403
CLOSE_NOT_FOUND = 4404
HEARTBEAT_INTERVAL = 25


class BaseConsumer(AsyncJsonWebsocketConsumer):
    groups_joined: list[str]

    @classmethod
    async def encode_json(cls, content):
        from django.core.serializers.json import DjangoJSONEncoder

        return json.dumps(content, cls=DjangoJSONEncoder)

    async def connect(self):
        self.groups_joined = []
        self.user = self.scope.get("user")
        if self.user is None or not self.user.is_authenticated:
            await self.close(code=CLOSE_UNAUTHORIZED)
            return
        ok = await self.authorize()
        if not ok:
            return
        await self.accept()
        for g in self.groups_joined:
            await self.channel_layer.group_add(g, self.channel_name)
        self._hb = asyncio.ensure_future(self._heartbeat())
        await self.on_connected()

    async def authorize(self) -> bool:
        return True

    async def on_connected(self):
        pass

    async def disconnect(self, code):
        hb = getattr(self, "_hb", None)
        if hb:
            hb.cancel()
        for g in getattr(self, "groups_joined", []):
            await self.channel_layer.group_discard(g, self.channel_name)

    async def _heartbeat(self):
        try:
            while True:
                await asyncio.sleep(HEARTBEAT_INTERVAL)
                await self.send_json({"type": "ping", "ts": int(time.time())})
        except asyncio.CancelledError:
            pass

    async def push_event(self, event):
        await self.send_json(event["payload"])

    async def receive_json(self, content, **kwargs):
        t = content.get("type")
        if t == "pong" or t == "ping":
            if t == "ping":
                await self.send_json({"type": "pong"})
            return
        handler = getattr(self, f"handle_{t}", None) if t else None
        if handler:
            await handler(content)
        else:
            await self.send_json({"type": "error", "message": f"unknown message type {t}"})


class OrderTrackingConsumer(BaseConsumer):
    """/ws/orders/<order_id>/tracking/ — customer (owner), restaurant owner, assigned rider, or admin."""

    async def authorize(self):
        self.order_id = self.scope["url_route"]["kwargs"]["order_id"]
        info = await self._load_order()
        if info is None:
            await self.close(code=CLOSE_NOT_FOUND)
            return False
        self.order_info = info
        u = self.user
        allowed = (u.is_staff or u.role == "admin" or info["customer_id"] == u.id or info["owner_id"] == u.id
                   or (info["rider_user_id"] and info["rider_user_id"] == u.id))
        if not allowed:
            await self.close(code=CLOSE_FORBIDDEN)
            return False
        self.groups_joined = [order_group(self.order_id)]
        return True

    @database_sync_to_async
    def _load_order(self):
        from apps.orders.models import Order

        o = Order.objects.select_related("restaurant", "rider").filter(pk=self.order_id).first()
        if o is None:
            return None
        return {"customer_id": o.customer_id, "owner_id": o.restaurant.owner_id,
                "rider_user_id": o.rider.user_id if o.rider else None}

    async def on_connected(self):
        snapshot = await database_sync_to_async(self._snapshot)()
        await self.send_json({"type": "snapshot", **snapshot})

    def _snapshot(self):
        from .services import tracking_snapshot

        return tracking_snapshot(self.order_id, self.user)

    async def handle_chat(self, content):
        from .services import send_chat_message

        text = (content.get("text") or "").strip()
        if not text:
            return
        try:
            await database_sync_to_async(send_chat_message)(self.order_id, self.user, text)
        except Exception as e:  # noqa: BLE001
            await self.send_json({"type": "error", "message": str(e)})

    async def handle_chat_read(self, content):
        from .services import mark_chat_read

        await database_sync_to_async(mark_chat_read)(self.order_id, self.user)


class RiderConsumer(BaseConsumer):
    """/ws/rider/ — the rider app. Receives offers/order events; may stream location updates."""

    async def authorize(self):
        if self.user.role != "rider":
            await self.close(code=CLOSE_FORBIDDEN)
            return False
        rider_id = await self._rider_id()
        if rider_id is None:
            await self.close(code=CLOSE_FORBIDDEN)
            return False
        self.rider_id = rider_id
        self.groups_joined = [rider_group(rider_id)]
        self._last_loc = 0.0
        return True

    @database_sync_to_async
    def _rider_id(self):
        from apps.riders.models import RiderProfile

        return RiderProfile.objects.filter(user=self.user).values_list("id", flat=True).first()

    async def handle_location(self, content):
        """Same payload as POST /api/v1/rider/location/ — streamed over the socket to save battery/data."""
        from django.conf import settings

        now = time.time()
        if now - self._last_loc < settings.LOCALRUSH["LOCATION_MIN_INTERVAL_SECONDS"]:
            return  # rate limit per connection
        self._last_loc = now
        from .services import ingest_location

        try:
            result = await database_sync_to_async(ingest_location)(self.rider_id, content)
            await self.send_json({"type": "location.ack", **result})
        except Exception as e:  # noqa: BLE001
            await self.send_json({"type": "location.error", "message": str(e)})


class PartnerConsumer(BaseConsumer):
    """/ws/partner/ — the restaurant app: new orders, status changes, rider arrival."""

    async def authorize(self):
        if self.user.role != "partner":
            await self.close(code=CLOSE_FORBIDDEN)
            return False
        rid = await self._restaurant_id()
        if rid is None:
            await self.close(code=CLOSE_NOT_FOUND)
            return False
        self.groups_joined = [restaurant_group(rid)]
        return True

    @database_sync_to_async
    def _restaurant_id(self):
        from apps.restaurants.models import Restaurant

        return Restaurant.objects.filter(owner=self.user).values_list("id", flat=True).first()


class AdminLiveConsumer(BaseConsumer):
    """/ws/admin/live/ — all rider locations and order events for the ops dashboard."""

    async def authorize(self):
        if not (self.user.is_staff or self.user.role == "admin"):
            await self.close(code=CLOSE_FORBIDDEN)
            return False
        self.groups_joined = [ADMIN_GROUP]
        return True

    async def on_connected(self):
        from .services import admin_live_snapshot

        snap = await database_sync_to_async(admin_live_snapshot)()
        await self.send_json({"type": "snapshot", **snap})


