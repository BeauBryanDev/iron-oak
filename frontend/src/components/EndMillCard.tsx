import Link from "next/link";

import AddToCartButton from "./AddToCartButton";
import ProductImage from "./ProductImage";
import type { EndMillResponse } from "@/lib/api/types";
import { endMillImageUrl, money } from "@/lib/format";

/** "2 mm", "0.5 mm": BigDecimals arrive as numbers like 2.00. */
export function mm(value: number): string {
  return `${Number(value)} mm`;
}

export default function EndMillCard({ endMill }: { endMill: EndMillResponse }) {
  return (
    <article className="flex flex-col border border-orange/30 bg-[#dbac60] text-black">
      <div className="relative aspect-square overflow-hidden bg-white">
        <span
          className={`absolute left-3 top-3 z-10 px-3 py-1 text-xs font-black ${
            endMill.inStock ? "bg-orange text-black" : "bg-black text-cream"
          }`}
        >
          {endMill.inStock ? endMill.fluteType.toUpperCase() : "OUT OF STOCK"}
        </span>

        <ProductImage
          src={endMillImageUrl(endMill)}
          alt={endMill.name}
          className="h-full w-full object-contain"
        />
      </div>

      <div className="flex flex-1 flex-col p-5">
        <h3 className="text-lg font-black">{endMill.name}</h3>
        <p className="text-xs font-bold tracking-wide text-black/60">SKU {endMill.sku}</p>

        <dl className="mt-4 flex-1 space-y-1 border-t border-black/15 pt-3 text-xs">
          <div className="flex justify-between gap-3">
            <dt className="font-bold tracking-wide text-black/60">DIAMETER</dt>
            <dd className="font-black">{mm(endMill.cuttingDiameterMm)}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="font-bold tracking-wide text-black/60">RADIUS</dt>
            <dd className="font-black">{Number(endMill.radiusMm) > 0 ? mm(endMill.radiusMm) : "Flat"}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="font-bold tracking-wide text-black/60">LENGTH</dt>
            <dd className="font-black">{mm(endMill.overallLengthMm)}</dd>
          </div>
          <div className="flex justify-between gap-3">
            <dt className="font-bold tracking-wide text-black/60">MAX SPEED</dt>
            <dd className="font-black">
              {endMill.maxRpm ? `${endMill.maxRpm.toLocaleString("en-US")} rpm` : "Not listed"}
            </dd>
          </div>
        </dl>

        <div className="mt-4 text-2xl font-black text-orange">{money(endMill.price)}</div>

        <div className="mt-5 flex gap-2">
          <Link
            href={`/end-mills/${encodeURIComponent(endMill.sku)}`}
            className="flex-1 border border-black px-3 py-3 text-center text-sm font-black hover:bg-black hover:text-cream"
          >
            DETAILS
          </Link>

          <AddToCartButton
            disabled={!endMill.inStock}
            item={{
              itemType: "END_MILL",
              referenceId: endMill.id,
              name: endMill.name,
              code: endMill.sku,
              unitPrice: endMill.price,
              imageUrl: endMillImageUrl(endMill),
              maxQuantity: null,
            }}
          />
        </div>
      </div>
    </article>
  );
}
