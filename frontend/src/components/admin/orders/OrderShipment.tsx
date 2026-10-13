"use client";

import { useState, type FormEvent } from "react";
import { Check, Truck } from "lucide-react";

import { ConfirmDialog, ErrorNote, Facts, Panel, inputClass, primaryButton } from "@/components/admin/ui";
import { SHIPMENT_LABEL, SHIPMENT_STEPS, formatDate, formatDateTime } from "@/lib/admin/labels";
import { adminUpdateShipment } from "@/lib/api/adminOrders";
import { ApiError } from "@/lib/api/http";
import type { OrderResponse, ShipmentStatus } from "@/lib/api/types";

/** The steps staff may pick next; the backend only moves forward. */
function nextSteps(current: ShipmentStatus | null): ShipmentStatus[] {
  if (current === null) return ["SHIPPED"];
  if (current === "SHIPPED") return ["IN_TRANSIT", "DELIVERED"];
  if (current === "IN_TRANSIT") return ["DELIVERED"];
  return [];
}

function Stepper({ order }: { order: OrderResponse }) {
  const current = order.shipment?.status ?? null;
  const reached = current ? SHIPMENT_STEPS.indexOf(current) : -1;
  const dates: Record<ShipmentStatus, string | null> = {
    SHIPPED: formatDateTime(order.shipment?.shippedAt),
    IN_TRANSIT: null,
    DELIVERED: formatDateTime(order.shipment?.deliveredAt),
  };
  return (
    <ol className="flex items-start">
      {SHIPMENT_STEPS.map((step, index) => {
        const done = index <= reached;
        return (
          <li key={step} className="flex flex-1 flex-col items-center text-center">
            <div className="flex w-full items-center">
              <span className={`h-0.5 flex-1 ${index === 0 ? "invisible" : done ? "bg-orange" : "bg-white/10"}`} />
              <span className={`flex h-7 w-7 shrink-0 items-center justify-center border ${done ? "border-orange bg-orange text-black" : "border-cream/20 text-cream/30"}`}>
                {done ? <Check className="h-4 w-4" /> : <span className="text-xs font-bold">{index + 1}</span>}
              </span>
              <span className={`h-0.5 flex-1 ${index === SHIPMENT_STEPS.length - 1 ? "invisible" : index < reached ? "bg-orange" : "bg-white/10"}`} />
            </div>
            <p className={`mt-2 text-xs font-bold ${done ? "text-cream" : "text-cream/40"}`}>{SHIPMENT_LABEL[step]}</p>
            {dates[step] && <p className="text-[11px] text-cream/40">{dates[step]}</p>}
          </li>
        );
      })}
    </ol>
  );
}

export default function OrderShipment({ order, onChanged }: { order: OrderResponse; onChanged: (order: OrderResponse) => void }) {
  const current = order.shipment?.status ?? null;
  const options = nextSteps(current);
  const paid = order.status === "CONFIRMED" || order.status === "IN_PROGRESS";

  const [status, setStatus] = useState<ShipmentStatus | "">(options[0] ?? "");
  const [carrier, setCarrier] = useState(order.shipment?.carrier ?? "");
  const [tracking, setTracking] = useState(order.shipment?.trackingNumber ?? "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelivered, setConfirmDelivered] = useState(false);

  if (order.shippingMode === "NONE" || order.shipment === null) {
    return (
      <Panel title="SHIPMENT">
        <p className="text-sm text-cream/50">Nothing to ship on this order.</p>
      </Panel>
    );
  }

  /** status "" = keep the current step and only correct carrier / tracking. */
  async function send(target: ShipmentStatus) {
    const updated = await adminUpdateShipment(order.id, {
      status: target,
      carrier: carrier.trim() || undefined,
      trackingNumber: tracking.trim() || undefined,
    });
    onChanged(updated);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    const target = status || current;
    if (!target) return;
    if (target === "DELIVERED" && current !== "DELIVERED") {
      setConfirmDelivered(true);
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await send(target);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "Could not update the shipment.");
    } finally {
      setSaving(false);
    }
  }

  const needsCarrier = current === null;
  const canEdit = paid && current !== "DELIVERED";
  const hasServices = order.items.some((item) => item.itemType === "SERVICE");

  return (
    <Panel title="SHIPMENT" subtitle={current ? undefined : "Not shipped yet"}>
      <Stepper order={order} />

      <div className="mt-5">
        <Facts
          rows={[
            ["Carrier", order.shipment.carrier],
            ["Tracking", order.shipment.trackingNumber ? <span className="font-mono text-xs">{order.shipment.trackingNumber}</span> : null],
            ["Delivery time", order.shipment.estimatedDeliveryDays !== null ? `${order.shipment.estimatedDeliveryDays} days` : null],
            ["Expected by", formatDate(order.shipment.expectedDeliveryDate)],
          ]}
        />
      </div>

      {!paid && current !== "DELIVERED" && (
        <p className="mt-4 text-xs text-cream/50">The order can be shipped once it is paid.</p>
      )}

      {canEdit && (
        <form onSubmit={submit} className="mt-5 space-y-3 border-t border-orange/20 pt-4">
          <label className="block">
            <span className="text-xs font-bold text-cream/60">NEXT STEP</span>
            <select value={status} onChange={(e) => setStatus(e.target.value as ShipmentStatus | "")} className={`${inputClass} mt-1`}>
              {options.map((step) => <option key={step} value={step}>{SHIPMENT_LABEL[step]}</option>)}
              {current && <option value="">Keep “{SHIPMENT_LABEL[current]}”, only fix carrier / tracking</option>}
            </select>
          </label>
          <label className="block">
            <span className="text-xs font-bold text-cream/60">CARRIER {needsCarrier && <span className="text-orange">*</span>}</span>
            <input value={carrier} maxLength={100} onChange={(e) => setCarrier(e.target.value)} placeholder="e.g. Servientrega, DHL" className={`${inputClass} mt-1`} />
          </label>
          <label className="block">
            <span className="text-xs font-bold text-cream/60">TRACKING NUMBER</span>
            <input value={tracking} maxLength={100} onChange={(e) => setTracking(e.target.value)} placeholder="Optional" className={`${inputClass} mt-1 font-mono`} />
          </label>
          {status === "SHIPPED" && order.status === "CONFIRMED" && (
            <p className="text-xs text-cream/50">Shipping moves the order to “In progress”.</p>
          )}
          {error && <ErrorNote message={error} />}
          <button type="submit" disabled={saving || (needsCarrier && !carrier.trim())} className={`flex items-center gap-2 ${primaryButton}`}>
            <Truck className="h-4 w-4" /> {saving ? "SAVING…" : status ? `MARK ${SHIPMENT_LABEL[status].toUpperCase()}` : "SAVE DETAILS"}
          </button>
        </form>
      )}

      {confirmDelivered && (
        <ConfirmDialog
          title="MARK AS DELIVERED?"
          confirmLabel="MARK DELIVERED"
          body={
            <>
              <p>This records today as the delivery date, which starts the warranty on its products and machines.</p>
              <p className="mt-2">
                {hasServices
                  ? "The order also has a service line, so it stays in progress until you complete it."
                  : "The order will be marked completed."}{" "}
                This cannot be undone.
              </p>
            </>
          }
          onConfirm={() => send("DELIVERED")}
          onClose={() => setConfirmDelivered(false)}
        />
      )}
    </Panel>
  );
}
