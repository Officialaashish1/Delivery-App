import pytest
from django.core.cache import cache

from apps.accounts.models import User

from .conftest import auth


@pytest.mark.django_db
class TestOTPAuth:
    def setup_method(self):
        cache.clear()

    def test_request_and_verify_creates_customer(self, api, monkeypatch):
        monkeypatch.setattr("apps.accounts.services.otp.generate_code", lambda: "246810")
        r = api.post("/api/v1/auth/otp/request/", {"phone": "9876543210", "role": "customer"}, format="json")
        assert r.status_code == 200 and r.json()["is_existing_user"] is False
        r = api.post("/api/v1/auth/otp/verify/", {"phone": "9876543210", "code": "000000", "role": "customer"},
                     format="json")
        assert r.status_code == 400 and r.json()["error"]["code"] == "otp_invalid"
        assert r.json()["error"]["attempts_left"] == 4
        r = api.post("/api/v1/auth/otp/verify/", {"phone": "9876543210", "code": "246810", "role": "customer",
                                                   "full_name": "New User"}, format="json")
        assert r.status_code == 200, r.content
        body = r.json()
        assert body["is_new_user"] is True and body["user"]["phone"] == "+919876543210"
        assert "access" in body and "refresh" in body
        user = User.objects.get(phone="+919876543210")
        assert user.is_phone_verified and hasattr(user, "customer_profile")
        # code cannot be reused
        r = api.post("/api/v1/auth/otp/verify/", {"phone": "9876543210", "code": "246810", "role": "customer"},
                     format="json")
        assert r.status_code == 400

    def test_cooldown_and_wrong_app(self, api, monkeypatch, customer):
        monkeypatch.setattr("apps.accounts.services.otp.generate_code", lambda: "111111")
        r = api.post("/api/v1/auth/otp/request/", {"phone": customer.phone, "role": "customer"}, format="json")
        assert r.status_code == 200
        r = api.post("/api/v1/auth/otp/request/", {"phone": customer.phone, "role": "customer"}, format="json")
        assert r.status_code == 429 and r.json()["error"]["code"] == "otp_cooldown"
        cache.clear()
        r = api.post("/api/v1/auth/otp/request/", {"phone": customer.phone, "role": "rider"}, format="json")
        assert r.status_code == 409 and r.json()["error"]["code"] == "wrong_app"

    def test_refresh_rotation_and_revocation(self, api, customer):
        from apps.accounts.authentication import issue_tokens

        tokens = issue_tokens(customer)
        r = api.post("/api/v1/auth/token/refresh/", {"refresh": tokens["refresh"]}, format="json")
        assert r.status_code == 200 and r.json()["access"]
        new_refresh = r.json()["refresh"]
        # old refresh is blacklisted
        r = api.post("/api/v1/auth/token/refresh/", {"refresh": tokens["refresh"]}, format="json")
        assert r.status_code in (400, 401)
        # logout-all bumps token_version → access rejected
        auth(api, customer)
        r = api.post("/api/v1/auth/logout-all/")
        assert r.status_code == 204
        r = api.get("/api/v1/auth/me/")
        assert r.status_code == 401
        r = api.post("/api/v1/auth/token/refresh/", {"refresh": new_refresh}, format="json")
        assert r.status_code in (400, 401)

    def test_suspended_user_rejected(self, api, customer):
        auth(api, customer)
        customer.is_suspended = True
        customer.save()
        assert api.get("/api/v1/auth/me/").status_code == 401

    def test_password_login_partner(self, api, partner):
        r = api.post("/api/v1/auth/login/", {"phone": partner.phone, "password": "partner12345", "role": "partner"},
                     format="json")
        assert r.status_code == 200 and r.json()["user"]["role"] == "partner"
        r = api.post("/api/v1/auth/login/", {"phone": partner.phone, "password": "wrong"}, format="json")
        assert r.status_code == 400

    def test_role_permissions(self, api, customer, partner):
        auth(api, customer)
        assert api.get("/api/v1/partner/restaurant/").status_code == 403
        assert api.get("/api/v1/riders/dashboard/").status_code == 403
        assert api.get("/api/v1/admin/overview/").status_code == 403
        auth(api, partner)
        assert api.get("/api/v1/cart/").status_code == 403

    def test_address_crud_and_default(self, customer_api):
        r = customer_api.post("/api/v1/customers/addresses/", {"label": "home", "address_line1": "Flat 1",
                                                               "city": "Surat", "latitude": 21.2, "longitude": 72.8},
                              format="json")
        assert r.status_code == 201 and r.json()["is_default"] is True
        a1 = r.json()["id"]
        r = customer_api.post("/api/v1/customers/addresses/", {"label": "work", "address_line1": "Office",
                                                               "city": "Surat", "latitude": 21.21, "longitude": 72.81,
                                                               "is_default": True}, format="json")
        assert r.status_code == 201 and r.json()["is_default"] is True
        r = customer_api.get(f"/api/v1/customers/addresses/{a1}/")
        assert r.json()["is_default"] is False
        r = customer_api.post("/api/v1/customers/addresses/", {"label": "home", "address_line1": "x", "city": "y",
                                                               "latitude": 95, "longitude": 0}, format="json")
        assert r.status_code == 400
