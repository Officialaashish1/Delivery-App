"""Server-side image compression for uploaded photos (menu items, logos, profile pictures)."""
import io
import os

from django.core.files.base import ContentFile
from PIL import Image, ImageOps


def compress_image(file_obj, max_side=1280, quality=82) -> ContentFile:
    img = Image.open(file_obj)
    img = ImageOps.exif_transpose(img)
    if img.mode not in ("RGB", "L"):
        img = img.convert("RGB")
    img.thumbnail((max_side, max_side))
    buf = io.BytesIO()
    img.save(buf, format="WEBP", quality=quality, method=4)
    name = os.path.splitext(getattr(file_obj, "name", "image"))[0] + ".webp"
    return ContentFile(buf.getvalue(), name=name)
