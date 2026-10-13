"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { ArrowLeft, Bot, Clock, Copy, RefreshCw, Truck } from "lucide-react";

import OrderStatusBadge from "@/components/admin/OrderStatusBadge";
import OrderPayments from "@/components/admin/orders/OrderPayments";
import OrderShipment from "@/components/admin/orders/OrderShipment";
import { ConfirmDialog, ErrorNote, Facts, Panel, Skeleton, dangerButton, inputClass, outlineButton, primaryButton } from "@/components/admin/ui";
import {
  CHANNEL_LABEL,
  ITEM_TYPE_LABEL,
  ORDER_TRANSITIONS,
  SHIPPING_MODE_LABEL,
  SHIPPING_SOURCE_LABEL,
  formatDateTime,
} from "@/lib/admin/labels";
import { adminGetOrder, adminOrderPayments, adminSetShippingCost, adminUpdateOrderStatus } from "@/lib/api/adminOrders";
import { ApiError } from "@/lib/api/http";
import type { OrderResponse, OrderStatus, PaymentResponse } from "@/lib/api/types";
import { money } from "@/lib/format";
import { ORDER_STATUS_LABEL } from "@/lib/orderStatus";

const ACTION: Partial<Record<OrderStatus, { label: string; danger?: boolean }>> = {
  CONFIRMED: { label: "CONFIRM ORDER" },
  IN_PROGRESS: { label: "START PREPARING" },
  COMPLETED: { label: "MARK COMPLETED" },
  CANCELLED: { label: "CANCEL ORDER", danger: true },
};

function statusBody(order: OrderResponse, target: OrderStatus, paid: number): React.ReactNode {
  const parcelOpen = order.shippingMode !== "NONE" && order.shipment?.status !== "DELIVERED";
  switch (target) {
    case "CANCELLED":
      return (
        <>
          <p>Order {order.orderNumber} will be cancelled and any stock it holds goes back on sale.</p>
          {paid > 0 && (
            <p className="mt-2 font-bold text-amber-300">
              {money(paid)} has been paid. Cancelling does not return money: refund the payments too.
            </p>
          )}
          <p className="mt-2">This cannot be undone.</p>
        </>
      );
    case "COMPLETED":
      return parcelOpen ? (
        <>
          <p className="font-bold text-amber-300">This order has a parcel that is not marked delivered.</p>
          <p className="mt-2">
            Use the shipment’s <b>Delivered</b> step instead: it completes the order and starts the warranty. An order
            completed here has no delivery date, so the customer cannot file warranty claims on it.
          </p>
        </>
      ) : (
        <p>Order {order.orderNumber} will be marked completed. This cannot be undone.</p>
      );
    case "IN_PROGRESS":
      return <p>Marks the order as being prepared. Shipping it later does this automatically too.</p>;
    default:
      return <p>Change the order to “{ORDER_STATUS_LABEL[target]}”.</p>;
  }
}

function ShippingQuote({ order, paid, onChanged }: { order: OrderResponse; paid: number; onChanged: (o: OrderResponse) => void }) {
  const onRequest = order.shippingStatus === "ON_REQUEST";
  const editable = ["PENDING_PAYMENT", "PAYMENT_FAILED", "CONFIRMED", "DRAFT"].includes(order.status) && paid === 0;
  const [open, setOpen] = useState(onRequest);
  const [amount, setAmount] = useState(onRequest ? "" : order.shippingCost.toFixed(2));
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!editable || (!onRequest && order.shippingMode === "NONE")) return null;

  async function submit(event: FormEvent) {
    event.preventDefault();
    const value = Number(amount);
    if (!Number.isFinite(value) || value < 0) {
      setError("Enter a shipping cost of 0 or more.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onChanged(await adminSetShippingCost(order.id, Math.round(value * 100) / 100));
      setOpen(false);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "Could not save the shipping cost.");
    } finally {
      setSaving(false);
    }
  }

  if (!open) {
    return (
      <button type="button" onClick={() => setOpen(true)} className="mt-3 text-xs font-bold text-orange hover:text-orange-light">
        Change shipping cost
      </button>
    );
  }

  return (
    <form onSubmit={submit} className={`mt-4 space-y-3 border p-4 ${onRequest ? "border-amber-400/60 bg-amber-400/10" : "border-orange/30"}`}>
      {onRequest && (
        <p className="text-sm text-amber-200">
          <b>Shipping needs a quote.</b> This order has a milling machine or an air route without a set price, so the
          customer cannot pay until you enter the freight cost.
        </p>
      )}
      <label className="block">
        <span className="text-xs font-bold text-cream/60">SHIPPING COST (USD)</span>
        <input
          inputMode="decimal"
          value={amount}
          onChange={(e) => setAmount(e.target.value.replace(",", "."))}
          placeholder="0.00"
          className={`${inputClass} mt-1 max-w-[12rem] tabular-nums`}
        />
      </label>
      {error && <ErrorNote message={error} />}
      <div className="flex gap-3">
        <button type="submit" disabled={saving || amount === ""} className={primaryButton}>{saving ? "SAVING…" : "SAVE SHIPPING"}</button>
        {!onRequest && <button type="button" onClick={() => setOpen(false)} className="px-3 text-xs font-black text-cream/60">CANCEL</button>}
      </div>
    </form>
  );
}

