# LocalRush — Entity relationship diagram

```mermaid
erDiagram
  User ||--o| CustomerProfile : has
  User ||--o| RiderProfile : has
  User ||--o{ Restaurant : owns
  User ||--o{ Address : has
  User ||--o{ Device : registers
  User ||--o{ OTPRequest : requests
  User ||--o{ Notification : receives
  User ||--o{ SupportTicket : opens
  User ||--o{ AuditLog : acts

  Restaurant ||--o{ RestaurantHours : opens
  Restaurant ||--o{ RestaurantHoliday : closes
  Restaurant ||--o{ RestaurantDocument : uploads
  Restaurant }o--o{ Cuisine : serves
  Restaurant }o--o{ RestaurantCategory : listed_in
  Restaurant ||--o{ MenuCategory : has
  Restaurant ||--o{ MenuItem : sells
  MenuCategory ||--o{ MenuItem : groups
  MenuItem ||--o{ VariantGroup : has
  VariantGroup ||--o{ MenuVariant : options
  MenuItem ||--o{ AddonGroup : has
  AddonGroup ||--o{ MenuAddon : options
  Restaurant ||--o{ Banner : promotes
  Restaurant ||--o{ Coupon : funds

  User ||--o| Cart : owns
  Cart ||--o{ CartItem : contains
  CartItem }o--|| MenuItem : refers
  CartItem }o--o| MenuVariant : chooses
  CartItem }o--o{ MenuAddon : adds

  User ||--o{ Order : places
  Restaurant ||--o{ Order : receives
  RiderProfile ||--o{ Order : delivers
  Address ||--o{ Order : delivered_to
  Order ||--o{ OrderItem : contains
  Order ||--o{ OrderStatusHistory : logs
  Order ||--o{ RiderAssignment : offers
  RiderProfile ||--o{ RiderAssignment : receives
  Order ||--o{ Payment : paid_by
  Payment ||--o{ Refund : refunded_by
  Order ||--o| CouponUsage : uses
  Coupon ||--o{ CouponUsage : used
  Order ||--o| Review : reviewed
  Order ||--o{ ChatMessage : chat
  Order ||--o{ RiderLocation : track
  Order ||--o| SettlementLine : settles
  Order ||--o{ RiderEarning : earns
  Order ||--o{ SupportTicket : disputes
  SupportTicket ||--o{ TicketMessage : thread

  RiderProfile ||--o{ RiderDocument : uploads
  RiderProfile ||--o{ RiderLocation : history
  RiderProfile ||--o{ RiderEarning : ledger
  RiderProfile ||--o{ RiderPayout : withdraws
  RiderPayout ||--o{ RiderEarning : settles
  Restaurant ||--o{ RestaurantSettlement : paid
  RestaurantSettlement ||--o{ SettlementLine : batches
  PlatformSettings ||--|| PlatformSettings : singleton
  PlatformLedger ||--|| PlatformLedger : daily_rollup
  WebhookEvent ||--|| WebhookEvent : idempotency

  User { uuid id PK; string phone UK; string email UK; string role; bool is_suspended; int token_version }
  Order { uuid id PK; string order_number UK; string status; decimal total_amount; decimal commission_amount; decimal restaurant_payout; decimal rider_payout; string pickup_otp; string delivery_otp; geography delivery_location; string idempotency_key UK }
  RiderProfile { uuid id PK; string status; bool is_online; bool is_on_delivery; geography current_location; decimal wallet_balance; decimal cod_cash_in_hand }
  Restaurant { uuid id PK; string slug UK; string status; geography location; decimal commission_percent; bool is_open }
  Payment { uuid id PK; string provider_order_id; string provider_payment_id; string status; decimal amount }
```

Spatial indexes: `Address.location`, `Restaurant.location`, `RiderProfile.current_location`, `Order.delivery_location`, `Order.restaurant_location`, `RiderLocation.location` (PostGIS `geography(Point,4326)` + GiST).
Key constraints: one accepted `RiderAssignment` per order (partial unique), unique `(provider, provider_payment_id)`, unique `(user, dedupe_key)` notifications, unique `(provider, event_id)` webhook events, unique `(restaurant, period_start, period_end)` settlements. Soft deletes on `Restaurant`, `MenuItem`, `Address`.
