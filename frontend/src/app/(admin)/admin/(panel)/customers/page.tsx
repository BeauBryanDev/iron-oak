import { Suspense } from "react";

import CustomersList from "@/components/admin/customers/CustomersList";

/** /admin/customers: filters live in the URL, read with useSearchParams (hence the Suspense). */
export default function AdminCustomersPage() {
  return (
    <Suspense fallback={<p className="text-cream/60">Loading customers…</p>}>
      <CustomersList />
    </Suspense>
  );
}
