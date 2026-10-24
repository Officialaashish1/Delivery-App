import contextvars

from .models import AuditLog

_request_ctx: contextvars.ContextVar[dict] = contextvars.ContextVar("audit_request_ctx")


def set_request_context(**kwargs):
    _request_ctx.set(kwargs)


def get_request_context() -> dict:
    return _request_ctx.get({})


def audit(action: str, target=None, actor=None, **metadata):
    ctx = get_request_context()
    AuditLog.objects.create(
        actor=actor if actor is not None else ctx.get("user"),
        action=action,
        target_type=target.__class__.__name__ if target is not None else "",
        target_id=str(getattr(target, "pk", "")) if target is not None else "",
        metadata=metadata,
        ip_address=ctx.get("ip"),
        user_agent=(ctx.get("user_agent") or "")[:256],
    )
