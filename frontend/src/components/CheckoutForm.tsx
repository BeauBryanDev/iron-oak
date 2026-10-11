"use client";

import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";

import { ApiError } from "@/lib/api/http";
import { createCheckoutSession, createOrder, quoteShipping } from "@/lib/api/orders";
import type { CreateOrderRequest, OrderLineRequest, ShippingQuoteResponse } from "@/lib/api/types";
import { useCart } from "@/lib/cart/CartContext";
import { money } from "@/lib/format";

/** Countries the shipping rules accept (backend ShippingCalculator). The backend still validates. */
const COUNTRIES = [
  { code: "CO", name: "Colombia" },
  { code: "US", name: "United States" },
  { code: "MX", name: "Mexico" },
  { code: "CR", name: "Costa Rica" },
  { code: "EC", name: "Ecuador" },
  { code: "PA", name: "Panama" },
  { code: "PE", name: "Peru" },
  { code: "BR", name: "Brazil" },
  { code: "AR", name: "Argentina" },
  { code: "BO", name: "Bolivia" },
];

const inputClass =
  "w-full border border-orange/40 bg-[#17110a] px-3 py-2 text-cream outline-none focus:border-orange";

function Field({ label, children, hint }: { label: string; children: ReactNode; hint?: string }) {
  return (
    <label className="block">
      <span className="text-xs font-bold tracking-wide text-cream/60">{label}</span>
      <div className="mt-1">{children}</div>
      {hint && <span className="mt-1 block text-xs text-cream/40">{hint}</span>}
    </label>
  );
}

function newIdempotencyKey(): string {
  if (typeof crypto !== "undefined" && typeof crypto.randomUUID === "function") {
    return crypto.randomUUID();
  }
  return `ck-${Date.now()}-${Math.random().toString(36).slice(2)}`;
}

