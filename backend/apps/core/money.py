from decimal import ROUND_HALF_UP, Decimal

TWO_PLACES = Decimal("0.01")
ZERO = Decimal("0.00")


def q(value) -> Decimal:
    """Quantize any numeric to 2 decimal places using banker's-safe HALF_UP."""
    return Decimal(str(value)).quantize(TWO_PLACES, rounding=ROUND_HALF_UP)


def percent_of(amount, percent) -> Decimal:
    return q(Decimal(str(amount)) * Decimal(str(percent)) / Decimal("100"))
