"""
Load test for the customer discovery + ordering path and the rider location endpoint.
Run against a seeded dev stack (first: docker compose exec api python manage.py loadtest_tokens --customers 200 --riders 50):
  docker run --rm --network localrush_default -v $PWD/backend/loadtests:/mnt/locust -p 127.0.0.1:8089:8089 \
     locustio/locust -f /mnt/locust/locustfile.py --host http://api:8000
Headless example: ... locustio/locust -f /mnt/locust/locustfile.py --host http://api:8000 --headless -u 200 -r 20 -t 2m
"""
import json
import os
import random
import time

from locust import HttpUser, between, task

OTP = "123456"  # dev fixed OTP

# Preferred: a token pool written by `python manage.py loadtest_tokens` (avoids the per-IP OTP throttle that would
# otherwise block logins when every virtual user shares the load generator's IP).
_TOKENS = {"customers": [], "riders": []}
_path = os.environ.get("LOADTEST_TOKENS", os.path.join(os.path.dirname(__file__), "tokens.json"))
if os.path.exists(_path):
    with open(_path) as _f:
        _TOKENS = json.load(_f)


class CustomerBrowsing(HttpUser):
    weight = 8
    wait_time = between(1, 4)

    def on_start(self):
        if _TOKENS["customers"]:
            self.token = random.choice(_TOKENS["customers"])
        else:
            phone = f"+9196{random.randint(10000000, 99999999)}"
            self.client.post("/api/v1/auth/otp/request/", json={"phone": phone, "role": "customer"})
            r = self.client.post("/api/v1/auth/otp/verify/", json={"phone": phone, "code": OTP, "role": "customer",
                                                                     "full_name": "Load User"})
            self.token = r.json().get("access", "")
        self.headers = {"Authorization": f"Bearer {self.token}"}
        self.lat, self.lng = 21.23 + random.uniform(-0.02, 0.02), 72.87 + random.uniform(-0.02, 0.02)
        self.slugs = []

    @task(5)
    def home(self):
        r = self.client.get(f"/api/v1/restaurants/home/?lat={self.lat}&lng={self.lng}", headers=self.headers, name="/home")
        if r.ok:
            self.slugs = [x["slug"] for s in r.json()["sections"] for x in s["restaurants"]]

    @task(3)
    def list_and_detail(self):
        r = self.client.get(f"/api/v1/restaurants/?lat={self.lat}&lng={self.lng}&sort=distance", headers=self.headers,
                            name="/restaurants")
        if r.ok and r.json()["results"]:
            slug = random.choice(r.json()["results"])["slug"]
            self.client.get(f"/api/v1/restaurants/{slug}/?lat={self.lat}&lng={self.lng}", headers=self.headers,
                            name="/restaurants/{slug}")

    @task(2)
    def search(self):
        q = random.choice(["pizza", "paneer", "dal", "biryani", "roti"])
        self.client.get(f"/api/v1/restaurants/search/?q={q}&lat={self.lat}&lng={self.lng}", headers=self.headers,
                        name="/search")

    @task(1)
    def cart_preview(self):
        self.client.get("/api/v1/cart/", headers=self.headers, name="/cart")


class RiderLocationStream(HttpUser):
    """Simulates online riders posting GPS every 4 seconds (the hot write path)."""

    weight = 2
    wait_time = between(3, 5)

    def on_start(self):
        if _TOKENS["riders"]:
            token = random.choice(_TOKENS["riders"])
        else:
            phone = f"+9197{random.randint(10000000, 99999999)}"
            self.client.post("/api/v1/auth/otp/request/", json={"phone": phone, "role": "rider"})
            r = self.client.post("/api/v1/auth/otp/verify/", json={"phone": phone, "code": OTP, "role": "rider"})
            token = r.json().get("access", "")
        self.headers = {"Authorization": f"Bearer {token}"}
        self.lat, self.lng = 21.23 + random.uniform(-0.03, 0.03), 72.87 + random.uniform(-0.03, 0.03)
        self.client.post("/api/v1/riders/online/", json={"is_online": True, "latitude": self.lat, "longitude": self.lng},
                         headers=self.headers, name="/riders/online")

    @task
    def post_location(self):
        self.lat += random.uniform(-0.0004, 0.0004)
        self.lng += random.uniform(-0.0004, 0.0004)
        self.client.post("/api/v1/rider/location/", headers=self.headers, name="/rider/location", json={
            "latitude": self.lat, "longitude": self.lng, "accuracy": 8, "speed": 20, "bearing": 90,
            "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())})
