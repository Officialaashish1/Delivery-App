import logging

from django.core.exceptions import PermissionDenied
from django.core.exceptions import ValidationError as DjangoValidationError
from django.db import IntegrityError
from django.http import Http404
from rest_framework import status
from rest_framework.exceptions import APIException
from rest_framework.views import exception_handler

log = logging.getLogger("localrush.api")


class BusinessError(APIException):
    """Raised by domain services for rule violations. Carries a stable machine-readable code."""

    status_code = status.HTTP_400_BAD_REQUEST
    default_code = "business_error"
    default_detail = "The request could not be completed."

    def __init__(self, detail=None, code=None, status_code=None, extra=None):
        super().__init__(detail=detail, code=code)
        if status_code:
            self.status_code = status_code
        self.extra = extra or {}


class ConflictError(BusinessError):
    status_code = status.HTTP_409_CONFLICT
    default_code = "conflict"


def api_exception_handler(exc, context):
    if isinstance(exc, DjangoValidationError):
        exc = APIException(detail=exc.messages, code="validation_error")
        exc.status_code = 400
    if isinstance(exc, IntegrityError):
        log.warning("integrity error", exc_info=exc)
        exc = ConflictError(detail="Conflicting data.", code="conflict")
    response = exception_handler(exc, context)
    if response is None:
        return None
    detail = response.data
    code = getattr(exc, "default_code", "error")
    if isinstance(exc, Http404):
        code = "not_found"
    elif isinstance(exc, PermissionDenied):
        code = "permission_denied"
    if isinstance(detail, dict) and "detail" in detail and len(detail) == 1:
        message = str(detail["detail"])
        errors = None
        codes = detail["detail"]
        if hasattr(codes, "code"):
            code = codes.code
    elif isinstance(detail, dict):
        message = "Validation failed."
        errors = detail
        code = "validation_error"
    elif isinstance(detail, list):
        message = str(detail[0]) if detail else "Error."
        errors = detail
    else:
        message = str(detail)
        errors = None
    payload = {"error": {"code": code, "message": message}}
    if errors is not None:
        payload["error"]["fields"] = errors
    if getattr(exc, "extra", None):
        payload["error"].update(exc.extra)
    response.data = payload
    return response
