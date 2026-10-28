/**
 * TypeScript matches of the backend DTOs (com.ironoak.dto). The backend is the source of
 * truth: when a record changes there, change it here. BigDecimal is serialized as a JSON
 * number; OffsetDateTime as an ISO-8601 string.
 */

//  shared 

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

//  enums (domain/enums) 

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
/** END_MILL since V13 (end mills are sold like products). */
export type OrderItemType = "PRODUCT" | "SERVICE" | "MACHINE" | "END_MILL";
export type PricingType = "FIXED" | "HOURLY" | "QUOTE";
export type ShippingStatus = "QUOTED" | "ON_REQUEST";
export type ShippingMode = "NONE" | "SEA" | "ROAD" | "AIR";
export type ShippingSource = "GOOGLE" | "MANUAL" | "ESTIMATE" | "FIXED" | "STAFF";
/** null on an order until staff ship it; moves forward only. */
export type ShipmentStatus = "SHIPPED" | "IN_TRANSIT" | "DELIVERED";
export type BookingStatus = "REQUESTED" | "CONFIRMED" | "COMPLETED" | "CANCELLED";
export type ServiceQuoteStatus = "REQUESTED" | "QUOTED" | "DECLINED";
export type PaymentStatus = "PENDING" | "PROCESSING" | "PAID" | "FAILED"
  | "EXPIRED" | "CANCELLED" | "REFUNDED";
export type ClaimStatus = "OPEN" | "IN_REVIEW" | "APPROVED" | "REJECTED" | "RESOLVED";
export type ComplaintStatus = "PENDING" | "RESOLVED" | "REJECTED";
export type TicketStatus = "OPEN" | "IN_PROGRESS" | "CLOSED";
export type CutDirection = "Right-Hand" | "Left-Hand";

//  catalog (public) 

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
  /** shipping weight of one unit in kg; null until staff enter it */
  weightKg: number | null;
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
  /** packed shipping weight (kg) and volume (m3); null until staff enter them */
  weightKg: number | null;
  volumeM3: number | null;
  imageUrl: string | null;
}

/**
 * GET /api/end-mills (paged), /{id}, /sku/{sku}, /by-vision/{visionLabel}. Public view: no
 * stock count or vision label, only inStock. Sizes in mm, weight in grams.
 */
export interface EndMillResponse {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  fluteType: string;
  cutDirection: CutDirection;
  cuttingDiameterMm: number;
  radiusMm: number;
  material: string;
  overallLengthMm: number;
  weightG: number | null;
  price: number;
  maxRpm: number | null;
  inStock: boolean;
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

//  orders and checkout (public) 

export interface OrderLineRequest {
  itemType: OrderItemType;
  /** product, service offering, milling machine or end mill id, per itemType */
  referenceId: number;
  quantity: number;
  /** required for HOURLY services, ignored otherwise */
  estimatedHours?: number | null;
}

/**
 * Web orders must carry customerName + customerEmail and, for products, machines or end mills,
 * country (ISO-2), city and shippingAddress. A service line needs country "CO" and city
 * Bogota or Medellin (technicians only work there).
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

/**
 * Where an order's parcel is (null when nothing ships). status is null until staff ship it.
 * expectedDeliveryDate (yyyy-MM-dd) = shipped date + estimatedDeliveryDays, once shipped.
 */
export interface ShipmentInfo {
  status: ShipmentStatus | null;
  carrier: string | null;
  trackingNumber: string | null;
  shippedAt: string | null;
  deliveredAt: string | null;
  estimatedDeliveryDays: number | null;
  expectedDeliveryDate: string | null;
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
  shipment: ShipmentInfo | null;
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
  /** carrier, tracking number, status and expected delivery date; null when nothing ships */
  shipment: ShipmentInfo | null;
}

export interface ShippingQuoteRequest {
  country?: string;
  city?: string;
  province?: string;
  items: OrderLineRequest[];
}

/**
 * USD. total is the shipping cost; ON_REQUEST means staff will quote it (0 until then).
 * itemsSubtotal, taxRate and taxes use the same rules as the real order (items only, only
 * above 100.00); estimatedTotal = itemsSubtotal + total + taxes, i.e. what Stripe will charge
 * (without shipping while ON_REQUEST). Products, machines and end mills only.
 */
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
  itemsSubtotal: number;
  taxRate: number;
  taxes: number;
  estimatedTotal: number;
}

export interface CheckoutSessionResponse {
  orderNumber: string;
  paymentId: number;
  /** send the customer's browser here */
  checkoutUrl: string;
  expiresAt: string;
}

//  admin auth 

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
  /** every paid order (CONFIRMED, IN_PROGRESS, COMPLETED), from the moment the customer pays */
  paidRevenue: number;
  pendingComplaints: number;
  requestedBookings: number;
  openWarrantyClaims: number;
  openSupportTickets: number;
  topProducts: TopProduct[];
}

//  technician bookings and service quotes (public; Bogota and Medellin, Colombia only) 

