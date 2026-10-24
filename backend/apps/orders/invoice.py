from django.template.loader import render_to_string

from apps.core.models import PlatformSettings


def render_invoice_html(order) -> str:
    return render_to_string("orders/invoice.html", {"order": order, "items": order.items.all(),
                                                    "settings": PlatformSettings.load()})
