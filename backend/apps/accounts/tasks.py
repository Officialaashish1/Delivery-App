from celery import shared_task
from django.utils import timezone
from rest_framework_simplejwt.token_blacklist.models import OutstandingToken

from .models import OTPRequest


@shared_task
def flush_expired_tokens():
    OutstandingToken.objects.filter(expires_at__lt=timezone.now()).delete()
    OTPRequest.objects.filter(expires_at__lt=timezone.now() - timezone.timedelta(days=1)).delete()
