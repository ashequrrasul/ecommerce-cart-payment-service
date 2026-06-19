CREATE SCHEMA IF NOT EXISTS cart_payment_service;

CREATE TABLE IF NOT EXISTS cart_payment_service.carts (
  customer_id TEXT PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS cart_payment_service.cart_items (
  id BIGSERIAL PRIMARY KEY,
  customer_id TEXT NOT NULL REFERENCES cart_payment_service.carts(customer_id) ON DELETE CASCADE,
  product_id BIGINT NOT NULL,
  product_name TEXT NOT NULL,
  unit_price NUMERIC(12, 2) NOT NULL CHECK (unit_price > 0),
  quantity INTEGER NOT NULL CHECK (quantity > 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (customer_id, product_id)
);

CREATE TABLE IF NOT EXISTS cart_payment_service.payments (
  id BIGSERIAL PRIMARY KEY,
  customer_id TEXT NOT NULL,
  status TEXT NOT NULL,
  amount NUMERIC(12, 2) NOT NULL CHECK (amount >= 0),
  provider_reference TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
