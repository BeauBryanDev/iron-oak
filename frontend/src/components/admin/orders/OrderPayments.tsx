"use client";

import { useState, type FormEvent } from "react";
import { CreditCard, Plus } from "lucide-react";

import { ConfirmDialog, ErrorNote, Panel, dangerButton, inputClass, outlineButton, primaryButton } from "@/components/admin/ui";
import { PAYMENT_TONE, formatDateTime } from "@/lib/admin/labels";
import { adminCreatePayment, adminRefundPayment, adminUpdatePaymentStatus } from "@/lib/api/adminOrders";
import { ApiError } from "@/lib/api/http";
import type { OrderResponse, PaymentResponse, PaymentStatus } from "@/lib/api/types";
import { money } from "@/lib/format";

const MANUAL_METHODS: { value: string; label: string }[] = [
  { value: "cash", label: "Cash" },
  { value: "bank_transfer", label: "Bank transfer" },
  { value: "card_terminal", label: "Card terminal (in store)" },
  { value: "other", label: "Other" },
];

const PROVIDER_LABEL: Record<string, string> = {
  stripe: "Stripe",
  ...Object.fromEntries(MANUAL_METHODS.map((m) => [m.value, m.label])),
};

type Pending =
  | { kind: "status"; payment: PaymentResponse; status: PaymentStatus }
  | { kind: "refund"; payment: PaymentResponse };

const STATUS_ACTION: Partial<Record<PaymentStatus, { label: string; danger: boolean; body: string }>> = {
  PAID: { label: "MARK PAID", danger: false, body: "Record that this money was received. When it covers the order total, the order becomes paid." },
  FAILED: { label: "MARK FAILED", danger: true, body: "This attempt did not go through. The order stays held for another payment while the hold lasts." },
  CANCELLED: { label: "CANCEL PAYMENT", danger: true, body: "This payment attempt will be closed." },
  REFUNDED: { label: "MARK REFUNDED", danger: true, body: "Records that you returned this money yourself (cash, transfer). No money is moved by the system, and the order is not cancelled." },
};

function reference(payment: PaymentResponse): string | null {
  return payment.providerReference ?? payment.paymentIntentId ?? payment.checkoutSessionId;
}