/** POST /api/bookings. country must be "CO" and city Bogota or Medellin, else 422. */
export interface CreateBookingRequest {
  customerName: string;
  customerEmail: string;
  customerPhone?: string;
  serviceOfferingId: number;
  locationAddress: string;
  country: string;
  city: string;
  /** ISO-8601, in the future */
  scheduledAt: string;
  machineModel?: string;
  notes?: string;
}

/**
 * A technician visit. After staff confirm it, orderNumber is what the customer pays with
 * (GET /api/orders/{orderNumber}, POST /api/orders/{orderNumber}/checkout-session).
 */
export interface BookingResponse {
  id: number;
  /** Public reference, SB-YYYYMMDD-XXXXXXXX (V14). */
  bookingNumber: string;
  status: BookingStatus;
  serviceCode: string;
  serviceName: string;
  customerName: string;
  locationAddress: string;
  country: string | null;
  city: string | null;
  scheduledAt: string;
  machineModel: string | null;
  notes: string | null;
  cancelReason: string | null;
  orderId: number | null;
  orderNumber: string | null;
  orderStatus: OrderStatus | null;
  grandTotal: number | null;
  createdAt: string;
  updatedAt: string;
}

/** POST /api/service-quotes, for QUOTE-priced services (e.g. SPINDLE_TOOLING_SERVICE). */
export interface CreateServiceQuoteRequest {
  serviceCode: string;
  customerName: string;
  customerEmail: string;
  customerPhone?: string;
  country: string;
  city: string;
  description: string;
}

/** Once QUOTED, orderNumber pays it; orderStatus and grandTotal (price + tax) follow that order. */
export interface ServiceQuoteResponse {
  id: number;
  status: ServiceQuoteStatus;
  serviceCode: string;
  serviceName: string;
  customerName: string;
  customerEmail: string;
  customerPhone: string | null;
  country: string;
  city: string | null;
  description: string;
  quotedPrice: number | null;
  staffNote: string | null;
  orderId: number | null;
  orderNumber: string | null;
  orderStatus: OrderStatus | null;
  grandTotal: number | null;
  quotedAt: string | null;
  createdAt: string;
  updatedAt: string;
}

//  admin: products (/api/admin/products)

/** Staff view of a product (AdminProductResponse): includes inactive rows and the stock count. */
export interface AdminProductResponse {
  id: number;
  sku: string;
  name: string;
  brand: string | null;
  category: string;
  description: string | null;
  price: number;
  stockQuantity: number;
  warrantyMonths: number;
  weightKg: number | null;
  volumeM3: number | null;
  imageUrl: string | null;
  isActive: boolean;
  toolCategoryId: number;
  toolCategoryDisplayName: string;
  visionName: string;
  createdAt: string;
}

//  admin: end mills (/api/admin/end-mills)

