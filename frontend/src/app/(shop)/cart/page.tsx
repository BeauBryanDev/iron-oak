"use client";

import Link from "next/link";
import { Minus, Plus, Trash2 } from "lucide-react";

import ProductImage from "@/components/ProductImage";
import { useCart } from "@/lib/cart/CartContext";
import { money } from "@/lib/format";

export default function CartPage() {
  const { items, hydrated, count, subtotal, setQuantity, remove, clear } = useCart();

  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-16">
      <div className="mx-auto max-w-6xl">

        <p className="font-black tracking-widest text-orange">
          YOUR ORDER
        </p>

        <h1 className="industrial-title mt-3 text-6xl">
          CART
        </h1>

        {!hydrated ? (
          <p className="mt-6 text-lg text-cream/70">Loading your cart…</p>
        ) : items.length === 0 ? (
          <div className="mt-6">
            <p className="text-lg text-cream/70">Your cart is empty.</p>
            <Link
              href="/catalog"
              className="mt-6 inline-block bg-orange px-6 py-3 text-sm font-black text-black hover:bg-orange-light"
            >
              BROWSE THE CATALOG
            </Link>
          </div>
        ) : (
          <div className="mt-10 grid gap-8 lg:grid-cols-[1fr_340px]">

            <ul className="border border-orange/30 bg-[#17110a]">
              {items.map((item) => (
                <li
                  key={item.key}
                  className="flex flex-wrap items-center gap-4 border-b border-orange/15 p-4 last:border-b-0"
                >
                  <div className="h-20 w-20 shrink-0 bg-white">
                    <ProductImage src={item.imageUrl} alt={item.name} className="h-full w-full object-contain p-2" />
                  </div>

                  <div className="min-w-0 flex-1">
                    <p className="font-black text-cream">{item.name}</p>
                    <p className="text-xs text-cream/50">
                      {item.itemType === "MACHINE" ? "Machine" : "SKU"} {item.code}
                    </p>
                    <p className="mt-1 text-sm font-bold text-orange">{money(item.unitPrice)}</p>
                  </div>

                  <div className="flex items-center border border-orange/40">
                    <button
                      type="button"
                      aria-label={`Decrease quantity of ${item.name}`}
                      onClick={() => setQuantity(item.key, item.quantity - 1)}
                      className="px-3 py-2 hover:bg-orange/10"
                    >
                      <Minus className="h-4 w-4" />
                    </button>
                    <span className="w-10 text-center font-black">{item.quantity}</span>
                    <button
                      type="button"
                      aria-label={`Increase quantity of ${item.name}`}
                      onClick={() => setQuantity(item.key, item.quantity + 1)}
                      disabled={item.maxQuantity !== null && item.quantity >= item.maxQuantity}
                      className="px-3 py-2 hover:bg-orange/10 disabled:opacity-30"
                    >
                      <Plus className="h-4 w-4" />
                    </button>
                  </div>

                  <p className="w-28 text-right font-black text-cream">{money(item.unitPrice * item.quantity)}</p>

                  <button
                    type="button"
                    aria-label={`Remove ${item.name}`}
                    onClick={() => remove(item.key)}
                    className="text-cream/50 hover:text-orange"
                  >
                    <Trash2 className="h-5 w-5" />
                  </button>
                </li>
              ))}
            </ul>

            <aside className="h-fit border border-orange/30 bg-[#17110a] p-6">
              <h2 className="text-lg font-black tracking-wide text-orange">ORDER SUMMARY</h2>

              <dl className="mt-4 space-y-2 text-sm">
                <div className="flex justify-between">
                  <dt className="text-cream/70">Items ({count})</dt>
                  <dd className="font-black">{money(subtotal)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-cream/70">Shipping</dt>
                  <dd className="text-cream/60">at checkout</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-cream/70">Taxes</dt>
                  <dd className="text-cream/60">at checkout</dd>
                </div>
              </dl>

              <p className="mt-4 text-xs text-cream/50">
                Prices are confirmed by our system when the order is placed.
              </p>

              <Link
                href="/checkout"
                className="mt-6 block bg-orange px-6 py-4 text-center text-sm font-black text-black hover:bg-orange-light"
              >
                CHECKOUT
              </Link>

              <button
                type="button"
                onClick={clear}
                className="mt-3 w-full text-center text-xs font-bold text-cream/50 hover:text-orange"
              >
                EMPTY CART
              </button>
            </aside>

          </div>
        )}

      </div>
    </section>
  );
}
