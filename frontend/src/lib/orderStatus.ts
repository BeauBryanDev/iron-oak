import type { OrderStatus } from "@/lib/api/types";

export const ORDER_STATUS_LABEL: Record<OrderStatus, string> = {
  DRAFT: "Draft",
  PENDING_PAYMENT: "Awaiting payment",
  PAYMENT_FAILED: "Payment failed",
  EXPIRED: "Expired",
  CONFIRMED: "Paid and confirmed",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
  CANCELLED: "Cancelled",
};

export const ORDER_STATUS_HELP: Record<OrderStatus, string> = {
  DRAFT: "This order has not been submitted yet.",
  PENDING_PAYMENT: "Your items are being held for you until payment is made.",
  PAYMENT_FAILED: "The last payment attempt did not go through. You can try again while your items are held.",
  EXPIRED: "The hold on your items ran out before payment was received, so the items were released.",
  CONFIRMED: "Thank you. We have your payment and are preparing your order.",
  IN_PROGRESS: "Your order is being prepared.",
  COMPLETED: "Your order is complete.",
  CANCELLED: "This order was cancelled.",
};
