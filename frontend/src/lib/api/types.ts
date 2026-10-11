/**
 * TypeScript mirror of the backend DTOs (com.ironoak.dto). The backend is the source of
 * truth: when a record changes there, change it here. BigDecimal is serialized as a JSON
 * number; OffsetDateTime as an ISO-8601 string.
 */

// ---------- shared ----------

export interface PageMeta {
  size: number;
  number: number;
  totalElements: number;
  totalPages: number;
}

/** Spring Data PagedModel. */
export interface Paged<T> {
  content: T[];
  page: PageMeta;
}

/** RFC 7807 body produced by GlobalExceptionHandler. */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  instance?: string;
}

// ---------- enums (domain/enums) ----------

export type OrderStatus =
  | "DRAFT"
  | "PENDING_PAYMENT"
  | "PAYMENT_FAILED"
  | "EXPIRED"
  | "CONFIRMED"
  | "IN_PROGRESS"
  | "COMPLETED"
  | "CANCELLED";

/** AGENT_CHAT is a deprecated legacy value the backend never writes any more. */
export type OrderChannel = "WEB_CHECKOUT" | "PIPER" | "ADMIN_MANUAL" | "AGENT_CHAT";
export type OrderItemType = "PRODUCT" | "SERVICE" | "MACHINE";
export type PricingType = "FIXED" | "HOURLY" | "QUOTE";
export type ShippingStatus = "QUOTED" | "ON_REQUEST";
export type ShippingMode = "NONE" | "SEA" | "ROAD" | "AIR";
export type ShippingSource = "GOOGLE" | "MANUAL" | "ESTIMATE" | "FIXED" | "STAFF";

// ---------- catalog (public) ----------

export interface ProductResponse {
  id: number;
  sku: string;
  name: string;
  brand: string | null;
  category: string;
  visionName: string;
  description: string | null;
  price: number;
  stockQuantity: number;
  inStock: boolean;
  warrantyMonths: number;
  imageUrl: string | null;
  toolCategoryDisplayName: string | null;
  toolCategoryId: number | null;
}

export interface ToolCategoryResponse {
  id: number;
  modelLabel: string;
  displayName: string;
  synonyms: string[];
  description: string | null;
}

export interface MillingMachineResponse {
  id: number;
  modelCode: string;
  name: string;
  description: string | null;
  powerKw: number;
  spindleMinRpm: number;
  spindleMaxRpm: number;
  tableLengthMm: number;
  tableWidthMm: number;
  price: number;
  warrantyMonths: number;
  imageUrl: string | null;
}

/** Prices are starting prices; priceUnit says what they cover ("visit", "hour", "day"). */
export interface ServiceOfferingResponse {
  id: number;
  code: string;
  name: string;
  category: string;
  description: string | null;
  pricingType: PricingType;
  fixedPrice: number | null;
  hourlyRate: number | null;
  estimatedMinHours: number | null;
  estimatedMaxHours: number | null;
  priceUnit: string | null;
}

// ---------- orders and checkout (public) ----------

export interface OrderLineRequest {
  itemType: OrderItemType;
  /** product, service offering or milling machine id, per itemType */
  referenceId: number;
  quantity: number;
  /** required for HOURLY services, ignored otherwise */
  estimatedHours?: number | null;
}

/**
 * Web orders must carry customerName + customerEmail and, for products or machines,
 * country (ISO-2), city and shippingAddress.
 */
export interface CreateOrderRequest {
  customerName?: string;
  customerEmail?: string;
  customerPhone?: string;
  country?: string;
  province?: string;
  city?: string;
  shippingAddress?: string;
  items: OrderLineRequest[];
}

export interface OrderItemResponse {
  id: number;
  itemType: OrderItemType;
  referenceId: number;
  name: string;
  itemCode: string;
  quantity: number;
  estimatedHours: number | null;
  unitPrice: number;
  subtotal: number;
}

/** grandTotal = subtotal + shippingCost + taxes. */
export interface OrderResponse {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  channel: OrderChannel;
  customerName: string | null;
  customerEmail: string | null;
  phoneNumber: string | null;
  country: string | null;
  province: string | null;
  city: string | null;
  shippingAddress: string | null;
  currency: string;
  subtotal: number;
  shippingCost: number;
  shippingStatus: ShippingStatus;
  shippingMode: ShippingMode;
  shippingDistanceKm: number | null;
  shippingSource: ShippingSource | null;
  taxes: number;
  grandTotal: number;
  reservationExpiresAt: string | null;
  createdAt: string;
  updatedAt: string;
  items: OrderItemResponse[];
}

/** What anyone holding an order number may see: no name, email, phone or address. */
export interface PublicOrderLine {
  name: string;
  itemCode: string;
  quantity: number;
  unitPrice: number;
  subtotal: number;
}

export interface PublicOrderResponse {
  orderNumber: string;
  status: OrderStatus;
  shippingStatus: ShippingStatus;
  /** true when the order can be paid right now (unpaid, shipping quoted, stock hold valid) */
  payable: boolean;
  currency: string;
  subtotal: number;
  shippingCost: number;
  taxes: number;
  grandTotal: number;
  reservationExpiresAt: string | null;
  createdAt: string;
  items: PublicOrderLine[];
}

export interface ShippingQuoteRequest {
  country?: string;
  city?: string;
  province?: string;
  items: OrderLineRequest[];
}

/** USD. ON_REQUEST means staff will quote the shipping (total is 0 until then). */
export interface ShippingQuoteResponse {
  currency: string;
  country: string | null;
  status: ShippingStatus;
  mode: ShippingMode;
  domestic: boolean;
  distanceKm: number | null;
  source: ShippingSource | null;
  estimated: boolean;
  total: number;
  note: string | null;
}

export interface CheckoutSessionResponse {
  orderNumber: string;
  paymentId: number;
  /** send the customer's browser here */
  checkoutUrl: string;
  expiresAt: string;
}

// ---------- admin auth ----------

/** Returned by login, refresh and change-password. refreshToken is single-use. */
export interface AdminLoginResponse {
  token: string;
  tokenType: string;
  expiresInMinutes: number;
  refreshToken: string;
  refreshExpiresAt: string;
  username: string;
  fullName: string | null;
  passwordChangeRequired: boolean;
}

export interface AdminProfileResponse {
  username: string;
  email: string;
  fullName: string | null;
  lastLoginAt: string | null;
}

export interface TopProduct {
  productId: number;
  name: string;
  unitsSold: number;
}

export interface DashboardKPIResponse {
  openOrders: number;
  pendingPaymentOrders: number;
  completedOrders: number;
  cancelledOrders: number;
  expiredOrders: number;
  webCheckoutOrders: number;
  piperOrders: number;
  completedRevenue: number;
  pendingComplaints: number;
  requestedBookings: number;
  openWarrantyClaims: number;
  openSupportTickets: number;
  topProducts: TopProduct[];
}
