"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ArrowLeft, Pencil, RefreshCw, Trash2 } from "lucide-react";

import CustomerForm from "@/components/admin/customers/CustomerForm";
import OrderStatusBadge from "@/components/admin/OrderStatusBadge";
import { ConfirmDialog, ErrorNote, Facts, Panel, Skeleton, dangerButton, outlineButton, primaryButton } from "@/components/admin/ui";
import { CHANNEL_LABEL, formatDateTime } from "@/lib/admin/labels";
import { adminDeleteCustomer, adminGetCustomer, adminUpdateCustomer } from "@/lib/api/adminCustomers";
import { adminListOrders } from "@/lib/api/adminOrders";
import { ApiError } from "@/lib/api/http";
import type { CustomerRequest, CustomerResponse, OrderResponse } from "@/lib/api/types";
import { money } from "@/lib/format";

const RECENT_ORDERS = 10;

/**
 * The customer's orders, found by email (the order search matches the buyer's email). A
 * customer without an email has no reliable way to be matched, so nothing is listed.
 */
function CustomerOrders({ email }: { email: string | null }) {
  const [orders, setOrders] = useState<OrderResponse[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!email) return;
    let cancelled = false;
    adminListOrders({ q: email, size: RECENT_ORDERS, sort: "createdAt,desc" })
      .then((page) => {
        if (cancelled) return;
        const own = page.content.filter((o) => o.customerEmail?.toLowerCase() === email);
        setOrders(own);
        setTotal(own.length === page.content.length ? page.page.totalElements : own.length);
      })
      .catch((cause) => !cancelled && setError(cause instanceof ApiError ? cause.message : "Could not load orders."));
    return () => {
      cancelled = true;
    };
  }, [email]);

  const viewAll = email ? `/admin/orders?q=${encodeURIComponent(email)}` : null;

  return (
    <Panel
      title="ORDERS"
      subtitle={email ? `Newest ${RECENT_ORDERS} orders placed with ${email}` : undefined}
      actions={viewAll && total > RECENT_ORDERS ? (
        <Link href={viewAll} className="text-xs font-bold text-orange hover:text-orange-light">VIEW ALL ({total})</Link>
      ) : undefined}
    >
      {!email ? (
        <p className="text-sm text-cream/50">This customer has no email, so their orders cannot be matched.</p>
      ) : error ? (
        <ErrorNote message={error} />
      ) : !orders ? (
        <div className="space-y-2">{Array.from({ length: 3 }, (_, i) => <Skeleton key={i} className="h-10" />)}</div>
      ) : orders.length === 0 ? (
        <p className="text-sm text-cream/50">No orders yet.</p>
      ) : (
        <ul className="divide-y divide-orange/10">
          {orders.map((order) => (
            <li key={order.id}>
              <Link href={`/admin/orders/${order.id}`} className="flex flex-wrap items-center gap-x-4 gap-y-1 py-3 hover:bg-white/[0.03]">
                <span className="font-mono text-xs text-orange-light">{order.orderNumber}</span>
                <OrderStatusBadge status={order.status} />
                <span className="text-xs text-cream/50">{CHANNEL_LABEL[order.channel]}</span>
                <span className="ml-auto text-xs text-cream/50">{formatDateTime(order.createdAt)}</span>
                <span className="w-24 text-right font-bold tabular-nums">{money(order.grandTotal)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Panel>
  );
}

export default function CustomerDetail({ id }: { id: number }) {
  const router = useRouter();
  const [customer, setCustomer] = useState<CustomerResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setCustomer(await adminGetCustomer(id));
      setError(null);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "Could not load this customer.");
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  async function save(body: CustomerRequest) {
    setCustomer(await adminUpdateCustomer(id, body));
  }

  async function remove() {
    await adminDeleteCustomer(id);
    router.replace("/admin/customers");
  }

  const back = (
    <Link href="/admin/customers" className="inline-flex items-center gap-2 text-sm font-bold text-orange hover:text-orange-light">
      <ArrowLeft className="h-4 w-4" /> Customers
    </Link>
  );

  if (!customer) {
    return (
      <div className="mx-auto max-w-5xl space-y-4">
        {back}
        {error ? <ErrorNote message={error} /> : <><Skeleton className="h-20" /><Skeleton className="h-64" /></>}
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      {back}

      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="industrial-title text-3xl sm:text-4xl">{customer.name}</h1>
          <p className="mt-1 text-sm text-cream/50">Customer #{customer.id} · since {formatDateTime(customer.createdAt)}</p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => void load()} disabled={loading} aria-label="Reload" className={outlineButton}>
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          </button>
          <button type="button" onClick={() => setEditing(true)} className={`flex items-center gap-2 ${primaryButton}`}>
            <Pencil className="h-4 w-4" /> EDIT
          </button>
          <button type="button" onClick={() => setDeleting(true)} className={`flex items-center gap-2 ${dangerButton}`}>
            <Trash2 className="h-4 w-4" /> DELETE
          </button>
        </div>
      </div>

      {error && <ErrorNote message={error} />}

      <Panel title="CONTACT">
        <Facts
          rows={[
            ["Email", customer.email],
            ["Phone", customer.phone],
            ["Address", customer.address && <span className="whitespace-pre-line">{customer.address}</span>],
          ]}
        />
      </Panel>

      <CustomerOrders email={customer.email} />

      {editing && <CustomerForm customer={customer} onSave={save} onClose={() => setEditing(false)} />}
      {deleting && (
        <ConfirmDialog
          title="DELETE CUSTOMER"
          body={
            <>
              <p>{customer.name} will be removed for good.</p>
              <p className="mt-2 text-cream/60">
                A customer with orders, bookings, warranty claims, support tickets or chats cannot be deleted; the
                system refuses and says why.
              </p>
            </>
          }
          confirmLabel="DELETE"
          danger
          onConfirm={remove}
          onClose={() => setDeleting(false)}
        />
      )}
    </div>
  );
}
