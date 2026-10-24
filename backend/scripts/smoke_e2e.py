"""End-to-end smoke test against a running dev stack (docker compose up). Run:
  docker run --rm --network localrush_default -v $PWD/backend/scripts:/s:ro python:3.12-slim python -I /s/smoke_e2e.py
Requires: python manage.py seed --demo (dev OTP 123456)."""
import json
import sys
import time
import urllib.error
import urllib.request

BASE = "http://api:8000/api/v1"
def call(method, path, body=None, token=None, raw=False):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(BASE + path, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token: req.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(req, timeout=20) as r:
            txt = r.read().decode()
            return r.status, (json.loads(txt) if txt and not raw else txt)
    except urllib.error.HTTPError as e:
        txt = e.read().decode()
        return e.code, (json.loads(txt) if txt.startswith("{") else txt)
def ok(label, status, data, expect=(200, 201, 204)):
    flag = "OK " if status in expect else "FAIL"
    print(f"[{flag}] {label} -> {status}" + ("" if status in expect else f" {str(data)[:300]}"))
    if status not in expect: sys.exit(1)
    return data
def login(phone, role):
    s, d = call("POST", "/auth/otp/request/", {"phone": phone, "role": role})
    if s == 429:
        print("   otp cooldown, waiting 31s"); time.sleep(31); s, d = call("POST", "/auth/otp/request/", {"phone": phone, "role": role})
    ok(f"otp request {role}", s, d)
    s, d = call("POST", "/auth/otp/verify/", {"phone": phone, "code": "123456", "role": role, "full_name": "Test " + role}); ok(f"otp verify {role}", s, d)
    return d["access"]

# clean slate: admin cancels any active orders left by previous runs
s, d = call("POST", "/auth/login/", {"phone": "+919999999999", "password": "admin12345"}); ok("admin login", s, d); admin = d["access"]
s, act = call("GET", "/admin/orders/?scope=active", token=admin); ok("admin active orders", s, act)
for o in act["results"]:
    s, d = call("POST", f"/admin/orders/{o['id']}/cancel/", {"reason": "smoke cleanup", "force": True}, token=admin); print("   cleanup", o["order_number"], s)
cust = login("+919200000001", "customer")
call("DELETE", "/cart/", token=cust)
s, home = call("GET", "/restaurants/home/?lat=21.2300&lng=72.8700&city=Surat", token=cust); ok("home", s, home)
print("   sections:", [(x["key"], len(x["restaurants"])) for x in home["sections"]], "categories:", len(home["categories"]))
s, lst = call("GET", "/restaurants/?lat=21.23&lng=72.87&sort=distance", token=cust); ok("restaurant list", s, lst)
rest = lst["results"][0]; print("   first:", rest["name"], rest["distance_km"], "km, fee", rest["delivery_fee"], "eta", rest["delivery_time_min"])
s, detail = call("GET", f"/restaurants/{rest['slug']}/?lat=21.23&lng=72.87", token=cust); ok("restaurant detail", s, detail)
item = detail["menu"][0]["items"][0]; print("   item:", item["name"], item["effective_price"], "variants:", len(item["variant_groups"]))
s, search = call("GET", "/restaurants/search/?q=paneer&lat=21.23&lng=72.87", token=cust); ok("search", s, search); print("   search items:", len(search["items"]))
body = {"menu_item_id": item["id"], "quantity": 2}
if item["variant_groups"]:
    body["variant_id"] = item["variant_groups"][0]["options"][0]["id"]; body["addon_ids"] = [item["addon_groups"][0]["options"][0]["id"]]
s, cart = call("POST", "/cart/", body, token=cust); ok("add to cart", s, cart)
item2 = detail["menu"][1]["items"][0]
s, cart = call("POST", "/cart/", {"menu_item_id": item2["id"], "quantity": 1}, token=cust); ok("add 2nd item", s, cart)
s, addrs = call("GET", "/customers/addresses/", token=cust); ok("addresses", s, addrs)
addr = addrs["results"][0]
s, cart = call("PATCH", "/cart/", {"delivery_address_id": addr["id"]}, token=cust); ok("cart address", s, cart)
for code in ("WELCOME50", "FREEDEL", ""):
    s, cart = call("PATCH", "/cart/", {"coupon_code": code}, token=cust)
    if s == 200: print("   coupon used:", code or "none"); break
print("   pricing:", {k: cart["pricing"][k] for k in ("item_total", "delivery_fee", "coupon_discount", "tax_amount", "total")})
s, coupons = call("GET", f"/coupons/?restaurant={rest['id']}&subtotal={cart['pricing']['item_total']}", token=cust); ok("coupons", s, coupons)
s, placed = call("POST", "/orders/", {"address_id": addr["id"], "payment_method": "cod", "idempotency_key": f"smoke-{time.time()}"}, token=cust); ok("place order COD", s, placed)
order = placed["order"]; oid = order["id"]; print("   order", order["order_number"], order["status"], "total", order["total_amount"])
s, dup = call("POST", "/orders/", {"address_id": addr["id"], "payment_method": "cod"}, token=cust); ok("empty cart rejected", s, dup, expect=(400,))

# rider
rider = login("+919100000001", "rider")
s, d = call("POST", "/riders/online/", {"is_online": True, "latitude": 21.229, "longitude": 72.871}, token=rider); ok("rider online", s, d)
s, d = call("POST", "/rider/location/", {"latitude": 21.2291, "longitude": 72.8711, "accuracy": 5, "speed": 0, "bearing": 90, "timestamp": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}, token=rider); ok("rider location", s, d)
# partner
s, d = call("POST", "/auth/login/", {"phone": rest and "+919000000001", "password": "partner12345", "role": "partner"}); ok("partner password login", s, d); partner = d["access"]
# find which partner owns this restaurant
s, pr = call("GET", "/partner/restaurant/", token=partner); ok("partner restaurant", s, pr)
if pr["id"] != rest["id"]:
    s, d = call("POST", "/auth/login/", {"phone": "+919000000002", "password": "partner12345"}); partner = d["access"]
s, dash = call("GET", "/partner/dashboard/", token=partner); ok("partner dashboard", s, dash); print("   today:", dash["today"])
s, new_orders = call("GET", "/partner/orders/?scope=new", token=partner); ok("partner new orders", s, new_orders)
assert any(o["id"] == oid for o in new_orders["results"]), "order not visible to partner"
s, d = call("POST", f"/partner/orders/{oid}/accept/", {"preparation_minutes": 15}, token=partner); ok("partner accept", s, d)
pickup_otp = d["pickup_otp"]; print("   pickup otp", pickup_otp)
s, d = call("POST", f"/partner/orders/{oid}/preparing/", {}, token=partner); ok("partner preparing", s, d)

# wait for celery dispatch
offers = None
for _ in range(45):
    s, offers = call("GET", "/riders/offers/", token=rider)
    if offers.get("results"): break
    time.sleep(1)
ok("rider offers", s, offers); assert offers["results"], "no offer received (is the worker running?)"
offer = offers["results"][0]; print("   offer for", offer["order"]["order_number"], "payout", offer["order"]["rider_payout"], "seconds_left", offer["seconds_left"])
s, d = call("POST", f"/riders/offers/{offer['id']}/accept/", {}, token=rider); ok("rider accept offer", s, d)
s, d = call("GET", f"/orders/{oid}/", token=cust); ok("customer sees rider", s, d); print("   status", d["status"], "rider", d["rider"] and d["rider"]["full_name"], "delivery otp", d["delivery_otp"])
delivery_otp = d["delivery_otp"]
s, d = call("POST", f"/riders/orders/{oid}/pickup/", {"otp": pickup_otp}, token=rider); ok("pickup before ready rejected", s, d, expect=(409,))
s, d = call("POST", f"/partner/orders/{oid}/ready/", {}, token=partner); ok("partner ready", s, d); print("   status", d["status"])
s, d = call("GET", f"/riders/orders/{oid}/route/?lat=21.2291&lng=72.8711", token=rider); ok("rider route", s, d); print("   route", d["leg"], d["distance_km"], "km", d["duration_min"], "min source", d["source"])
s, d = call("POST", f"/riders/orders/{oid}/pickup/", {"otp": "0000"}, token=rider); ok("wrong pickup otp", s, d, expect=(400,))
s, d = call("POST", f"/riders/orders/{oid}/pickup/", {"otp": pickup_otp}, token=rider); ok("pickup", s, d); print("   status", d["status"])
s, d = call("GET", f"/tracking/orders/{oid}/", token=cust); ok("tracking snapshot", s, d); print("   rider_location", d["rider_location"] and (d["rider_location"]["latitude"], d["rider_location"]["longitude"]), "route", d["route"] and d["route"]["leg"])
# stream GPS points toward the customer in <=190 m steps (anti-teleport allows small steps regardless of speed)
import math

dest = d["delivery_location"]; cur = dict(d["rider_location"] or {"latitude": 21.2291, "longitude": 72.8711})
def hav(a, b): 
    R=6371.0088; p1,p2=math.radians(a[0]),math.radians(b[0]); dp=math.radians(b[0]-a[0]); dl=math.radians(b[1]-a[1])
    x=math.sin(dp/2)**2+math.cos(p1)*math.cos(p2)*math.sin(dl/2)**2; return 2*R*math.asin(math.sqrt(x))
total = hav((cur["latitude"], cur["longitude"]), (dest["latitude"], dest["longitude"])); steps = max(1, math.ceil(total / 0.18))
print(f"   approach: {total:.2f} km in {steps} steps")
accepted = 0; last_status = None
for i in range(1, steps + 1):
    lat = cur["latitude"] + (dest["latitude"] - cur["latitude"]) * i / steps; lng = cur["longitude"] + (dest["longitude"] - cur["longitude"]) * i / steps
    ts = time.strftime("%Y-%m-%dT%H:%M:%S", time.gmtime()) + f".{int(time.time()*1000)%1000:03d}Z"
    s, r = call("POST", "/rider/location/", {"order_id": oid, "latitude": lat, "longitude": lng, "accuracy": 6, "speed": 20, "bearing": 45, "timestamp": ts}, token=rider)
    accepted += int(bool(r.get("accepted"))); time.sleep(1.1)
print(f"   accepted {accepted}/{steps} location updates")
s, d = call("GET", f"/orders/{oid}/", token=cust); print("   status after approach:", d["status"]); delivery_otp = d["delivery_otp"]; assert delivery_otp, "customer should see delivery OTP now"
assert d["status"] == "near_destination", "expected automatic near_destination transition"
s, d = call("POST", f"/riders/orders/{oid}/deliver/", {"otp": "9999"}, token=rider); ok("wrong delivery otp", s, d, expect=(400,))
s, d = call("POST", f"/riders/orders/{oid}/deliver/", {"otp": delivery_otp}, token=rider); ok("deliver", s, d); print("   status", d["status"])
s, d = call("GET", "/riders/earnings/?period=today", token=rider); ok("rider earnings", s, d); print("   today", d["summary"]["today_earnings"], "wallet", d["summary"]["wallet_balance"], "cod in hand", d["summary"]["cod_cash_in_hand"])
s, d = call("POST", "/reviews/", {"order_id": oid, "restaurant_rating": 5, "rider_rating": 4, "restaurant_comment": "Great!"}, token=cust); ok("review", s, d)
s, d = call("GET", f"/orders/{oid}/invoice/", token=cust, raw=True); ok("invoice", s, d); print("   invoice bytes", len(d))
s, d = call("GET", "/notifications/", token=cust); ok("notifications", s, d); print("   customer notifications:", [n["title"] for n in d["results"]][:6])
s, d = call("GET", "/partner/finance/summary/", token=partner); ok("partner finance", s, d); print("  ", d)
s, d = call("POST", f"/orders/{oid}/reorder/", {}, token=cust); ok("reorder", s, d); print("   reorder cart items", d["cart"]["item_count"], "skipped", d["skipped_items"])
# admin
s, d = call("GET", "/admin/overview/", token=admin); ok("admin overview", s, d); print("   orders_today", d["orders_today"])
s, d = call("GET", "/admin/orders/?q=" + order["order_number"], token=admin); ok("admin order search", s, d)
s, d = call("GET", f"/admin/orders/{oid}/detail_full/", token=admin); ok("admin order detail", s, d)
s, d = call("GET", "/admin/riders/live/", token=admin); ok("admin live", s, d); print("   live riders", len(d["riders"]))
# cancellation path
s, cart = call("POST", "/cart/", {"menu_item_id": item2["id"], "quantity": 1}, token=cust); ok("cart again", s, cart)
s, placed2 = call("POST", "/orders/", {"address_id": addr["id"], "payment_method": "cod"}, token=cust); ok("2nd order", s, placed2)
s, d = call("POST", f"/orders/{placed2['order']['id']}/cancel/", {"reason": "changed my mind"}, token=cust); ok("customer cancel", s, d); print("   status", d["status"])
s, d = call("POST", "/riders/online/", {"is_online": False}, token=rider); ok("rider offline", s, d)
print("\nALL SMOKE STEPS PASSED")
