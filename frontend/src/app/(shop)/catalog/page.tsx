import CatalogListing from "@/components/CatalogListing";
import CatalogRows from "@/components/CatalogRows";

export default async function CatalogPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string; page?: string }>;
}) {
  const { q, page } = await searchParams;
  const search = q?.trim() || undefined;
  const pageNumber = Math.max(1, Number.parseInt(page ?? "1", 10) || 1);

  return (
    <div className="min-h-screen bg-[#241b0e] pt-10">
      <div className="px-8 pb-8">
        <p className="font-black tracking-widest text-orange">
          IRON & OAK
        </p>

        <h1 className="industrial-title text-6xl">
          {search ? `RESULTS FOR "${search.toUpperCase()}"` : "HARDWARE CATALOG"}
        </h1>

        <p className="mt-3 max-w-2xl text-cream/60">
          Professional tools, materials and equipment
          built for serious work.
        </p>
      </div>

      <div className="px-8 pb-16">
        {/* search results are one paged grid; browsing is a carousel row per category */}
        {search ? <CatalogListing basePath="/catalog" q={search} page={pageNumber} /> : <CatalogRows />}
      </div>
    </div>
  );
}
