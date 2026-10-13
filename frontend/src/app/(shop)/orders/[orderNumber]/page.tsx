import Link from "next/link";

import OrderStatusView from "@/components/OrderStatusView";

export default async function OrderPage({
  params,
  searchParams,
}: {
  params: Promise<{ orderNumber: string }>;
  searchParams: Promise<{ payment?: string }>;
}) {
  const { orderNumber } = await params;
  const { payment } = await searchParams;

  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-16">
      <div className="mx-auto max-w-6xl">
        <p className="font-black tracking-widest text-orange">YOUR ORDER</p>

        <h1 className="industrial-title mt-3 text-5xl md:text-6xl">ORDER STATUS</h1>

        <div className="mt-10">
          <OrderStatusView orderNumber={orderNumber} paymentUnavailable={payment === "unavailable"} />
        </div>

        <Link href="/catalog" className="mt-10 inline-block text-sm font-black text-orange hover:text-orange-light">
          ← CONTINUE SHOPPING
        </Link>
      </div>
    </section>
  );
}
