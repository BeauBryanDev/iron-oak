import Link from "next/link";
import { notFound, permanentRedirect } from "next/navigation";

import AddToCartButton from "@/components/AddToCartButton";
import BackButton from "@/components/BackButton";
import ApiUnavailable from "@/components/ApiUnavailable";
import { mm } from "@/components/EndMillCard";
import ProductImage from "@/components/ProductImage";
import { getEndMill, getEndMillBySku } from "@/lib/api/catalog";
import { ApiError } from "@/lib/api/http";
import type { EndMillResponse } from "@/lib/api/types";
import { endMillImageUrl, money } from "@/lib/format";

/**
 * /end-mills/{sku}, e.g. /end-mills/BN-002. A plain number (an id) or a lower-case SKU loads
 * the end mill and redirects to its SKU address, as product pages do.
 */
export default async function EndMillPage({ params }: { params: Promise<{ sku: string }> }) {
  const { sku } = await params;
  const key = decodeURIComponent(sku).trim().toUpperCase();
  const isLegacyId = /^\d{1,18}$/.test(key);

  if (!key) notFound();

  let endMill: EndMillResponse;
  try {
    endMill = isLegacyId
      ? await getEndMill(Number(key))
      : await getEndMillBySku(key);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    return (
      <div className="min-h-screen bg-[#241b0e] px-8 py-16">
        <ApiUnavailable error={error} what="this end mill" />
      </div>
    );
  }

  if (isLegacyId || sku !== endMill.sku) {
    permanentRedirect(`/end-mills/${encodeURIComponent(endMill.sku)}`);
  }

  const specs: [string, string][] = [
    ["SKU", endMill.sku],
    ["FLUTE TYPE", endMill.fluteType],
    ["CUTTING DIAMETER", mm(endMill.cuttingDiameterMm)],
    ["CORNER / BALL RADIUS", Number(endMill.radiusMm) > 0 ? mm(endMill.radiusMm) : "Flat (0 mm)"],
    ["OVERALL LENGTH", mm(endMill.overallLengthMm)],
    ["MATERIAL", endMill.material],
    ["CUT DIRECTION", endMill.cutDirection],
    ["MAX SPEED", endMill.maxRpm ? `${endMill.maxRpm.toLocaleString("en-US")} rpm` : "Not listed"],
    ["WEIGHT", endMill.weightG ? `${Number(endMill.weightG)} g` : "Not listed"],
    ["WARRANTY", "None (consumable)"],
  ];

  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-12 text-cream">
      <div className="mx-auto max-w-6xl">
        <BackButton fallback="/end-mills" />

        <p className="text-sm font-bold tracking-wide text-cream/60">
          <Link href="/end-mills" className="hover:text-orange">
            END MILLS
          </Link>{" "}
          / {endMill.fluteType.toUpperCase()}
        </p>

        <div className="mt-6 grid gap-10 md:grid-cols-2">
          <div className="flex aspect-square items-center justify-center overflow-hidden border border-orange/30 bg-white">
            <ProductImage src={endMillImageUrl(endMill)} alt={endMill.name} className="h-full w-full object-contain" />
          </div>

          <div>
            <p className="font-black tracking-widest text-orange">SOLID CARBIDE END MILL</p>
            <h1 className="industrial-title mt-2 text-5xl leading-tight">{endMill.name.toUpperCase()}</h1>

            {endMill.description && (
              <p className="mt-6 text-lg leading-8 text-cream/70">{endMill.description}</p>
            )}

            <div className="mt-6 text-4xl font-black text-orange">{money(endMill.price)}</div>
            <p className="mt-1 text-sm font-bold text-cream/60">
              {endMill.inStock ? "In stock" : "Out of stock"}
            </p>

            <dl className="mt-6 grid grid-cols-2 gap-4 border-t border-orange/20 pt-5 text-sm">
              {specs.map(([label, value]) => (
                <div key={label}>
                  <dt className="text-xs font-bold tracking-wide text-cream/50">{label}</dt>
                  <dd className="mt-1 font-black">{value}</dd>
                </div>
              ))}
            </dl>

            <div className="mt-8">
              <AddToCartButton
                withQuantity
                label="ADD TO CART"
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
        </div>
      </div>
    </section>
  );
}
