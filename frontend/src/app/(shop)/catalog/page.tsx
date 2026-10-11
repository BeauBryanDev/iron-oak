import ProductGrid from "@/components/ProductGrid";

export default function CatalogPage() {
  return (
    <div className="min-h-screen bg-[#241b0e] pt-10">
      <div className="px-8 pb-8">
        <p className="font-black tracking-widest text-orange">
          IRON & OAK
        </p>

        <h1 className="industrial-title text-6xl">
          HARDWARE CATALOG
        </h1>

        <p className="mt-3 max-w-2xl text-cream/60">
          Professional tools, materials and equipment
          built for serious work.
        </p>
      </div>

      <ProductGrid />
    </div>
  );
}
