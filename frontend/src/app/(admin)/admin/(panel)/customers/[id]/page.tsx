import { notFound } from "next/navigation";

import CustomerDetail from "@/components/admin/customers/CustomerDetail";

/** /admin/customers/{id}: one customer, editable, with their orders. */
export default async function AdminCustomerPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  if (!/^\d{1,18}$/.test(id)) notFound();
  return <CustomerDetail id={Number(id)} />;
}
