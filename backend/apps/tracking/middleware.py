"""JWT auth for WebSockets. Token via `?token=` query param or `Authorization: Bearer` header."""
from urllib.parse import parse_qs

from channels.db import database_sync_to_async
from channels.middleware import BaseMiddleware
from django.contrib.auth.models import AnonymousUser


@database_sync_to_async
def get_user_from_token(raw_token: str):
    from rest_framework_simplejwt.exceptions import TokenError
    from rest_framework_simplejwt.tokens import AccessToken

    from apps.accounts.models import User

    try:
        token = AccessToken(raw_token)
        user = User.objects.get(pk=token["user_id"])
        if not user.can_login or token.get("tv") != user.token_version:
            return AnonymousUser()
        return user
    except (TokenError, User.DoesNotExist, KeyError):
        return AnonymousUser()


class JWTAuthMiddleware(BaseMiddleware):
    async def __call__(self, scope, receive, send):
        token = None
        qs = parse_qs(scope.get("query_string", b"").decode())
        if "token" in qs:
            token = qs["token"][0]
        else:
            for name, value in scope.get("headers", []):
                if name == b"authorization" and value.lower().startswith(b"bearer "):
                    token = value[7:].decode()
        scope["user"] = await get_user_from_token(token) if token else AnonymousUser()
        return await super().__call__(scope, receive, send)
