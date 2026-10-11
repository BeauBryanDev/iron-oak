import { request } from "./http";
import type {
  CheckoutSessionResponse,
  CreateOrderRequest,
  OrderResponse,
  PublicOrderResponse,
  ShippingQuoteRequest,
  ShippingQuoteResponse,
} from "./types";

/** Shipping for a cart. Never calls Google on the backend; taxes are not included. */
export function quoteShipping(body: ShippingQuoteRequest, signal?: AbortSignal): Promise<ShippingQuoteResponse> {
  return request("/api/shipping/quote", { method: "POST", body, signal });
}

/**
 * Places a web order. The same Idempotency-Key returns the original order instead of taking
 * stock twice, so reuse a key only for an identical retry.
 */
export function createOrder(body: CreateOrderRequest, idempotencyKey: string): Promise<OrderResponse> {
  return request("/api/orders", {
    method: "POST",
    body,
    headers: { "Idempotency-Key": idempotencyKey },
  });
}

/** Public read by order number: no personal data. */
export function getOrderByNumber(orderNumber: string, signal?: AbortSignal): Promise<PublicOrderResponse> {
  return request(`/api/orders/${encodeURIComponent(orderNumber)}`, { signal });
}

/** Opens (or returns the still-open) Stripe Checkout page for an order. */
export function createCheckoutSession(orderNumber: string): Promise<CheckoutSessionResponse> {
  return request(`/api/orders/${encodeURIComponent(orderNumber)}/checkout-session`, { method: "POST" });
}
