import ApiUnavailable from "./ApiUnavailable";
import Pagination from "./Pagination";
import ProductGrid from "./ProductGrid";
import { listProducts } from "@/lib/api/catalog";

const PAGE_SIZE = 12;

type Props = {
  basePath: string;
  category?: string;
  q?: string;
  /** one-based page number from the URL */
  page: number;
};

/** A paged product listing for the catalog and its category pages. */
export default async function CatalogListing({ basePath, category, q, page }: Props) {
  try {
    const result = await listProducts({ category, q, page: page - 1, size: PAGE_SIZE });
    return (
      <>
        <ProductGrid products={result.content} />
        <Pagination
          basePath={basePath}
          page={result.page.number}
          totalPages={result.page.totalPages}
          params={{ q }}
        />
      </>
    );
  } catch (error) {
    return <ApiUnavailable error={error} what="products" />;
  }
}
