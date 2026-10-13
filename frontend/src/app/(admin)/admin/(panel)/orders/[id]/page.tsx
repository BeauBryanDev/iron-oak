import { notFound } from "next/navigation";

import OrderDetail from "@/components/admin/orders/OrderDetail";

/** /admin/orders/{id}: the staff view of one order (data loads in the browser with the staff token). */
export default async function AdminOrderPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) notFound();
  return <OrderDetail id={Number(id)} />;
}
