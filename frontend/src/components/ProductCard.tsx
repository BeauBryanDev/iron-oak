import Link from "next/link";
import { Heart } from "lucide-react";

import AddToCartButton from "./AddToCartButton";
import ProductImage from "./ProductImage";
import { LOW_STOCK_THRESHOLD, formatWeight, money } from "@/lib/format";
import type { ProductResponse } from "@/lib/api/types";

export default function ProductCard({ product }: { product: ProductResponse }) {
  return (
    <article className="flex flex-col border border-orange/30 bg-[#dbac60] text-black">
      <div className="relative h-56 bg-white">
        <span
          className={`absolute left-3 top-3 px-3 py-1 text-xs font-black ${
            product.inStock ? "bg-orange text-black" : "bg-black text-cream"
          }`}
        >
          {product.inStock ? product.category.toUpperCase() : "OUT OF STOCK"}
        </span>

        <button type="button" aria-label="Save for later" className="absolute right-3 top-3">
          <Heart className="h-5 w-5" />
        </button>

        <ProductImage
          src={product.imageUrl}
          alt={product.name}
          className="h-full w-full object-contain p-5"
        />
      </div>

      <div className="flex flex-1 flex-col p-5">
        <h3 className="text-lg font-black">{product.name}</h3>

        <p className="mt-2 line-clamp-3 flex-1 text-sm text-black/70">{product.description}</p>

        <dl className="mt-4 space-y-1 border-t border-black/15 pt-3 text-xs">
          <div className="flex justify-between gap-3">
            <dt className="font-bold tracking-wide text-black/60">BRAND</dt>
            <dd className="truncate text-right font-black">{product.brand ?? "—"}</dd>
          </div>

          <div className="flex justify-between gap-3">
            <dt className="font-bold tracking-wide text-black/60">STOCK</dt>
            <dd className="text-right font-black">
              {!product.inStock
                ? "Out of stock"
                : product.stockQuantity <= LOW_STOCK_THRESHOLD
                  ? `Only ${product.stockQuantity} left`
                  : `${product.stockQuantity} in stock`}
            </dd>
          </div>

          <div className="flex justify-between gap-3">
            <dt className="font-bold tracking-wide text-black/60">WEIGHT</dt>
            <dd className="text-right font-black">{formatWeight(product.weightKg) ?? "Not listed"}</dd>
          </div>
        </dl>

        <div className="mt-4 text-2xl font-black text-orange">{money(product.price)}</div>

        <div className="mt-5 flex gap-2">
          <Link
            href={`/product/${product.id}`}
            className="flex-1 border border-black px-3 py-3 text-center text-sm font-black hover:bg-black hover:text-cream"
          >
            DETAILS
          </Link>

          <AddToCartButton
            disabled={!product.inStock}
            item={{
              itemType: "PRODUCT",
              referenceId: product.id,
              name: product.name,
              code: product.sku,
              unitPrice: product.price,
              imageUrl: product.imageUrl,
              maxQuantity: product.stockQuantity,
            }}
          />
        </div>
      </div>
    </article>
  );
}