export default function CheckoutForm() {
  const router = useRouter();
  const { items, hydrated, subtotal, clear } = useCart();

  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [country, setCountry] = useState("CO");
  const [province, setProvince] = useState("");
  const [city, setCity] = useState("");
  const [address, setAddress] = useState("");

  const [quote, setQuote] = useState<ShippingQuoteResponse | null>(null);
  const [quoting, setQuoting] = useState(false);
  const [quoteError, setQuoteError] = useState<string | null>(null);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // One key per distinct payload: an identical retry returns the original order instead of a second one.
  const attempt = useRef<{ signature: string; key: string } | null>(null);

  const orderItems: OrderLineRequest[] = useMemo(
    () => items.map((item) => ({ itemType: item.itemType, referenceId: item.referenceId, quantity: item.quantity })),
    [items],
  );
  const itemsKey = JSON.stringify(orderItems);

  // Live shipping quote once a destination is known.
  useEffect(() => {
    const trimmedCity = city.trim();
    if (!hydrated || orderItems.length === 0 || trimmedCity.length < 2) {
      setQuote(null);
      setQuoteError(null);
      setQuoting(false);
      return;
    }
    const controller = new AbortController();
    const timer = window.setTimeout(async () => {
      setQuoting(true);
      setQuoteError(null);
      try {
        const result = await quoteShipping(
          { country, city: trimmedCity, province: province.trim() || undefined, items: orderItems },
          controller.signal,
        );
        if (!controller.signal.aborted) setQuote(result);
      } catch (cause) {
        if (controller.signal.aborted) return;
        setQuote(null);
        setQuoteError(cause instanceof ApiError ? cause.message : "Could not calculate shipping.");
      } finally {
        if (!controller.signal.aborted) setQuoting(false);
      }
    }, 500);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
    // orderItems is covered by itemsKey
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hydrated, country, city, province, itemsKey]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting || items.length === 0) return;
    setSubmitting(true);
    setError(null);

    const payload: CreateOrderRequest = {
      customerName: name.trim(),
      customerEmail: email.trim(),
      customerPhone: phone.trim() || undefined,
      country,
      province: province.trim() || undefined,
      city: city.trim(),
      shippingAddress: address.trim(),
      items: orderItems,
    };

    const signature = JSON.stringify(payload);
    if (attempt.current?.signature !== signature) {
      attempt.current = { signature, key: newIdempotencyKey() };
    }

    let orderNumber: string;
    let needsQuote: boolean;
    try {
      const order = await createOrder(payload, attempt.current.key);
      orderNumber = order.orderNumber;
      needsQuote = order.shippingStatus === "ON_REQUEST";
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "We could not place your order.");
      setSubmitting(false);
      return;
    }

    // The order exists and its stock is held, so the cart has done its job.
    clear();

    if (needsQuote) {
      router.push(`/orders/${orderNumber}`);
      return;
    }
    try {
      const session = await createCheckoutSession(orderNumber);
      window.location.assign(session.checkoutUrl);
    } catch {
      // The order is safe; the order page offers a Pay button to try again.
      router.push(`/orders/${orderNumber}?payment=unavailable`);
    }
  }

  if (!hydrated) {
    return <p className="text-lg text-cream/70">Loading your cart…</p>;
  }

  if (items.length === 0) {
    return (
      <div>
        <p className="text-lg text-cream/70">Your cart is empty.</p>
        <Link
          href="/catalog"
          className="mt-6 inline-block bg-orange px-6 py-3 text-sm font-black text-black hover:bg-orange-light"
        >
          BROWSE THE CATALOG
        </Link>
      </div>
    );
  }

  const shippingKnown = quote !== null && quote.status === "QUOTED";
  const estimatedTotal = shippingKnown ? subtotal + quote.total : null;

  return (
    <form onSubmit={handleSubmit} className="grid gap-8 lg:grid-cols-[1fr_380px]">

      <div className="space-y-8">
        <fieldset className="space-y-4 border border-orange/30 bg-[#17110a] p-6">
          <legend className="px-2 text-sm font-black tracking-wide text-orange">CONTACT</legend>

          <Field label="FULL NAME">
            <input required maxLength={200} value={name} onChange={(e) => setName(e.target.value)} className={inputClass} autoComplete="name" />
          </Field>

          <div className="grid gap-4 md:grid-cols-2">
            <Field label="EMAIL" hint="Your receipt and order updates go here.">
              <input required type="email" maxLength={200} value={email} onChange={(e) => setEmail(e.target.value)} className={inputClass} autoComplete="email" />
            </Field>
            <Field label="PHONE (OPTIONAL)">
              <input type="tel" maxLength={30} value={phone} onChange={(e) => setPhone(e.target.value)} className={inputClass} autoComplete="tel" />
            </Field>
          </div>
        </fieldset>

        <fieldset className="space-y-4 border border-orange/30 bg-[#17110a] p-6">
          <legend className="px-2 text-sm font-black tracking-wide text-orange">SHIP TO</legend>

          <div className="grid gap-4 md:grid-cols-3">
            <Field label="COUNTRY">
              <select value={country} onChange={(e) => setCountry(e.target.value)} className={inputClass}>
                {COUNTRIES.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="PROVINCE / STATE (OPTIONAL)">
              <input maxLength={100} value={province} onChange={(e) => setProvince(e.target.value)} className={inputClass} />
            </Field>
            <Field label="CITY">
              <input required maxLength={100} value={city} onChange={(e) => setCity(e.target.value)} className={inputClass} autoComplete="address-level2" />
            </Field>
          </div>

          <Field label="STREET ADDRESS">
            <input required maxLength={500} value={address} onChange={(e) => setAddress(e.target.value)} className={inputClass} autoComplete="street-address" />
          </Field>
        </fieldset>
      </div>

      <aside className="h-fit space-y-4 border border-orange/30 bg-[#17110a] p-6">
        <h2 className="text-lg font-black tracking-wide text-orange">YOUR ORDER</h2>

        <ul className="space-y-2 text-sm">
          {items.map((item) => (
            <li key={item.key} className="flex justify-between gap-3">
              <span className="text-cream/80">
                {item.quantity} × {item.name}
              </span>
              <span className="shrink-0 font-bold">{money(item.unitPrice * item.quantity)}</span>
            </li>
          ))}
        </ul>

        <dl className="space-y-2 border-t border-orange/20 pt-4 text-sm">
          <div className="flex justify-between">
            <dt className="text-cream/70">Items</dt>
            <dd className="font-black">{money(subtotal)}</dd>
          </div>

          <div className="flex justify-between gap-3">
            <dt className="text-cream/70">Shipping</dt>
            <dd className="text-right font-black">
              {quoting ? (
                <span className="font-normal text-cream/60">calculating…</span>
              ) : quote ? (
                quote.status === "ON_REQUEST" ? (
                  <span className="text-orange">quoted by our team</span>
                ) : (
                  money(quote.total)
                )
              ) : (
                <span className="font-normal text-cream/60">enter your city</span>
              )}
            </dd>
          </div>

          <div className="flex justify-between">
            <dt className="text-cream/70">Taxes</dt>
            <dd className="text-cream/60">added when you order</dd>
          </div>
        </dl>

        {quote?.status === "ON_REQUEST" && (
          <p className="text-xs text-orange">
            Freight for this order needs a staff quote. We will hold your items and email you the price; you can pay
            once it is set.
          </p>
        )}
        {quote?.estimated && quote.status === "QUOTED" && (
          <p className="text-xs text-cream/50">Shipping is an estimate until the order is placed.</p>
        )}
        {quote?.note && <p className="text-xs text-cream/50">{quote.note}</p>}
        {quoteError && <p className="text-xs text-red-400">{quoteError}</p>}

        {estimatedTotal !== null && (
          <div className="flex justify-between border-t border-orange/20 pt-4 text-sm">
            <span className="font-bold text-cream/70">Before taxes</span>
            <span className="text-lg font-black text-orange">{money(estimatedTotal)}</span>
          </div>
        )}

        {error && (
          <p role="alert" className="border border-red-400/50 bg-red-950/40 p-3 text-sm text-red-200">
            {error}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="w-full bg-orange px-6 py-4 text-sm font-black text-black transition-colors hover:bg-orange-light disabled:cursor-wait disabled:opacity-60"
        >
          {submitting ? "PLACING YOUR ORDER…" : quote?.status === "ON_REQUEST" ? "PLACE ORDER" : "PLACE ORDER AND PAY"}
        </button>

        <p className="text-xs text-cream/40">
          Your items are held for you while you pay. Payment is handled on Stripe&apos;s secure page.
        </p>
      </aside>

    </form>
  );
}
