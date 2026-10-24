from rest_framework import exceptions
from rest_framework_simplejwt.authentication import JWTAuthentication as BaseJWTAuthentication
from rest_framework_simplejwt.tokens import RefreshToken


class JWTAuthentication(BaseJWTAuthentication):
    """Adds suspension checks and token_version-based global revocation to SimpleJWT."""

    def get_user(self, validated_token):
        user = super().get_user(validated_token)
        if user.is_suspended:
            raise exceptions.AuthenticationFailed("Account suspended.", code="account_suspended")
        if validated_token.get("tv") != user.token_version:
            raise exceptions.AuthenticationFailed("Token revoked. Please login again.", code="token_revoked")
        return user


def issue_tokens(user) -> dict:
    refresh = RefreshToken.for_user(user)
    refresh["tv"] = user.token_version
    refresh["role"] = user.role
    return {"access": str(refresh.access_token), "refresh": str(refresh)}


try:
    from drf_spectacular.extensions import OpenApiAuthenticationExtension

    class JWTScheme(OpenApiAuthenticationExtension):
        target_class = "apps.accounts.authentication.JWTAuthentication"
        name = "jwtAuth"

        def get_security_definition(self, auto_schema):
            return {"type": "http", "scheme": "bearer", "bearerFormat": "JWT"}
except ImportError:  # pragma: no cover
    pass
