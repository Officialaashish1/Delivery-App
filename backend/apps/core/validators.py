import magic
from django.conf import settings
from django.core.exceptions import ValidationError


def _sniff(file_obj) -> str:
    pos = file_obj.tell() if hasattr(file_obj, "tell") else 0
    head = file_obj.read(2048)
    file_obj.seek(pos)
    return magic.from_buffer(head, mime=True)


def validate_image_upload(file_obj):
    if file_obj.size > settings.MAX_UPLOAD_SIZE:
        raise ValidationError(f"File too large (max {settings.MAX_UPLOAD_SIZE // (1024 * 1024)} MB).")
    mime = _sniff(file_obj)
    if mime not in settings.ALLOWED_IMAGE_TYPES:
        raise ValidationError("Only JPEG, PNG and WebP images are allowed.")


def validate_document_upload(file_obj):
    if file_obj.size > settings.MAX_UPLOAD_SIZE:
        raise ValidationError(f"File too large (max {settings.MAX_UPLOAD_SIZE // (1024 * 1024)} MB).")
    mime = _sniff(file_obj)
    if mime not in settings.ALLOWED_DOCUMENT_TYPES:
        raise ValidationError("Only JPEG, PNG, WebP images or PDF documents are allowed.")
