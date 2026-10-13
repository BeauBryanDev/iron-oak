import Link from "next/link";
import { notFound } from "next/navigation";

import ApiUnavailable from "@/components/ApiUnavailable";
import CatalogListing from "@/components/CatalogListing";
import { listCategories } from "@/lib/api/catalog";
import { slugify } from "@/lib/slug";

export default async function CategoryPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ page?: string }>;
}) {
  const { slug } = await params;
  const { page } = await searchParams;
  const pageNumber = Math.max(1, Number.parseInt(page ?? "1", 10) || 1);

  let categories: string[];
  try {
    categories = await listCategories();
  } catch (error) {
    return (
      <div className="min-h-screen bg-[#241b0e] px-8 pt-10">
        <ApiUnavailable error={error} what="this category" />
      </div>
    );
  }

  // the backend owns the category names; the URL slug is derived from them
  const category = categories.find((name) => slugify(name) === slug);
  if (!category) notFound();

  return (
    <div className="min-h-screen bg-[#241b0e] pt-10">
      <div className="px-8 pb-8">
        <p className="font-black tracking-widest text-orange">
          <Link href="/catalog" className="hover:text-orange-light">
            CATALOG
          </Link>{" "}
          /
        </p>

        <h1 className="industrial-title text-6xl">{category.toUpperCase()}</h1>
      </div>

      <div className="px-8 pb-16">
        <CatalogListing basePath={`/catalog/${slug}`} category={category} page={pageNumber} />
      </div>
    </div>
  );
}
