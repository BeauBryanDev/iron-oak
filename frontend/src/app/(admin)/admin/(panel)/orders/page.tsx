import { Suspense } from "react";

import OrdersList from "@/components/admin/orders/OrdersList";

/** /admin/orders: filters live in the URL, read with useSearchParams (hence the Suspense). */
export default function AdminOrdersPage() {
  return (
    <Suspense fallback={<p className="text-cream/60">Loading orders…</p>}>
      <OrdersList />
    </Suspense>
  );
}
