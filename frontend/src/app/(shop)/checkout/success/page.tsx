import Link from "next/link";

import PaymentConfirmation from "@/components/PaymentConfirmation";
import { parseOrderNumber } from "@/lib/orderNumber";

/** Stripe returns here as /checkout/success?order=IO-...&session_id=... after a payment. */
export default async function CheckoutSuccessPage({
  searchParams,
}: {
  searchParams: Promise<{ order?: string | string[] }>;
}) {
  const orderNumber = parseOrderNumber((await searchParams).order);

  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-16">
      <div className="mx-auto max-w-6xl">
        <p className="font-black tracking-widest text-orange">THANK YOU</p>

        <h1 className="industrial-title mt-3 text-5xl md:text-6xl">PAYMENT</h1>

        <div className="mt-10">
          {orderNumber ? (
            <PaymentConfirmation orderNumber={orderNumber} />
          ) : (
            <div className="border border-orange/40 bg-[#17110a] p-6">
              <p className="font-black tracking-wide text-orange">WE COULD NOT FIND YOUR ORDER NUMBER</p>
              <p className="mt-2 text-sm text-cream/70">
                If you just paid, your order is safe. Enter the order number from your confirmation to see its status.
              </p>
              <Link href="/orders" className="mt-4 inline-block text-sm font-black text-orange hover:text-orange-light">
                TRACK AN ORDER →
              </Link>
            </div>
          )}
        </div>
      </div>
    </section>
  );
}
