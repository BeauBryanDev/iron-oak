import Link from "next/link";
import { notFound, permanentRedirect } from "next/navigation";

import AddToCartButton from "@/components/AddToCartButton";
import BackButton from "@/components/BackButton";
import ApiUnavailable from "@/components/ApiUnavailable";
import ProductImage from "@/components/ProductImage";
import { getProduct, getProductBySku } from "@/lib/api/catalog";
import { ApiError } from "@/lib/api/http";
import type { ProductResponse } from "@/lib/api/types";
import { formatWeight, money, productImageUrl } from "@/lib/format";
import { slugify } from "@/lib/slug";

/**
 * /product/{sku}, e.g. /product/IO-CLAM-001. Old /product/{id} links (a plain number) still
 * work: they load the product by id and redirect to its SKU address.
 */
export default async function ProductPage({ params }: { params: Promise<{ sku: string }> }) {
  const { sku } = await params;
  const key = decodeURIComponent(sku).trim().toUpperCase();
  const isLegacyId = /^\d{1,18}$/.test(key);

  if (!key) notFound();

  let product: ProductResponse;
  try {
    product = isLegacyId
      ? await getProduct(Number(key))
      : await getProductBySku(key);
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    return (
      <div className="min-h-screen bg-[#241b0e] px-8 py-16">
        <ApiUnavailable error={error} what="this product" />
      </div>
    );
  }

  // one address per product: old id links and lower-case SKUs land on /product/{SKU}
  if (isLegacyId || sku !== product.sku) {
    permanentRedirect(`/product/${encodeURIComponent(product.sku)}`);
  }

  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-12">
      <div className="mx-auto max-w-6xl">
        <BackButton fallback="/catalog" />

        <p className="text-sm font-bold tracking-wide text-cream/60">
          <Link href="/catalog" className="hover:text-orange">
            CATALOG
          </Link>{" "}
          /{" "}
          <Link href={`/catalog/${slugify(product.category)}`} className="hover:text-orange">
            {product.category.toUpperCase()}
          </Link>
        </p>

        <div className="mt-6 grid gap-10 md:grid-cols-2">
          <div className="flex aspect-square items-center justify-center overflow-hidden border border-orange/30 bg-white">
            <ProductImage src={productImageUrl(product)} alt={product.name} className="h-full w-full object-contain" />
          </div>

          <div>
            {product.brand && <p className="font-black tracking-widest text-orange">{product.brand.toUpperCase()}</p>}

            <h1 className="industrial-title mt-2 text-5xl leading-tight">{product.name}</h1>

            <p className="mt-6 text-lg leading-8 text-cream/70">{product.description}</p>

            <div className="mt-6 text-4xl font-black text-orange">{money(product.price)}</div>

            <dl className="mt-6 grid grid-cols-2 gap-4 border-t border-orange/20 pt-5 text-sm">
              <div>
                <dt className="text-xs font-bold tracking-wide text-cream/50">AVAILABILITY</dt>
                <dd className="mt-1 font-black">
                  {product.inStock ? `${product.stockQuantity} in stock` : "Out of stock"}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-bold tracking-wide text-cream/50">WARRANTY</dt>
                <dd className="mt-1 font-black">
                  {product.warrantyMonths > 0 ? `${product.warrantyMonths} months` : "None"}
                </dd>
              </div>
              <div>
                <dt className="text-xs font-bold tracking-wide text-cream/50">WEIGHT</dt>
                <dd className="mt-1 font-black">{formatWeight(product.weightKg) ?? "Not listed"}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold tracking-wide text-cream/50">SKU</dt>
                <dd className="mt-1 font-black">{product.sku}</dd>
              </div>
              <div>
                <dt className="text-xs font-bold tracking-wide text-cream/50">CATEGORY</dt>
                <dd className="mt-1 font-black">{product.category}</dd>
              </div>
            </dl>

            <div className="mt-8">
              <AddToCartButton
                withQuantity
                label="ADD TO CART"
                disabled={!product.inStock}
                item={{
                  itemType: "PRODUCT",
                  referenceId: product.id,
                  name: product.name,
                  code: product.sku,
                  unitPrice: product.price,
                  imageUrl: productImageUrl(product),
                  maxQuantity: product.stockQuantity,
                }}
              />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
