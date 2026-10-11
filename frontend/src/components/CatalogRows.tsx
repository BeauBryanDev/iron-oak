import ApiUnavailable from "./ApiUnavailable";
import ProductCard from "./ProductCard";
import ProductCarousel from "./ProductCarousel";
import { listCategories, listProducts } from "@/lib/api/catalog";
import { slugify } from "@/lib/slug";

const ROW_SIZE = 12;

/** The default catalog view: one carousel row per storefront category, loaded in parallel. */
export default async function CatalogRows() {
  let categories: string[];
  try {
    categories = await listCategories();
  } catch (error) {
    return <ApiUnavailable error={error} what="the catalog" />;
  }

  const rows = await Promise.allSettled(
    categories.map(async (category) => ({
      category,
      page: await listProducts({ category, size: ROW_SIZE }),
    })),
  );

  const loaded = rows.flatMap((row) => (row.status === "fulfilled" && row.value.page.content.length > 0 ? [row.value] : []));

  if (loaded.length === 0) {
    const firstFailure = rows.find((row) => row.status === "rejected");
    return firstFailure ? (
      <ApiUnavailable error={firstFailure.reason} what="the catalog" />
    ) : (
      <p className="py-10 text-center text-cream/60">No products found.</p>
    );
  }

  return (
    <div className="space-y-6">
      {loaded.map(({ category, page }) => (
        <ProductCarousel
          key={category}
          title={category.toUpperCase()}
          href={`/catalog/${slugify(category)}`}
          linkLabel={`VIEW ALL (${page.page.totalElements}) →`}
        >
          {page.content.map((product) => (
            <ProductCard key={product.id} product={product} />
          ))}
        </ProductCarousel>
      ))}
    </div>
  );
}
