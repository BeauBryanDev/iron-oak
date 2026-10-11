import Link from "next/link";

import { slugify } from "@/lib/slug";

const categories = [
  {
    name: "POWER TOOLS",
    image: "/productImages/category-power-tools.jpg",
  },
  {
    name: "HAND TOOLS",
    image: "/productImages/category-hand-tools.jpg",
  },
  {
    name: "BUILDING MATERIALS",
    image: "/productImages/category-materials.jpg",
  },
  {
    name: "SAFETY EQUIPMENT",
    image: "/productImages/category-safety.jpg",
  },
];

export default function CategoryGrid() {
  return (
    <section className="py-4">

      <div className="grid grid-cols-2 gap-4 xl:grid-cols-4">

        {categories.map((category) => (
          <Link
            key={category.name}
            href={`/catalog/${slugify(category.name)}`}
            className="group relative h-48 overflow-hidden border border-orange/30"
          >

            <img
              src={category.image}
              alt={category.name}
              className="absolute inset-0 h-full w-full object-cover"
            />

            <div className="absolute inset-0 bg-black/55" />

            <div className="relative flex h-full flex-col justify-end p-5">

              <h3 className="industrial-title text-3xl">
                {category.name}
              </h3>

              <span className="mt-3 w-fit bg-orange px-4 py-2 text-sm font-black text-black transition-colors group-hover:bg-orange-light">
                SHOP NOW →
              </span>

            </div>

          </Link>
        ))}

      </div>

    </section>
  );
}


