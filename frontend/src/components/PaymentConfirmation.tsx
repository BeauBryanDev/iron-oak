"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";

import OrderStatusView from "./OrderStatusView";
import { getOrderByNumber } from "@/lib/api/orders";
import type { OrderStatus } from "@/lib/api/types";

/** Stripe's webhook, not this page, confirms the order; we only wait for it and show the result. */
const POLL_MS = 3000;
const MAX_ATTEMPTS = 30; // about 90 seconds

const PAID: ReadonlySet<OrderStatus> = new Set(["CONFIRMED", "IN_PROGRESS", "COMPLETED"]);
const DEAD: ReadonlySet<OrderStatus> = new Set(["EXPIRED", "CANCELLED"]);

type Phase = "confirming" | "paid" | "failed" | "inactive" | "slow";

function phaseOf(status: OrderStatus): Phase | null {
  if (PAID.has(status)) return "paid";
  if (status === "PAYMENT_FAILED") return "failed";
  if (DEAD.has(status)) return "inactive";
  return null; // PENDING_PAYMENT / DRAFT: still waiting for the webhook
}

export default function PaymentConfirmation({ orderNumber }: { orderNumber: string }) {
  const [phase, setPhase] = useState<Phase>("confirming");
  // changing the run key restarts the polling ("Check again")
  const [run, setRun] = useState(0);

  const restart = useCallback(() => {
    setPhase("confirming");
    setRun((value) => value + 1);
  }, []);

  useEffect(() => {
    let cancelled = false;
    let timer: number | undefined;
    let attempts = 0;

    async function poll() {
      attempts += 1;
      try {
        const order = await getOrderByNumber(orderNumber);
        const settled = phaseOf(order.status);
        if (settled) {
          if (!cancelled) setPhase(settled);
          return;
        }
      } catch {
        // a failed poll is not a failed payment: keep trying until the attempts run out
      }
      if (cancelled) return;
      if (attempts >= MAX_ATTEMPTS) {
        setPhase("slow");
        return;
      }
      timer = window.setTimeout(poll, POLL_MS);
    }

    void poll();
    return () => {
      cancelled = true;
      if (timer !== undefined) window.clearTimeout(timer);
    };
  }, [orderNumber, run]);

  return (
    <div className="space-y-8">
      <div className="border border-orange/40 bg-[#17110a] p-6" role="status" aria-live="polite">
        {phase === "confirming" && (
          <>
            <p className="font-black tracking-wide text-orange">CONFIRMING YOUR PAYMENT…</p>
            <p className="mt-2 text-sm text-cream/70">
              Your payment went through on Stripe. We are waiting for their confirmation, which usually takes a few
              seconds. Please keep this page open.
            </p>
          </>
        )}

        {phase === "paid" && (
          <>
            <p className="font-black tracking-wide text-orange">PAYMENT RECEIVED — THANK YOU</p>
            <p className="mt-2 text-sm text-cream/70">
              Your order {orderNumber} is confirmed and we are getting it ready. Keep the order number to follow its
              progress.
            </p>
          </>
        )}

        {phase === "failed" && (
          <>
            <p className="font-black tracking-wide text-red-300">THE PAYMENT DID NOT GO THROUGH</p>
            <p className="mt-2 text-sm text-cream/70">
              Nothing was charged for order {orderNumber}. Your items are still held for a while, so you can try again
              from the order page below.
            </p>
          </>
        )}

        {phase === "inactive" && (
          <>
            <p className="font-black tracking-wide text-red-300">THIS ORDER IS NO LONGER ACTIVE</p>
            <p className="mt-2 text-sm text-cream/70">
              Order {orderNumber} expired or was cancelled. If you were charged, the payment is refunded automatically
              and shows up on your statement in a few days.
            </p>
          </>
        )}

        {phase === "slow" && (
          <>
            <p className="font-black tracking-wide text-orange">STILL CONFIRMING</p>
            <p className="mt-2 text-sm text-cream/70">
              Stripe has not confirmed order {orderNumber} yet. That is not a failure: you can safely leave this page
              and check the order status later using the order number.
            </p>
            <button
              type="button"
              onClick={restart}
              className="mt-4 border border-orange px-4 py-2 text-xs font-black text-orange hover:bg-orange hover:text-black"
            >
              CHECK AGAIN
            </button>
          </>
        )}
      </div>

      {phase !== "confirming" && <OrderStatusView orderNumber={orderNumber} />}

      <Link href="/catalog" className="inline-block text-sm font-black text-orange hover:text-orange-light">
        ← CONTINUE SHOPPING
      </Link>
    </div>
  );
}
