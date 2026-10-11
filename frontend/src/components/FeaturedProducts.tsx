import ApiUnavailable from "./ApiUnavailable";
import ProductGrid from "./ProductGrid";
import { listProducts } from "@/lib/api/catalog";

const FEATURED_COUNT = 8;

/** Home-page strip: the first products the backend returns (name order). */
export default async function FeaturedProducts() {
  try {
    const page = await listProducts({ size: FEATURED_COUNT });
    return (
      <ProductGrid
        products={page.content}
        heading={{ title: "TODAY'S FEATURED PRODUCTS", actionLabel: "VIEW ALL PRODUCTS →", actionHref: "/catalog" }}
      />
    );
  } catch (error) {
    return <ApiUnavailable error={error} what="products" />;
  }
}
