
import pytest
from channels.testing import WebsocketCommunicator
from django.utils import timezone

from apps.accounts.authentication import issue_tokens
from apps.orders.models import Order, OrderStatus, RiderAssignment
from apps.tracking import store

from .conftest import auth, place_cod_order


def ts(offset=0):
    return (timezone.now() + timezone.timedelta(seconds=offset)).isoformat()


@pytest.fixture
def delivering_order(api, customer, address, partner, restaurant, menu, rider):
    oid = place_cod_order(auth(api, customer), menu, address)["id"]
    auth(api, partner).post(f"/api/v1/partner/orders/{oid}/accept/", {}, format="json")
    offer = RiderAssignment.objects.get(order_id=oid)
    auth(api, rider.user).post(f"/api/v1/riders/offers/{offer.id}/accept/")
    auth(api, partner).post(f"/api/v1/partner/orders/{oid}/ready/")
    o = Order.objects.get(pk=oid)
    auth(api, rider.user).post(f"/api/v1/riders/orders/{oid}/pickup/", {"otp": o.pickup_otp}, format="json")
    return Order.objects.get(pk=oid)


@pytest.mark.django_db(transaction=True)
class TestLocationIngest:
    def test_validation_and_plausibility(self, api, rider, delivering_order):
        rd = auth(api, rider.user)
        oid = str(delivering_order.id)
        r = rd.post("/api/v1/rider/location/", {"order_id": oid, "latitude": 95, "longitude": 72.87}, format="json")
        assert r.status_code == 400
        r = rd.post("/api/v1/rider/location/", {"order_id": oid, "latitude": 21.2300, "longitude": 72.8700,
                                                "accuracy": 900}, format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "low_accuracy"
        r = rd.post("/api/v1/rider/location/", {"order_id": oid, "latitude": 21.2300, "longitude": 72.8700,
                                                "accuracy": 5, "speed": 10, "bearing": 90, "timestamp": ts()},
                    format="json")
        assert r.status_code == 200 and r.json() == {"accepted": True, "order_tracked": True, "order_id": oid}
        # teleport 5 km in 2 seconds → rejected
        r = rd.post("/api/v1/rider/location/", {"order_id": oid, "latitude": 21.2750, "longitude": 72.8700,
                                                "accuracy": 5, "timestamp": ts(2)}, format="json")
        assert r.json()["accepted"] is False and r.json()["reason"] == "implausible_speed"
        # stale (older timestamp) → rejected
        r = rd.post("/api/v1/rider/location/", {"order_id": oid, "latitude": 21.2301, "longitude": 72.8701,
                                                "accuracy": 5, "timestamp": ts(-60)}, format="json")
        assert r.json()["reason"] == "stale"
        # small step ok, and the snapshot reflects it
        r = rd.post("/api/v1/rider/location/", {"order_id": oid, "latitude": 21.2305, "longitude": 72.8705,
                                                "accuracy": 5, "timestamp": ts(5)}, format="json")
        assert r.json()["accepted"] is True
        snap = auth(api, delivering_order.customer).get(f"/api/v1/tracking/orders/{oid}/").json()
        assert snap["rider_location"]["latitude"] == 21.2305 and snap["route"]["leg"] == "to_customer"
        assert snap["route"]["geometry"] and snap["route"]["duration_min"] >= 1

    def test_batch_replay_and_near_destination(self, api, rider, delivering_order):
        rd = auth(api, rider.user)
        oid = str(delivering_order.id)
        dest = delivering_order.delivery_location
        pts = []
        # 10 points walking to the destination over the last 100 seconds, each < 200 m apart
        for i in range(10):
            f = (i + 1) / 10
            pts.append({"order_id": oid, "latitude": 21.2300 + (dest.y - 21.2300) * f,
                        "longitude": 72.8700 + (dest.x - 72.8700) * f, "accuracy": 8, "speed": 15,
                        "timestamp": ts(-100 + i * 10)})
        r = rd.post("/api/v1/rider/location/", {"locations": pts}, format="json")
        assert r.status_code == 200
        accepted = [x for x in r.json()["results"] if x.get("accepted")]
        assert len(accepted) >= 8
        assert Order.objects.get(pk=oid).status == OrderStatus.NEAR_DESTINATION
        # history buffered → flushed to PostgreSQL
        from apps.riders.models import RiderLocation
        from apps.tracking.tasks import flush_location_history

        n = flush_location_history()
        assert n >= 1 and RiderLocation.objects.filter(order_id=oid).count() == n

    def test_other_rider_cannot_track_order(self, api, rider, delivering_order):
        from django.contrib.gis.geos import Point

        from apps.accounts.models import User
        from apps.riders.models import RiderProfile

        u2 = User.objects.create_user(phone="+919100000002", role="rider")
        rp2 = RiderProfile.objects.create(user=u2, status="approved", is_online=True,
                                          current_location=Point(72.87, 21.23, srid=4326))
        r = auth(api, u2).post("/api/v1/rider/location/", {"order_id": str(delivering_order.id), "latitude": 21.23,
                                                           "longitude": 72.87, "accuracy": 5, "timestamp": ts()},
                               format="json")
        assert r.status_code == 200 and r.json()["order_tracked"] is False
        assert store.get_rider_location(rp2.id) is not None


@pytest.mark.django_db(transaction=True)
class TestWebSockets:
    async def _connect(self, path, token=None):
        from config.asgi import application

        qs = f"?token={token}" if token else ""
        comm = WebsocketCommunicator(application, f"/{path}{qs}")
        comm.scope["headers"] = [(b"host", b"localhost")]
        ok, code = await comm.connect()
        return comm, ok, code

    @pytest.mark.asyncio
    async def test_tracking_requires_auth_and_ownership(self, delivering_order, customer, rider):
        from channels.db import database_sync_to_async

        from apps.accounts.models import User

        oid = delivering_order.id
        comm, ok, code = await self._connect(f"ws/orders/{oid}/tracking/")
        assert not ok and code == 4401
        other = await database_sync_to_async(User.objects.create_user)(phone="+919200000077", role="customer")
        tok = await database_sync_to_async(lambda: issue_tokens(other)["access"])()
        comm, ok, code = await self._connect(f"ws/orders/{oid}/tracking/", tok)
        assert not ok and code == 4403
        tok = await database_sync_to_async(lambda: issue_tokens(customer)["access"])()
        comm, ok, _ = await self._connect(f"ws/orders/{oid}/tracking/", tok)
        assert ok
        snap = await comm.receive_json_from(timeout=5)
        assert snap["type"] == "snapshot" and snap["order"]["id"] == str(oid)
        assert snap["delivery_location"]["latitude"] == delivering_order.delivery_location.y
        # a location update from the rider is pushed to the customer socket
        from apps.tracking.services import ingest_location

        await database_sync_to_async(ingest_location)(rider.id, {"order_id": str(oid), "latitude": 21.2300,
                                                                 "longitude": 72.8700, "accuracy": 5,
                                                                 "timestamp": ts()})
        msg = await comm.receive_json_from(timeout=5)
        assert msg["type"] == "rider.location" and msg["latitude"] == 21.23 and msg["rider_id"] == str(rider.id)
        assert msg.get("route") and msg["route"]["leg"] == "to_customer"
        # chat round trip
        await comm.send_json_to({"type": "chat", "text": "I am at the gate"})
        msg = await comm.receive_json_from(timeout=5)
        assert msg["type"] == "chat.message" and msg["text"] == "I am at the gate"
        await comm.send_json_to({"type": "ping"})
        assert (await comm.receive_json_from(timeout=5))["type"] == "pong"
        await comm.disconnect()

    @pytest.mark.asyncio
    async def test_rider_socket_streams_location(self, delivering_order, rider):
        from channels.db import database_sync_to_async

        tok = await database_sync_to_async(lambda: issue_tokens(rider.user)["access"])()
        comm, ok, _ = await self._connect("ws/rider/", tok)
        assert ok
        await comm.send_json_to({"type": "location", "order_id": str(delivering_order.id), "latitude": 21.2301,
                                 "longitude": 72.8701, "accuracy": 4, "timestamp": ts()})
        msg = await comm.receive_json_from(timeout=5)
        assert msg["type"] == "location.ack" and msg["accepted"] is True
        await comm.disconnect()

    @pytest.mark.asyncio
    async def test_partner_socket_gets_new_orders(self, api, customer, address, partner, restaurant, menu):
        from channels.db import database_sync_to_async

        tok = await database_sync_to_async(lambda: issue_tokens(partner)["access"])()
        comm, ok, _ = await self._connect("ws/partner/", tok)
        assert ok
        await database_sync_to_async(lambda: place_cod_order(auth(api, customer), menu, address))()
        msg = await comm.receive_json_from(timeout=5)
        assert msg["type"] in ("status.placed", "order.placed") and msg["status"] == "placed"
        await comm.disconnect()

    @pytest.mark.asyncio
    async def test_admin_socket_forbidden_for_customer(self, customer):
        from channels.db import database_sync_to_async

        tok = await database_sync_to_async(lambda: issue_tokens(customer)["access"])()
        comm, ok, code = await self._connect("ws/admin/live/", tok)
        assert not ok and code == 4403
