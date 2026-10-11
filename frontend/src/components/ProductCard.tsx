import Link from "next/link";
import { Heart } from "lucide-react";

import AddToCartButton from "./AddToCartButton";
import { imageSrc, money } from "@/lib/format";
import type { ProductResponse } from "@/lib/api/types";

export default function ProductCard({ product }: { product: ProductResponse }) {
  return (
    <article className="flex flex-col border border-orange/30 bg-[#f3eadb] text-black">
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

        <img
          src={imageSrc(product.imageUrl)}
          alt={product.name}
          className="h-full w-full object-contain p-5"
        />
      </div>

      <div className="flex flex-1 flex-col p-5">
        <h3 className="text-lg font-black">{product.name}</h3>

        {product.brand && <p className="mt-1 text-xs font-bold tracking-wide text-black/50">{product.brand}</p>}

        <p className="mt-2 line-clamp-3 flex-1 text-sm text-black/60">{product.description}</p>

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
