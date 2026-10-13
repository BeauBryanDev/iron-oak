import type {
  OrderChannel,
  OrderItemType,
  OrderStatus,
  PaymentStatus,
  ShipmentStatus,
  ShippingMode,
  ShippingSource,
} from "@/lib/api/types";

/** Staff-facing wording for backend enums. */

export const CHANNEL_LABEL: Record<OrderChannel, string> = {
  WEB_CHECKOUT: "Web",
  PIPER: "Piper",
  ADMIN_MANUAL: "Staff",
  AGENT_CHAT: "Web",
};

export const ITEM_TYPE_LABEL: Record<OrderItemType, string> = {
  PRODUCT: "Product",
  MACHINE: "Machine",
  SERVICE: "Service",
  END_MILL: "End mill",
};

export const SHIPMENT_LABEL: Record<ShipmentStatus, string> = {
  SHIPPED: "Shipped",
  IN_TRANSIT: "In transit",
  DELIVERED: "Delivered",
};

export const SHIPMENT_STEPS: ShipmentStatus[] = ["SHIPPED", "IN_TRANSIT", "DELIVERED"];

export const SHIPPING_MODE_LABEL: Record<ShippingMode, string> = {
  NONE: "Nothing to ship",
  ROAD: "Road",
  AIR: "Air",
  SEA: "Sea",
};

export const SHIPPING_SOURCE_LABEL: Record<ShippingSource, string> = {
  GOOGLE: "Google road distance",
  MANUAL: "Staff road distance",
  ESTIMATE: "Straight-line estimate",
  FIXED: "Fixed air price",
  STAFF: "Quoted by staff",
};

export const PAYMENT_TONE: Record<PaymentStatus, string> = {
  PENDING: "border-amber-400/60 bg-amber-400/10 text-amber-300",
  PROCESSING: "border-sky-400/60 bg-sky-400/10 text-sky-300",
  PAID: "border-emerald-400/60 bg-emerald-400/10 text-emerald-300",
  FAILED: "border-red-400/60 bg-red-500/10 text-red-300",
  EXPIRED: "border-cream/30 text-cream/50",
  CANCELLED: "border-cream/30 text-cream/50",
  REFUNDED: "border-violet-400/60 bg-violet-400/10 text-violet-300",
};

/** Mirrors OrderService.TRANSITIONS (staff changes); the backend still decides. */
export const ORDER_TRANSITIONS: Record<OrderStatus, OrderStatus[]> = {
  DRAFT: ["CONFIRMED", "CANCELLED"],
  PENDING_PAYMENT: ["CANCELLED"],
  PAYMENT_FAILED: ["CANCELLED"],
  EXPIRED: [],
  CONFIRMED: ["IN_PROGRESS", "CANCELLED"],
  IN_PROGRESS: ["COMPLETED", "CANCELLED"],
  COMPLETED: [],
  CANCELLED: [],
};

/** Quick filters on the orders list. */
export const ORDER_PRESETS: { key: string; label: string; status: OrderStatus[] }[] = [
  { key: "all", label: "All", status: [] },
  { key: "fulfil", label: "To fulfil", status: ["CONFIRMED", "IN_PROGRESS"] },
  { key: "unpaid", label: "Awaiting payment", status: ["PENDING_PAYMENT", "PAYMENT_FAILED"] },
  { key: "completed", label: "Completed", status: ["COMPLETED"] },
  { key: "cancelled", label: "Cancelled", status: ["CANCELLED"] },
  { key: "expired", label: "Expired", status: ["EXPIRED"] },
];

const dateTime = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric", hour: "2-digit", minute: "2-digit" });
const dateOnly = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" });

export function formatDateTime(iso: string | null | undefined): string | null {
  return iso ? dateTime.format(new Date(iso)) : null;
}

/** For yyyy-MM-dd values (no time zone shift). */
export function formatDate(isoDate: string | null | undefined): string | null {
  return isoDate ? dateOnly.format(new Date(`${isoDate.slice(0, 10)}T00:00:00Z`)) : null;
}
