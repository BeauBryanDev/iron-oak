import { authed } from "@/lib/auth/adminSession";
import type {
  OrderChannel,
  OrderItemType,
  OrderResponse,
  OrderStatus,
  Paged,
  PaymentResponse,
  PaymentStatus,
  ShipmentStatus,
} from "./types";

/**
 * Staff order and payment calls (/api/admin/orders, /api/admin/payments). The backend decides
 * every money and status rule; its 409/422 text is the answer to show.
 */

export interface AdminOrderQuery {
  /** repeatable: any of these */
  status?: OrderStatus[];
  channel?: OrderChannel;
  /** orders with at least one line of this type */
  itemType?: OrderItemType;
  /** inclusive UTC dates, yyyy-MM-dd; from after to is a 422 */
  from?: string;
  to?: string;
  /** order number, customer name or email, or an exact order id */
  q?: string;
  page?: number;
  size?: number;
  sort?: string;
}

/** Newest first unless sort says otherwise. */
export function adminListOrders(query: AdminOrderQuery = {}): Promise<Paged<OrderResponse>> {
  return authed("/api/admin/orders", { query: { ...query } });
}

export function adminGetOrder(id: number): Promise<OrderResponse> {
  return authed(`/api/admin/orders/${id}`);
}

/** Only the transitions OrderService allows; CANCELLED restocks but does not refund. */
export function adminUpdateOrderStatus(id: number, status: OrderStatus): Promise<OrderResponse> {
  return authed(`/api/admin/orders/${id}/status`, { method: "PATCH", body: { status } });
}

/** Freight quote for an ON_REQUEST order (unlocks payment); refused once anything is paid. */
export function adminSetShippingCost(id: number, shippingCost: number): Promise<OrderResponse> {
  return authed(`/api/admin/orders/${id}/shipping`, { method: "PATCH", body: { shippingCost } });
}

/** Forward only: SHIPPED (carrier required) -> IN_TRANSIT -> DELIVERED. Same status = correct carrier/tracking. */
export function adminUpdateShipment(
  id: number,
  body: { status: ShipmentStatus; carrier?: string; trackingNumber?: string },
): Promise<OrderResponse> {
  return authed(`/api/admin/orders/${id}/shipment`, { method: "PATCH", body });
}

export function adminOrderPayments(orderId: number): Promise<PaymentResponse[]> {
  return authed(`/api/admin/orders/${orderId}/payments`);
}

/** A manual (non-Stripe) payment for the unpaid balance; it starts PENDING. */
export function adminCreatePayment(body: {
  orderId: number;
  provider: string;
  providerReference?: string;
}): Promise<PaymentResponse> {
  return authed("/api/admin/payments", { method: "POST", body });
}

/** PAID confirms the order once it covers the total; REFUNDED is refused for Stripe payments. */
export function adminUpdatePaymentStatus(id: number, status: PaymentStatus): Promise<PaymentResponse> {
  return authed(`/api/admin/payments/${id}/status`, { method: "PATCH", body: { status } });
}

/** Full Stripe refund (moves the money), then REFUNDED. Does not cancel the order. */
export function adminRefundPayment(id: number, reason?: string): Promise<PaymentResponse> {
  return authed(`/api/admin/payments/${id}/refund`, { method: "POST", body: { reason } });
}
