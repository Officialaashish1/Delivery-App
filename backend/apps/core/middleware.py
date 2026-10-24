from .audit import set_request_context


def get_client_ip(request):
    """Trust only the proxy-set X-Real-IP (nginx sets it from $remote_addr); never a client-supplied XFF."""
    return request.META.get("HTTP_X_REAL_IP") or request.META.get("REMOTE_ADDR")


class AuditContextMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        user = getattr(request, "user", None)
        set_request_context(
            user=user if (user is not None and user.is_authenticated) else None,
            ip=get_client_ip(request),
            user_agent=request.META.get("HTTP_USER_AGENT", ""),
        )
        return self.get_response(request)
