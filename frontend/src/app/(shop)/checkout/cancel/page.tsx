import Link from "next/link";

import OrderStatusView from "@/components/OrderStatusView";
import { parseOrderNumber } from "@/lib/orderNumber";

/** Stripe returns here as /checkout/cancel?order=IO-... when the customer backs out of the payment page. */
export default async function CheckoutCancelPage({
  searchParams,
}: {
  searchParams: Promise<{ order?: string | string[] }>;
}) {
  const orderNumber = parseOrderNumber((await searchParams).order);

  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-16">
      <div className="mx-auto max-w-6xl">
        <p className="font-black tracking-widest text-orange">PAYMENT CANCELLED</p>

        <h1 className="industrial-title mt-3 text-5xl md:text-6xl">NOT PAID YET</h1>

        <p className="mt-6 max-w-2xl text-lg leading-8 text-cream/70">
          You left the payment page, and nothing was charged.
          {orderNumber
            ? " Your order is still here and your items are held for you for a limited time. Pay now to keep them."
            : " If you have an order number you can look it up and pay from there."}
        </p>

        <div className="mt-10">
          {orderNumber ? (
            <OrderStatusView orderNumber={orderNumber} />
          ) : (
            <Link href="/orders" className="inline-block text-sm font-black text-orange hover:text-orange-light">
              TRACK AN ORDER →
            </Link>
          )}
        </div>

        <Link href="/catalog" className="mt-10 inline-block text-sm font-black text-orange hover:text-orange-light">
          ← CONTINUE SHOPPING
        </Link>
      </div>
    </section>
  );
}
