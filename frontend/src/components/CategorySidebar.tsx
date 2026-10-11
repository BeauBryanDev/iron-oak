import Link from "next/link";
import { ChevronRight, ArrowRight } from "lucide-react";

import ShopNowButton from "./ShopNowButton";
import { listCategories } from "@/lib/api/catalog";
import { slugify } from "@/lib/slug";

async function loadCategories(): Promise<string[]> {
  try {
    return await listCategories();
  } catch {
    // The sidebar is navigation: an unreachable API should not take the whole page down.
    return [];
  }
}

export default async function CategorySidebar() {
  const categories = await loadCategories();

  return (
    <aside className="w-full shrink-0 lg:w-64">

      {/* CATEGORY LIST */}
      <nav className="border border-orange/30 bg-[#17110a]">

        <h2 className="border-b border-orange/30 px-5 py-4 text-lg font-black tracking-wide text-orange">
          SHOP BY CATEGORY
        </h2>

        {categories.length === 0 ? (
          <p className="px-5 py-4 text-sm text-cream/60">Categories are unavailable right now.</p>
        ) : (
          <ul>
            {categories.map((category) => (
              <li key={category}>
                <Link
                  href={`/catalog/${slugify(category)}`}
                  className="flex items-center justify-between border-b border-orange/15 px-5 py-3 text-sm font-semibold text-cream transition-colors hover:bg-orange/10 hover:text-orange"
                >
                  {category}

                  <ChevronRight className="h-4 w-4 shrink-0 text-orange/70" />
                </Link>
              </li>
            ))}
          </ul>
        )}

        <Link
          href="/catalog"
          className="group flex items-center gap-2 px-5 py-4 text-sm font-black text-orange transition-colors hover:text-orange-light"
        >
          View All Products

          <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
        </Link>
      </nav>

      {/* PROMO CARD */}
      <div className="relative mt-4 min-h-[380px] overflow-hidden border border-orange/30">

        <img
          src="/worker_image.jpg"
          alt="Workshop bench stacked with timber"
          className="absolute inset-0 h-full w-full object-cover"
        />

        <div className="absolute inset-0 bg-black/60" />

        <div className="relative flex min-h-[380px] flex-col justify-end p-6">

          <h3 className="industrial-title text-4xl leading-[0.95] text-cream">
            HIGH QUALITY TOOLS
          </h3>

          <p className="mt-4 text-sm leading-6 text-cream/80">
            From foundation to finishing touches, we&apos;ve got you covered.
          </p>

          <ShopNowButton
            size="sm"
            label="LEARN MORE"
            href="/about"
            className="mt-5"
          />
        </div>
      </div>
    </aside>
  );
}
