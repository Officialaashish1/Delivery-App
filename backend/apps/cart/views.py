from drf_spectacular.utils import extend_schema
from rest_framework import status
from rest_framework.response import Response
from rest_framework.views import APIView

from apps.accounts.models import Address
from apps.core.exceptions import BusinessError
from apps.core.permissions import IsCustomer
from apps.coupons.services import apply_coupon

from . import serializers as s
from .services import add_to_cart, get_cart, price_cart, update_cart_item


def cart_response(request, status_code=200):
    cart = get_cart(request.user)
    return Response(s.CartSerializer(cart, context={"request": request}).data, status=status_code)


@extend_schema(tags=["cart"])
class CartView(APIView):
    permission_classes = [IsCustomer]

    @extend_schema(responses=s.CartSerializer)
    def get(self, request):
        return cart_response(request)

    @extend_schema(request=s.AddToCartSerializer, responses=s.CartSerializer)
    def post(self, request):
        ser = s.AddToCartSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        add_to_cart(request.user, **ser.validated_data)
        return cart_response(request, status.HTTP_201_CREATED)

    @extend_schema(request=s.CartSettingsSerializer, responses=s.CartSerializer)
    def patch(self, request):
        ser = s.CartSettingsSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        d = ser.validated_data
        cart = get_cart(request.user)
        if "delivery_address_id" in d:
            if d["delivery_address_id"] is None:
                cart.delivery_address = None
            else:
                addr = Address.objects.filter(id=d["delivery_address_id"], user=request.user).first()
                if addr is None:
                    raise BusinessError("Address not found.", code="not_found", status_code=404)
                cart.delivery_address = addr
        if "coupon_code" in d:
            code = d["coupon_code"].strip().upper()
            if code and cart.restaurant:
                bd, _ = price_cart(cart, request.user, coupon_code="")
                apply_coupon(code, request.user, cart.restaurant, bd.item_total, bd.delivery_fee)  # raises if invalid
            cart.coupon_code = code
        if "special_instructions" in d:
            cart.special_instructions = d["special_instructions"]
        cart.save()
        return cart_response(request)

    @extend_schema(responses={204: None})
    def delete(self, request):
        get_cart(request.user).clear()
        return Response(status=status.HTTP_204_NO_CONTENT)


@extend_schema(tags=["cart"])
class CartItemView(APIView):
    permission_classes = [IsCustomer]

    @extend_schema(request=s.UpdateCartItemSerializer, responses=s.CartSerializer)
    def patch(self, request, item_id):
        ser = s.UpdateCartItemSerializer(data=request.data)
        ser.is_valid(raise_exception=True)
        update_cart_item(request.user, item_id, ser.validated_data["quantity"])
        return cart_response(request)

    @extend_schema(responses=s.CartSerializer)
    def delete(self, request, item_id):
        update_cart_item(request.user, item_id, 0)
        return cart_response(request)
