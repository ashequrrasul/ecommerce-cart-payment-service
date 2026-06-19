package com.goldenpath.cartpayment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@SpringBootApplication
public class CartPaymentApplication {
  public static void main(String[] args) {
    SpringApplication.run(CartPaymentApplication.class, args);
  }
}

@RestController
@Validated
class CartController {
  private final JdbcTemplate jdbc;

  CartController(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping("/healthz")
  public StatusResponse healthz() {
    return new StatusResponse("ok", "cart-payment-service");
  }

  @GetMapping("/cart/{customerId}")
  public CartResponse getCart(@PathVariable String customerId) {
    ensureCart(customerId);
    return loadCart(customerId);
  }

  @PostMapping("/cart/{customerId}/items")
  @ResponseStatus(HttpStatus.CREATED)
  public CartResponse addItem(@PathVariable String customerId, @Valid @RequestBody AddCartItemRequest request) {
    ensureCart(customerId);
    jdbc.update(
        """
        INSERT INTO cart_payment_service.cart_items (
          customer_id, product_id, product_name, unit_price, quantity
        )
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT (customer_id, product_id)
        DO UPDATE SET
          quantity = cart_payment_service.cart_items.quantity + EXCLUDED.quantity,
          product_name = EXCLUDED.product_name,
          unit_price = EXCLUDED.unit_price,
          updated_at = now()
        """,
        customerId,
        request.productId(),
        request.productName(),
        request.unitPrice(),
        request.quantity());
    return loadCart(customerId);
  }

  @DeleteMapping("/cart/{customerId}/items/{productId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void removeItem(@PathVariable String customerId, @PathVariable long productId) {
    jdbc.update(
        "DELETE FROM cart_payment_service.cart_items WHERE customer_id = ? AND product_id = ?",
        customerId,
        productId);
  }

  @PostMapping("/checkout")
  @ResponseStatus(HttpStatus.CREATED)
  public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
    CartResponse cart = loadCart(request.customerEmail());
    if (cart.items().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "Cart is empty");
    }

    String reference = "mock_" + UUID.randomUUID();
    Long paymentId = jdbc.queryForObject(
        """
        INSERT INTO cart_payment_service.payments (customer_id, status, amount, provider_reference)
        VALUES (?, 'succeeded', ?, ?)
        RETURNING id
        """,
        Long.class,
        request.customerEmail(),
        cart.total(),
        reference);
    jdbc.update("DELETE FROM cart_payment_service.cart_items WHERE customer_id = ?", request.customerEmail());

    return new CheckoutResponse(paymentId, "succeeded", reference, cart.total());
  }

  @GetMapping("/payments/{paymentId}")
  public PaymentResponse getPayment(@PathVariable long paymentId) {
    try {
      return jdbc.queryForObject(
          """
          SELECT id, customer_id, status, amount, provider_reference
          FROM cart_payment_service.payments
          WHERE id = ?
          """,
          (rs, rowNum) -> new PaymentResponse(
              rs.getLong("id"),
              rs.getString("customer_id"),
              rs.getString("status"),
              rs.getBigDecimal("amount"),
              rs.getString("provider_reference")),
          paymentId);
    } catch (EmptyResultDataAccessException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
    }
  }

  private void ensureCart(String customerId) {
    jdbc.update(
        """
        INSERT INTO cart_payment_service.carts (customer_id)
        VALUES (?)
        ON CONFLICT (customer_id)
        DO UPDATE SET updated_at = now()
        """,
        customerId);
  }

  private CartResponse loadCart(String customerId) {
    List<CartItemResponse> items = jdbc.query(
        """
        SELECT product_id, product_name, unit_price, quantity, unit_price * quantity AS line_total
        FROM cart_payment_service.cart_items
        WHERE customer_id = ?
        ORDER BY product_name
        """,
        (rs, rowNum) -> new CartItemResponse(
            rs.getLong("product_id"),
            rs.getString("product_name"),
            rs.getBigDecimal("unit_price"),
            rs.getInt("quantity"),
            rs.getBigDecimal("line_total")),
        customerId);
    BigDecimal total = items.stream()
        .map(CartItemResponse::lineTotal)
        .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new CartResponse(customerId, items, total);
  }
}

record StatusResponse(String status, String service) {}

record AddCartItemRequest(
    @Min(1) long productId,
    @NotBlank String productName,
    @DecimalMin("0.01") BigDecimal unitPrice,
    @Min(1) int quantity) {}

record CheckoutRequest(@Email String customerEmail) {}

record CartItemResponse(
    long productId,
    String productName,
    BigDecimal unitPrice,
    int quantity,
    BigDecimal lineTotal) {}

record CartResponse(String customerId, List<CartItemResponse> items, BigDecimal total) {}

record CheckoutResponse(Long paymentId, String status, String providerReference, BigDecimal amount) {}

record PaymentResponse(Long id, String customerId, String status, BigDecimal amount, String providerReference) {}