export default function OrderPayments({ order, payments, error, onChanged }: {
  order: OrderResponse;
  payments: PaymentResponse[] | null;
  error: string | null;
  onChanged: () => Promise<void>;
}) {
  const [pending, setPending] = useState<Pending | null>(null);
  const [reason, setReason] = useState("");
  const [showForm, setShowForm] = useState(false);
  const [method, setMethod] = useState(MANUAL_METHODS[0].value);
  const [ref, setRef] = useState("");
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  const paidTotal = (payments ?? []).filter((p) => p.status === "PAID").reduce((sum, p) => sum + p.amount, 0);
  const balance = Math.max(0, Math.round((order.grandTotal - paidTotal) * 100) / 100);
  const hasOpen = (payments ?? []).some((p) => p.status === "PENDING" || p.status === "PROCESSING");
  const canRecord =
    (order.status === "PENDING_PAYMENT" || order.status === "PAYMENT_FAILED") &&
    order.shippingStatus === "QUOTED" &&
    balance > 0 &&
    !hasOpen;

  async function record(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setFormError(null);
    try {
      await adminCreatePayment({ orderId: order.id, provider: method, providerReference: ref.trim() || undefined });
      setShowForm(false);
      setRef("");
      await onChanged();
    } catch (cause) {
      setFormError(cause instanceof ApiError ? cause.message : "Could not record the payment.");
    } finally {
      setSaving(false);
    }
  }

  function actionsFor(payment: PaymentResponse) {
    const stripe = payment.provider === "stripe";
    if (stripe) {
      return payment.status === "PAID"
        ? [<button key="refund" type="button" className={dangerButton} onClick={() => { setReason(""); setPending({ kind: "refund", payment }); }}>REFUND</button>]
        : [];
    }
    const next: PaymentStatus[] =
      payment.status === "PENDING" || payment.status === "PROCESSING" ? ["PAID", "FAILED", "CANCELLED"]
        : payment.status === "PAID" ? ["REFUNDED"]
          : [];
    return next.map((status) => (
      <button
        key={status}
        type="button"
        className={status === "PAID" ? primaryButton : STATUS_ACTION[status]?.danger ? dangerButton : outlineButton}
        onClick={() => setPending({ kind: "status", payment, status })}
      >
        {STATUS_ACTION[status]?.label}
      </button>
    ));
  }

  return (
    <Panel
      title="PAYMENTS"
      subtitle={`Paid ${money(paidTotal)} of ${money(order.grandTotal)}${balance > 0 ? ` · ${money(balance)} outstanding` : ""}`}
      actions={
        canRecord && !showForm ? (
          <button type="button" onClick={() => setShowForm(true)} className={`flex items-center gap-2 ${outlineButton}`}>
            <Plus className="h-4 w-4" /> RECORD PAYMENT
          </button>
        ) : undefined
      }
    >
      {error && <ErrorNote message={error} />}

      {showForm && (
        <form onSubmit={record} className="mb-5 space-y-3 border border-orange/30 bg-[#17110a] p-4">
          <p className="text-sm text-cream/80">
            Record a payment taken outside Stripe for the outstanding <b>{money(balance)}</b>. It starts as pending; mark it paid once the money is in.
          </p>
          <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
            <label className="block">
              <span className="text-xs font-bold text-cream/60">METHOD</span>
              <select value={method} onChange={(e) => setMethod(e.target.value)} className={`${inputClass} mt-1`}>
                {MANUAL_METHODS.map((m) => <option key={m.value} value={m.value}>{m.label}</option>)}
              </select>
            </label>
            <label className="block">
              <span className="text-xs font-bold text-cream/60">REFERENCE</span>
              <input value={ref} maxLength={100} onChange={(e) => setRef(e.target.value)} placeholder="Receipt or transfer number" className={`${inputClass} mt-1`} />
            </label>
          </div>
          {formError && <ErrorNote message={formError} />}
          <div className="flex gap-3">
            <button type="submit" disabled={saving} className={primaryButton}>{saving ? "SAVING…" : "RECORD"}</button>
            <button type="button" onClick={() => setShowForm(false)} className="px-3 text-xs font-black text-cream/60 hover:text-cream">CANCEL</button>
          </div>
        </form>
      )}

      {payments === null && !error ? (
        <p className="text-sm text-cream/50">Loading payments…</p>
      ) : payments && payments.length === 0 ? (
        <p className="text-sm text-cream/50">
          No payments yet.{order.shippingStatus === "ON_REQUEST" && " The customer can pay once shipping is quoted."}
        </p>
      ) : (
        <ul className="divide-y divide-orange/10">
          {(payments ?? []).map((payment) => (
            <li key={payment.id} className="flex flex-wrap items-center gap-x-4 gap-y-2 py-3">
              <CreditCard className="h-5 w-5 shrink-0 text-orange" />
              <div className="min-w-0 flex-1">
                <p className="flex flex-wrap items-center gap-2 text-sm">
                  <b className="tabular-nums">{money(payment.amount)}</b>
                  <span className="text-cream/60">{PROVIDER_LABEL[payment.provider] ?? payment.provider}</span>
                  <span className={`border px-2 py-0.5 text-[11px] font-bold ${PAYMENT_TONE[payment.status]}`}>{payment.status}</span>
                </p>
                <p className="mt-0.5 truncate text-xs text-cream/40">
                  #{payment.id} · {formatDateTime(payment.createdAt)}
                  {payment.paidAt && ` · paid ${formatDateTime(payment.paidAt)}`}
                  {reference(payment) && <> · <span className="font-mono">{reference(payment)}</span></>}
                </p>
                {payment.failureMessage && <p className="mt-1 text-xs text-red-300">{payment.failureMessage}</p>}
                {payment.provider === "stripe" && (payment.status === "PENDING" || payment.status === "PROCESSING") && (
                  <p className="mt-1 text-xs text-cream/40">Updated automatically by Stripe.</p>
                )}
              </div>
              <div className="flex flex-wrap gap-2">{actionsFor(payment)}</div>
            </li>
          ))}
        </ul>
      )}

      {pending?.kind === "status" && (
        <ConfirmDialog
          title={`${STATUS_ACTION[pending.status]?.label ?? pending.status}?`}
          confirmLabel={STATUS_ACTION[pending.status]?.label ?? "CONFIRM"}
          danger={STATUS_ACTION[pending.status]?.danger}
          body={<><p><b>{money(pending.payment.amount)}</b> · payment #{pending.payment.id}</p><p className="mt-2">{STATUS_ACTION[pending.status]?.body}</p></>}
          onConfirm={async () => {
            await adminUpdatePaymentStatus(pending.payment.id, pending.status);
            await onChanged();
          }}
          onClose={() => setPending(null)}
        />
      )}

      {pending?.kind === "refund" && (
        <ConfirmDialog
          title="REFUND THROUGH STRIPE?"
          confirmLabel={`REFUND ${money(pending.payment.amount)}`}
          danger
          body={
            <>
              <p>The full <b>{money(pending.payment.amount)}</b> goes back to the customer’s card. This cannot be undone.</p>
              <p className="mt-2">The order is not cancelled by a refund; cancel it separately if needed.</p>
            </>
          }
          onConfirm={async () => {
            await adminRefundPayment(pending.payment.id, reason.trim() || undefined);
            await onChanged();
          }}
          onClose={() => setPending(null)}
        >
          <label className="block">
            <span className="text-xs font-bold text-cream/60">REASON (kept in the audit log and on Stripe)</span>
            <input value={reason} maxLength={200} onChange={(e) => setReason(e.target.value)} className={`${inputClass} mt-1`} />
          </label>
        </ConfirmDialog>
      )}
    </Panel>
  );
}
