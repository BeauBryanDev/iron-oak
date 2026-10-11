"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";

/** Order numbers look like IO-20261010-ABCD1234. */
export default function OrderLookupForm() {
  const router = useRouter();
  const [value, setValue] = useState("");

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    const orderNumber = value.trim().toUpperCase();
    if (orderNumber) router.push(`/orders/${encodeURIComponent(orderNumber)}`);
  }

  return (
    <form onSubmit={handleSubmit} className="flex max-w-xl flex-col gap-3 sm:flex-row">
      <input
        required
        value={value}
        onChange={(e) => setValue(e.target.value)}
        placeholder="IO-20261010-ABCD1234"
        aria-label="Order number"
        className="flex-1 border border-orange/40 bg-[#17110a] px-4 py-3 text-cream outline-none focus:border-orange"
      />
      <button type="submit" className="bg-orange px-6 py-3 text-sm font-black text-black hover:bg-orange-light">
        TRACK ORDER
      </button>
    </form>
  );
}