/** Staff view: adds stock, vision label (YOLOv11 class), active flag and timestamps. */
export interface AdminEndMillResponse {
  id: number;
  sku: string;
  name: string;
  description: string | null;
  fluteType: string;
  cutDirection: CutDirection;
  cuttingDiameterMm: number;
  radiusMm: number;
  material: string;
  overallLengthMm: number;
  weightG: number | null;
  price: number;
  maxRpm: number | null;
  stockQuantity: number;
  visionLabel: string | null;
  imageUrl: string | null;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

/**
 * POST and PUT /api/admin/end-mills. stockQuantity only on create (default 0); sending it on
 * update is a 422 (use PATCH .../stock). radiusMm <= cuttingDiameterMm / 2. visionLabel is
 * lowercase_with_underscores and unique; sku unique (any case). isActive null = unchanged on update.
 */
export interface EndMillRequest {
  sku: string;
  name: string;
  description?: string | null;
  fluteType: string;
  cutDirection?: CutDirection;
  cuttingDiameterMm: number;
  radiusMm: number;
  material: string;
  overallLengthMm: number;
  weightG?: number | null;
  price: number;
  maxRpm?: number | null;
  stockQuantity?: number;
  visionLabel?: string | null;
  imageUrl?: string | null;
  isActive?: boolean | null;
}

/** PATCH .../active (products, machines, services, end mills, staff). */
export interface UpdateActiveRequest {
  isActive: boolean;
}

/** PATCH .../stock: positive adds, negative removes; never below zero (422). */
export interface AdjustStockRequest {
  delta: number;
}

//  admin: orders, bookings, service quotes 

/**
 * PATCH /api/admin/orders/{id}/shipment, paid orders only. SHIPPED needs a carrier; the status
 * moves forward only; DELIVERED completes the order and starts the warranty. null = unchanged.
 */
export interface UpdateShipmentRequest {
  status: ShipmentStatus;
  carrier?: string | null;
  trackingNumber?: string | null;
}

/**
 * POST /api/admin/bookings/{id}/confirm, after checking a technician is free. estimatedHours
 * for HOURLY services, price for QUOTE services, neither for FIXED. Creates the payable order.
 */
export interface ConfirmBookingRequest {
  estimatedHours?: number | null;
  price?: number | null;
}

/** POST /api/admin/service-quotes/{id}/quote: creates the order the customer pays. */
export interface PriceServiceQuoteRequest {
  price: number;
  note?: string | null;
}

/** POST /api/admin/service-quotes/{id}/decline: the note is shown to the customer. */
export interface DeclineServiceQuoteRequest {
  note: string;
}

//  admin: customers and shipping routes

/** GET /api/admin/customers (paged, newest first). Guest identities; there is no customer login. */
export interface CustomerResponse {
  id: number;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  createdAt: string;
}

/** POST and PUT /api/admin/customers. On PUT, an omitted email/phone/address is cleared. */
export interface CustomerRequest {
  name: string;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
}

/** GET /api/admin/shipping/routes (all rows, by country then city). distanceKm is road km from the warehouse. */
export interface ShippingRouteResponse {
  id: number;
  /** ISO code, e.g. CO */
  country: string;
  city: string;
  distanceKm: number;
  source: ShippingSource;
  updatedAt: string;
}

/** PUT /api/admin/shipping/routes: adds or overrides a city's road distance (source MANUAL). */
export interface ManualRouteRequest {
  country: string;
  city: string;
  distanceKm: number;
}

/** POST /api/admin/shipping/routes/refresh: counts over the known cities, plus Google calls left today. */
export interface RouteRefreshResult {
  known: number;
  alreadyStored: number;
  fetched: number;
  notFound: number;
  googleCallsLeft: number;
}

//  support tickets (public contact form, Piper escalations)

/** POST /api/support-tickets. reason is the topic (max 200), summary the message. */
export interface CreateSupportTicketRequest {
  customerEmail?: string;
  chatSessionId?: number;
  reason: string;
  summary: string;
}

/** customerName is null for guests who gave only an email, or nothing at all. */
export interface SupportTicketResponse {
  id: number;
  /** Public reference, ST-YYYYMMDD-XXXXXXXX (V14). */
  ticketNumber: string;
  status: TicketStatus;
  customerName: string | null;
  customerEmail: string | null;
  chatSessionId: number | null;
  reason: string;
  summary: string;
  createdAt: string;
}

export interface PaymentResponse {
  id: number;
  orderId: number;
  amount: number;
  currency: string;
  status: PaymentStatus;
  provider: string;
  providerReference: string | null;
  checkoutSessionId: string | null;
  paymentIntentId: string | null;
  failureCode: string | null;
  failureMessage: string | null;
  createdAt: string;
  updatedAt: string;
  paidAt: string | null;
}

//  admin: staff accounts and audit trail 

/** GET /api/admin/staff. passwordChangeRequired = still on a temporary password. */
export interface StaffResponse {
  id: number;
  username: string;
  email: string;
  fullName: string | null;
  active: boolean;
  passwordChangeRequired: boolean;
  createdAt: string;
  lastLoginAt: string | null;
}

/** POST /api/admin/staff. temporaryPassword: 12-72 characters, must not contain the username. */
export interface CreateStaffRequest {
  username: string;
  email: string;
  fullName?: string | null;
  temporaryPassword: string;
}

/** POST /api/admin/staff/{id}/reset-password (not for your own account). */
export interface ResetStaffPasswordRequest {
  temporaryPassword: string;
}

export type AuditAction =
  | "LOGIN_SUCCESS" | "LOGIN_FAILED" | "LOGOUT_ALL" | "PASSWORD_CHANGED" | "PASSWORD_CHANGE_FAILED"
  | "ADMIN_BOOTSTRAP" | "STAFF_CREATE" | "STAFF_DISABLE" | "STAFF_ENABLE" | "STAFF_PASSWORD_RESET"
  | "PRODUCT_CREATE" | "PRODUCT_UPDATE" | "PRODUCT_ACTIVE" | "STOCK_ADJUST"
  | "MACHINE_CREATE" | "MACHINE_UPDATE" | "MACHINE_ACTIVE"
  | "SERVICE_CREATE" | "SERVICE_UPDATE" | "SERVICE_ACTIVE" | "TOOL_CATEGORY_UPDATE"
  | "END_MILL_CREATE" | "END_MILL_UPDATE" | "END_MILL_ACTIVE"
  | "CUSTOMER_CREATE" | "CUSTOMER_UPDATE" | "CUSTOMER_DELETE"
  | "ORDER_CREATE" | "ORDER_STATUS" | "ORDER_SHIPPING_QUOTE" | "ORDER_SHIPMENT"
  | "PAYMENT_CREATE" | "PAYMENT_STATUS" | "REFUND"
  | "SERVICE_QUOTE_PRICED" | "SERVICE_QUOTE_DECLINED" | "BOOKING_CONFIRMED" | "BOOKING_STATUS"
  | "SHIPPING_ROUTE_SET" | "SHIPPING_ROUTES_REFRESH";

/** GET /api/admin/audit (paged, newest first). oldValue/newValue hold only the changed fields. */
export interface AuditLogResponse {
  id: number;
  createdAt: string;
  adminUserId: number | null;
  username: string;
  action: AuditAction;
  /** e.g. PRODUCT, END_MILL, ORDER, PAYMENT, BOOKING, ADMIN_USER */
  resourceType: string | null;
  resourceId: string | null;
  oldValue: Record<string, unknown> | null;
  newValue: Record<string, unknown> | null;
  ipAddress: string | null;
  userAgent: string | null;
}
