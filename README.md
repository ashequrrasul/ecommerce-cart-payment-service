# Cart Payment Service

Java Spring Boot service for Phase 2 of the e-commerce Golden Path.

It uses the shared Cloud SQL database `app` and owns the `cart_payment_service` schema.

Endpoints:

```text
GET    /cart/{customerId}
POST   /cart/{customerId}/items
DELETE /cart/{customerId}/items/{productId}
POST   /checkout
GET    /payments/{paymentId}
```
