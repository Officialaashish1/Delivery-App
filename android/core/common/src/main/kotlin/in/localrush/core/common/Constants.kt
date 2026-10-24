package `in`.localrush.core.common

object Constants {
    const val OTP_LENGTH = 6
    const val OTP_RESEND_SECONDS = 30
    const val DEV_OTP = "123456"
    const val INDIA_DIAL_CODE = "+91"
    const val PHONE_DIGITS_IN = 10

    const val ACCESS_TOKEN_TTL_MINUTES = 30
    const val REFRESH_TOKEN_TTL_DAYS = 30

    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 100

    const val DEFAULT_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
    const val DEFAULT_MAP_ATTRIBUTION = "© OpenFreeMap © OpenMapTiles Data from OpenStreetMap"

    /** Default city centre (Bengaluru) used before a location is known. */
    const val DEFAULT_LAT = 12.9716
    const val DEFAULT_LNG = 77.5946

    const val WS_PING_INTERVAL_SECONDS = 25
    const val PING_TYPE = "ping"
    const val PONG_TYPE = "pong"
    const val WS_CLOSE_UNAUTHENTICATED = 4401
    const val WS_CLOSE_FORBIDDEN = 4403
    const val WS_CLOSE_NOT_FOUND = 4404

    /** Rider location: fast interval when moving, slow when stationary. */
    const val LOCATION_INTERVAL_MOVING_MS = 5_000L
    const val LOCATION_INTERVAL_STATIONARY_MS = 30_000L
    const val LOCATION_STATIONARY_SPEED_MPS = 1.0f
    const val LOCATION_STATIONARY_AFTER_MS = 60_000L

    const val MARKER_ANIMATION_MS = 1_500L

    object Roles {
        const val CUSTOMER = "customer"
        const val RIDER = "rider"
        const val PARTNER = "partner"
    }

    object Channels {
        const val CUSTOMER_ORDERS = "localrush_orders"
        const val CUSTOMER_PROMOS = "localrush_promos"
        const val RIDER_OFFERS = "localrush_rider_offers"
        const val RIDER_ORDERS = "localrush_rider_orders"
        const val RIDER_TRACKING = "localrush_rider_tracking"
        const val PARTNER_ORDERS = "localrush_partner_orders"
    }

    object DeepLinks {
        const val SCHEME = "localrush"
        const val ORDER = "$SCHEME://order/"
        const val OFFER = "$SCHEME://offer/"
        const val TICKET = "$SCHEME://ticket/"
        const val PROMO = "$SCHEME://promo"
        const val HOME = "$SCHEME://home"
    }
}

/** Which app this binary is. Each app provides this via Hilt. */
enum class AppRole(val wire: String) {
    CUSTOMER(Constants.Roles.CUSTOMER),
    RIDER(Constants.Roles.RIDER),
    PARTNER(Constants.Roles.PARTNER);
}
