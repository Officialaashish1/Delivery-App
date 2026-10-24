from django.contrib.auth import authenticate, login, logout
from django.contrib.auth.decorators import login_required, user_passes_test
from django.http import JsonResponse
from django.shortcuts import redirect, render
from django.views.decorators.http import require_POST

from apps.accounts.authentication import issue_tokens
from apps.accounts.models import UserRole
from apps.accounts.serializers import normalise_phone
from apps.core.audit import audit


def is_admin(user):
    return user.is_authenticated and (user.is_staff or user.role == UserRole.ADMIN) and user.can_login


def login_view(request):
    if is_admin(request.user):
        return redirect("dashboard:app")
    error = ""
    if request.method == "POST":
        try:
            phone = normalise_phone(request.POST.get("phone", ""))
        except Exception:  # noqa: BLE001
            phone = ""
        user = authenticate(request, phone=phone, password=request.POST.get("password", ""))
        if user and is_admin(user):
            login(request, user)
            audit("dashboard.login", target=user, actor=user)
            return redirect(request.GET.get("next") or "dashboard:app")
        error = "Invalid credentials or not an admin account."
    return render(request, "dashboard/login.html", {"error": error})


@require_POST
def logout_view(request):
    logout(request)
    return redirect("dashboard:login")


@login_required(login_url="/dashboard/login/")
@user_passes_test(is_admin, login_url="/dashboard/login/")
def app_view(request):
    return render(request, "dashboard/app.html")


@login_required(login_url="/dashboard/login/")
@user_passes_test(is_admin, login_url="/dashboard/login/")
def api_token(request):
    """Hands the session-authenticated admin a short-lived JWT for the admin API and WebSocket."""
    tokens = issue_tokens(request.user)
    return JsonResponse({"access": tokens["access"], "refresh": tokens["refresh"],
                         "user": {"id": str(request.user.id), "name": request.user.full_name or request.user.phone,
                                  "is_superuser": request.user.is_superuser}})
