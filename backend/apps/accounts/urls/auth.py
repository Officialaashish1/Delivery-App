from django.urls import path

from .. import views

urlpatterns = [
    path("otp/request/", views.RequestOTPView.as_view(), name="otp-request"),
    path("otp/verify/", views.VerifyOTPView.as_view(), name="otp-verify"),
    path("login/", views.PasswordLoginView.as_view(), name="password-login"),
    path("token/refresh/", views.RefreshView.as_view(), name="token-refresh"),
    path("logout/", views.LogoutView.as_view(), name="logout"),
    path("logout-all/", views.LogoutAllView.as_view(), name="logout-all"),
    path("password/forgot/", views.ForgotPasswordView.as_view(), name="password-forgot"),
    path("password/change/", views.ChangePasswordView.as_view(), name="password-change"),
    path("me/", views.MeView.as_view(), name="me"),
    path("devices/", views.DeviceRegisterView.as_view(), name="device-register"),
    path("account/", views.DeleteAccountView.as_view(), name="account-delete"),
]
