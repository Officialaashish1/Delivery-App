from django.conf import settings


def dashboard_settings(request):
    return {"MAP_STYLE_URL": settings.LOCALRUSH["MAP_STYLE_URL"],
            "MAP_ATTRIBUTION": settings.LOCALRUSH["MAP_ATTRIBUTION"]}
