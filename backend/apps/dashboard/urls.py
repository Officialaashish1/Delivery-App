from django.urls import path

from . import views

app_name = "dashboard"
urlpatterns = [
    path("", views.app_view, name="app"),
    path("login/", views.login_view, name="login"),
    path("logout/", views.logout_view, name="logout"),
    path("api-token/", views.api_token, name="api-token"),
]
