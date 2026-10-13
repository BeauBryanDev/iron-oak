import OrderLookupForm from "@/components/OrderLookupForm";

export default function OrdersPage() {
  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-16">
      <div className="mx-auto max-w-6xl">
        <p className="font-black tracking-widest text-orange">YOUR ORDER</p>

        <h1 className="industrial-title mt-3 text-5xl md:text-6xl">TRACK AN ORDER</h1>

        <p className="mt-6 max-w-2xl text-lg leading-8 text-cream/70">
          Enter the order number from your confirmation to see its status, or to pay an order that is still waiting.
        </p>

        <div className="mt-8">
          <OrderLookupForm />
        </div>
      </div>
    </section>
  );
}
