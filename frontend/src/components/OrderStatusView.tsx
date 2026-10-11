"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";

import { ApiError } from "@/lib/api/http";
import { createCheckoutSession, getOrderByNumber } from "@/lib/api/orders";
import type { PublicOrderResponse } from "@/lib/api/types";
import { money } from "@/lib/format";
import { ORDER_STATUS_HELP, ORDER_STATUS_LABEL } from "@/lib/orderStatus";

function formatDate(iso: string): string {
  return new Date(iso).toLocaleString();
}

/** Public order page: anyone with the order number sees the order (no personal data) and can pay it. */
export default function OrderStatusView({ orderNumber, paymentUnavailable = false }: { orderNumber: string; paymentUnavailable?: boolean }) {
  const [order, setOrder] = useState<PublicOrderResponse | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const [paying, setPaying] = useState(false);
  const [payError, setPayError] = useState<string | null>(null);

  const load = useCallback(
    async (signal?: AbortSignal) => {
      setLoading(true);
      setError(null);
      try {
        setOrder(await getOrderByNumber(orderNumber, signal));
      } catch (cause) {
        if (signal?.aborted) return;
        setOrder(null);
        setError(cause instanceof ApiError ? cause : new ApiError(0, "Could not load this order."));
      } finally {
        if (!signal?.aborted) setLoading(false);
      }
    },
    [orderNumber],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  async function pay() {
    setPaying(true);
    setPayError(null);
    try {
      const session = await createCheckoutSession(orderNumber);
      window.location.assign(session.checkoutUrl);
    } catch (cause) {
      setPayError(cause instanceof ApiError ? cause.message : "Could not open the payment page.");
      setPaying(false);
    }
  }

  if (loading && !order) {
    return <p className="text-lg text-cream/70">Loading your order…</p>;
  }

  if (error) {
    return (
      <div className="border border-orange/40 bg-[#17110a] p-6">
        <p className="font-black tracking-wide text-orange">
          {error.status === 404 ? "ORDER NOT FOUND" : "COULD NOT LOAD THIS ORDER"}
        </p>
        <p className="mt-2 text-sm text-cream/70">
          {error.status === 404 ? "Check the order number and try again." : error.message}
        </p>
        <Link href="/orders" className="mt-4 inline-block text-sm font-black text-orange hover:text-orange-light">
          ← TRACK ANOTHER ORDER
        </Link>
      </div>
    );
  }

  if (!order) return null;

  const awaitingQuote = order.shippingStatus === "ON_REQUEST" && order.status === "PENDING_PAYMENT";

  return (
    <div className="grid gap-8 lg:grid-cols-[1fr_340px]">

      <div className="border border-orange/30 bg-[#17110a]">
        <div className="border-b border-orange/20 p-6">
          <p className="text-xs font-bold tracking-wide text-cream/50">ORDER</p>
          <p className="industrial-title text-3xl">{order.orderNumber}</p>
          <p className="mt-1 text-xs text-cream/50">Placed {formatDate(order.createdAt)}</p>
        </div>

        <ul>
          {order.items.map((line, index) => (
            <li key={`${line.itemCode}-${index}`} className="flex justify-between gap-4 border-b border-orange/15 p-4 last:border-b-0">
              <div>
                <p className="font-bold">{line.name}</p>
                <p className="text-xs text-cream/50">
                  {line.itemCode} · {line.quantity} × {money(line.unitPrice)}
                </p>
              </div>
              <p className="font-black">{money(line.subtotal)}</p>
            </li>
          ))}
        </ul>
      </div>

      <aside className="h-fit space-y-4 border border-orange/30 bg-[#17110a] p-6">
        <div>
          <p className="text-xs font-bold tracking-wide text-cream/50">STATUS</p>
          <p className="text-xl font-black text-orange">{ORDER_STATUS_LABEL[order.status]}</p>
          <p className="mt-1 text-sm text-cream/70">{ORDER_STATUS_HELP[order.status]}</p>
        </div>

        <dl className="space-y-2 border-t border-orange/20 pt-4 text-sm">
          <div className="flex justify-between">
            <dt className="text-cream/70">Items</dt>
            <dd className="font-black">{money(order.subtotal)}</dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-cream/70">Shipping</dt>
            <dd className="font-black">
              {awaitingQuote ? <span className="text-orange">awaiting quote</span> : money(order.shippingCost)}
            </dd>
          </div>
          <div className="flex justify-between">
            <dt className="text-cream/70">Taxes</dt>
            <dd className="font-black">{money(order.taxes)}</dd>
          </div>
          <div className="flex justify-between border-t border-orange/20 pt-3 text-base">
            <dt className="font-bold">Total</dt>
            <dd className="text-xl font-black text-orange">{money(order.grandTotal)}</dd>
          </div>
        </dl>

        {awaitingQuote && (
          <p className="text-sm text-orange">
            Our team is preparing the freight quote for this order. Your items are held, and the Pay button appears here
            once the price is set.
          </p>
        )}

        {order.status === "PENDING_PAYMENT" && order.reservationExpiresAt && (
          <p className="text-xs text-cream/50">Items held until {formatDate(order.reservationExpiresAt)}.</p>
        )}

        {paymentUnavailable && order.payable && (
          <p className="text-sm text-orange">We could not open the payment page just now. Please try again.</p>
        )}
        {payError && <p className="text-sm text-red-400">{payError}</p>}

        {order.payable && (
          <button
            type="button"
            onClick={pay}
            disabled={paying}
            className="w-full bg-orange px-6 py-4 text-sm font-black text-black hover:bg-orange-light disabled:cursor-wait disabled:opacity-60"
          >
            {paying ? "OPENING PAYMENT…" : "PAY NOW"}
          </button>
        )}

        <button
          type="button"
          onClick={() => void load()}
          className="w-full text-center text-xs font-bold text-cream/50 hover:text-orange"
        >
          REFRESH STATUS
        </button>
      </aside>

    </div>
  );
}
