import Link from "next/link";

import ProductCard from "./ProductCard";
import type { ProductResponse } from "@/lib/api/types";

type Heading = {
  eyebrow?: string;
  title: string;
  actionLabel?: string;
  actionHref?: string;
};

export default function ProductGrid({
  products,
  heading,
}: {
  products: ProductResponse[];
  heading?: Heading;
}) {
  return (
    <section className="border border-orange/30 bg-[#241b0e] p-6">
      {heading && (
        <div className="mb-7 flex items-end justify-between gap-4">
          <div>
            <p className="font-black tracking-widest text-orange">{heading.eyebrow ?? "IRON & OAK"}</p>
            <h2 className="industrial-title text-4xl">{heading.title}</h2>
          </div>

          {heading.actionHref && (
            <Link href={heading.actionHref} className="shrink-0 font-bold text-orange hover:text-orange-light">
              {heading.actionLabel ?? "VIEW ALL →"}
            </Link>
          )}
        </div>
      )}

      {products.length === 0 ? (
        <p className="py-10 text-center text-cream/60">No products found.</p>
      ) : (
        <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-4">
          {products.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      )}
    </section>
  );
}