export default function OrderDetail({ id }: { id: number }) {
  const [order, setOrder] = useState<OrderResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [payments, setPayments] = useState<PaymentResponse[] | null>(null);
  const [paymentsError, setPaymentsError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [target, setTarget] = useState<OrderStatus | null>(null);
  const [copied, setCopied] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    const [o, p] = await Promise.allSettled([adminGetOrder(id), adminOrderPayments(id)]);
    if (o.status === "fulfilled") {
      setOrder(o.value);
      setError(null);
    } else {
      setError(o.reason instanceof ApiError ? o.reason.message : "Could not load this order.");
    }
    if (p.status === "fulfilled") {
      setPayments(p.value);
      setPaymentsError(null);
    } else {
      setPaymentsError(p.reason instanceof ApiError ? p.reason.message : "Could not load payments.");
    }
    setLoading(false);
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);

  if (!order) {
    return (
      <div className="mx-auto max-w-7xl space-y-4">
        <Link href="/admin/orders" className="inline-flex items-center gap-2 text-sm font-bold text-orange"><ArrowLeft className="h-4 w-4" /> Orders</Link>
        {error ? <ErrorNote message={error} /> : <><Skeleton className="h-20" /><Skeleton className="h-96" /></>}
      </div>
    );
  }

  const paid = (payments ?? []).filter((p) => p.status === "PAID").reduce((sum, p) => sum + p.amount, 0);
  const transitions = ORDER_TRANSITIONS[order.status];
  const holding = (order.status === "PENDING_PAYMENT" || order.status === "PAYMENT_FAILED") && order.reservationExpiresAt;

  return (
    <div className="mx-auto max-w-7xl space-y-5">
      <Link href="/admin/orders" className="inline-flex items-center gap-2 text-sm font-bold text-orange hover:text-orange-light">
        <ArrowLeft className="h-4 w-4" /> Orders
      </Link>

      {/* header */}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex flex-wrap items-center gap-3">
            <h1 className="industrial-title text-3xl sm:text-4xl">{order.orderNumber}</h1>
            <button
              type="button"
              aria-label="Copy order number"
              onClick={() => {
                void navigator.clipboard?.writeText(order.orderNumber).then(() => {
                  setCopied(true);
                  window.setTimeout(() => setCopied(false), 1500);
                });
              }}
              className="text-cream/40 hover:text-orange"
            >
              <Copy className="h-4 w-4" />
            </button>
            {copied && <span className="text-xs text-emerald-300">Copied</span>}
            <OrderStatusBadge status={order.status} />
          </div>
          <p className="mt-1 flex flex-wrap items-center gap-x-2 text-sm text-cream/50">
            {order.channel === "PIPER" && <Bot className="h-4 w-4" />}
            {CHANNEL_LABEL[order.channel]} order · #{order.id} · placed {formatDateTime(order.createdAt)}
          </p>
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" onClick={() => void load()} disabled={loading} aria-label="Reload" className={outlineButton}>
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          </button>
          {transitions.map((status) => (
            <button
              key={status}
              type="button"
              onClick={() => setTarget(status)}
              className={ACTION[status]?.danger ? dangerButton : status === "COMPLETED" ? outlineButton : primaryButton}
            >
              {ACTION[status]?.label ?? status}
            </button>
          ))}
        </div>
      </div>

      {error && <ErrorNote message={error} />}

      {holding && (
        <p className="flex items-center gap-2 border border-amber-400/40 bg-amber-400/5 p-3 text-sm text-amber-200">
          <Clock className="h-4 w-4 shrink-0" />
          Stock is held for this order until {formatDateTime(order.reservationExpiresAt)}; unpaid by then, it expires and the items go back on sale.
        </p>
      )}

      <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
        <div className="space-y-5 lg:col-span-2">
          <Panel title="ITEMS">
            <div className="-mx-5 overflow-x-auto">
              <table className="w-full min-w-[560px] text-sm">
                <thead>
                  <tr className="border-b border-orange/20 text-left text-xs tracking-wider text-cream/50">
                    <th className="px-5 py-2 font-bold">ITEM</th>
                    <th className="px-3 py-2 font-bold">TYPE</th>
                    <th className="px-3 py-2 text-right font-bold">QTY</th>
                    <th className="px-3 py-2 text-right font-bold">UNIT</th>
                    <th className="px-5 py-2 text-right font-bold">SUBTOTAL</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-orange/10">
                  {order.items.map((item) => (
                    <tr key={item.id}>
                      <td className="px-5 py-2.5">
                        <p>{item.name}</p>
                        <p className="font-mono text-xs text-cream/40">{item.itemCode}</p>
                      </td>
                      <td className="px-3 py-2.5 text-cream/70">{ITEM_TYPE_LABEL[item.itemType]}</td>
                      <td className="px-3 py-2.5 text-right tabular-nums">
                        {item.quantity}
                        {item.estimatedHours !== null && <span className="block text-xs text-cream/40">{item.estimatedHours} h</span>}
                      </td>
                      <td className="px-3 py-2.5 text-right tabular-nums">{money(item.unitPrice)}</td>
                      <td className="px-5 py-2.5 text-right font-bold tabular-nums">{money(item.subtotal)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <dl className="ml-auto mt-4 max-w-xs space-y-1 text-sm">
              <div className="flex justify-between"><dt className="text-cream/60">Items</dt><dd className="tabular-nums">{money(order.subtotal)}</dd></div>
              <div className="flex justify-between">
                <dt className="text-cream/60">Shipping</dt>
                <dd className="tabular-nums">{order.shippingStatus === "ON_REQUEST" ? <span className="text-amber-300">to quote</span> : money(order.shippingCost)}</dd>
              </div>
              <div className="flex justify-between"><dt className="text-cream/60">Taxes</dt><dd className="tabular-nums">{money(order.taxes)}</dd></div>
              <div className="flex justify-between border-t border-orange/20 pt-2 text-base font-black">
                <dt>Total</dt><dd className="tabular-nums text-orange-light">{money(order.grandTotal)} <span className="text-xs text-cream/40">{order.currency}</span></dd>
              </div>
            </dl>
          </Panel>

          <OrderPayments order={order} payments={payments} error={paymentsError} onChanged={load} />
        </div>

        <div className="space-y-5">
          <Panel title="CUSTOMER">
            <Facts
              rows={[
                ["Name", order.customerName],
                ["Email", order.customerEmail ? <a href={`mailto:${order.customerEmail}`} className="text-orange hover:underline">{order.customerEmail}</a> : null],
                ["Phone", order.phoneNumber],
              ]}
            />
          </Panel>

          <Panel title="DELIVERY">
            <Facts
              rows={[
                ["Address", order.shippingAddress],
                ["City", [order.city, order.province].filter(Boolean).join(", ") || null],
                ["Country", order.country],
                ["Method", <span key="m" className="inline-flex items-center gap-1"><Truck className="h-3.5 w-3.5 text-orange" />{SHIPPING_MODE_LABEL[order.shippingMode]}</span>],
                ["Distance", order.shippingDistanceKm !== null ? `${order.shippingDistanceKm.toLocaleString("en-US")} km` : null],
                ["Priced by", order.shippingSource ? SHIPPING_SOURCE_LABEL[order.shippingSource] : null],
                ["Shipping cost", order.shippingStatus === "ON_REQUEST" ? "To quote" : money(order.shippingCost)],
              ]}
            />
            <ShippingQuote key={`${order.id}-${order.updatedAt}`} order={order} paid={paid} onChanged={(o) => { setOrder(o); void load(); }} />
          </Panel>

          <OrderShipment key={`${order.id}-${order.updatedAt}`} order={order} onChanged={setOrder} />
        </div>
      </div>

      {target && (
        <ConfirmDialog
          title={`${ACTION[target]?.label ?? target}?`}
          confirmLabel={ACTION[target]?.label ?? "CONFIRM"}
          danger={ACTION[target]?.danger}
          body={statusBody(order, target, paid)}
          onConfirm={async () => {
            setOrder(await adminUpdateOrderStatus(order.id, target));
          }}
          onClose={() => setTarget(null)}
        />
      )}
    </div>
  );
}
