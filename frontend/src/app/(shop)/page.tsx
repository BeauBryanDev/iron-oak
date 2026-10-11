import Hero from "@/components/Hero";
import CategorySidebar from "@/components/CategorySidebar";
import CategoryGrid from "@/components/CategoryGrid";
import FeaturedProducts from "@/components/FeaturedProducts";
import ValueStrip from "@/components/ValueStrip";

export default function Home() {
  return (
    <div className="bg-[#17110a] py-4 pr-4 lg:pr-6 xl:mr-[max(20%,320px)]">

      <div className="flex flex-col gap-4 lg:flex-row">

        <CategorySidebar />

        <div className="min-w-0 flex-1">
          <Hero />
          <CategoryGrid />
          <FeaturedProducts />
          <ValueStrip />
        </div>

      </div>
    </div>
  );
}
