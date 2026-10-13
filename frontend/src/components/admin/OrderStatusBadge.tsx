import type { OrderStatus } from "@/lib/api/types";
import { ORDER_STATUS_LABEL } from "@/lib/orderStatus";

const TONE: Record<OrderStatus, string> = {
  DRAFT: "border-cream/30 text-cream/60",
  PENDING_PAYMENT: "border-amber-400/60 bg-amber-400/10 text-amber-300",
  PAYMENT_FAILED: "border-red-400/60 bg-red-500/10 text-red-300",
  EXPIRED: "border-cream/30 bg-white/5 text-cream/50",
  CONFIRMED: "border-sky-400/60 bg-sky-400/10 text-sky-300",
  IN_PROGRESS: "border-orange/70 bg-orange/15 text-orange-light",
  COMPLETED: "border-emerald-400/60 bg-emerald-400/10 text-emerald-300",
  CANCELLED: "border-red-400/40 text-red-300/80",
};

/** Staff wording: "Paid" rather than the customer-facing "Paid and confirmed". */
const STAFF_LABEL: Partial<Record<OrderStatus, string>> = { CONFIRMED: "Paid" };

export default function OrderStatusBadge({ status }: { status: OrderStatus }) {
  return (
    <span className={`inline-block whitespace-nowrap border px-2 py-0.5 text-xs font-bold ${TONE[status]}`}>
      {STAFF_LABEL[status] ?? ORDER_STATUS_LABEL[status]}
    </span>
  );
}
