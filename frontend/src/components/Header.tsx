"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import {
  Search,
  ShoppingCart,
  UserRound,
} from "lucide-react";

import { useCart } from "@/lib/cart/CartContext";

export default function Header() {
  const router = useRouter();
  const { count, hydrated } = useCart();
  const [query, setQuery] = useState("");

  function handleSearch(event: FormEvent) {
    event.preventDefault();
    const term = query.trim();
    router.push(term ? `/catalog?q=${encodeURIComponent(term)}` : "/catalog");
  }

  return (
    <header className="sticky top-0 z-50 border-b border-orange/40 bg-[#17110a]">
      <div className="flex h-20 items-center">

        {/* BRAND */}
        <Link
          href="/"
          className="flex h-full items-center gap-3 border-r border-orange/30 px-6"
        >
          <img
            src="/assets/main_tools.svg"
            alt=""
            className="h-11 w-11 shrink-0"
          />

          <div>
            <div className="text-3xl font-black tracking-tight text-cream">
              IRON & OAK
            </div>

            <div className="text-xs font-bold tracking-[0.25em] text-orange">
              HARDWARE SUPPLY CO.
            </div>
          </div>

          <img
            src="/assets/metal_oak_icon.svg"
            alt=""
            className="h-10 w-10 shrink-0"
          />
        </Link>

        {/* SLOGAN */}
        <div className="hidden px-6 lg:block">
          <div className="text-lg font-black text-orange">
            BUILT TO WORK
          </div>

          <div className="text-xs text-cream/60">
            TOOLS. MATERIALS. PEOPLE.
          </div>
        </div>

        {/* NAVIGATION */}
        <nav className="flex flex-1 justify-center gap-8">
          <Link href="/" className="font-bold hover:text-orange">
            Home
          </Link>

          <Link href="/catalog" className="font-bold hover:text-orange">
            Catalog
          </Link>

          <Link href="/machines" className="font-bold hover:text-orange">
            Machines
          </Link>

          <Link href="/services" className="font-bold hover:text-orange">
            Services
          </Link>

          <Link href="/about" className="font-bold hover:text-orange">
            About Us
          </Link>
        </nav>

        {/* ACTIONS */}
        <div className="flex items-center gap-4 px-5">
          <img
            src="/assets/tools.svg"
            alt=""
            className="hidden h-8 w-8 shrink-0 xl:block"
          />

          <form
            onSubmit={handleSearch}
            role="search"
            className="hidden items-center border border-orange/40 px-3 py-2 xl:flex"
          >
            <button type="submit" aria-label="Search" className="mr-2">
              <Search className="h-5 w-5 text-orange" />
            </button>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              placeholder="Search tools, materials..."
              className="w-52 bg-transparent text-sm outline-none"
            />
          </form>

          <Link href="/orders" aria-label="Track an order" title="Track an order">
            <UserRound className="h-6 w-6 hover:text-orange" />
          </Link>

          <Link href="/cart" aria-label="Cart" className="relative">
            <ShoppingCart className="h-7 w-7 text-orange" />

            <span className="absolute -right-2 -top-2 flex h-5 min-w-5 items-center justify-center rounded-full bg-orange px-1 text-xs font-bold text-black">
              {hydrated ? count : 0}
            </span>
          </Link>
        </div>
      </div>
    </header>
  );
}
