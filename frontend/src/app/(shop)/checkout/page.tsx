import CheckoutForm from "@/components/CheckoutForm";

export default function CheckoutPage() {
  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-16">
      <div className="mx-auto max-w-6xl">
        <p className="font-black tracking-widest text-orange">SECURE CHECKOUT</p>

        <h1 className="industrial-title mt-3 text-6xl">CHECKOUT</h1>

        <div className="mt-10">
          <CheckoutForm />
        </div>
      </div>
    </section>
  );
}
