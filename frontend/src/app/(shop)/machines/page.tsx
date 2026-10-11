import ShopNowButton from "@/components/ShopNowButton";

const machines = [
  {
    name: "BENCHTOP MILL — BM-200",
    summary:
      "Compact vertical mill for prototyping, tool rooms and light production work.",
    power: "1.5 kW",
    spindle: "100 – 3,000 rpm",
    table: "700 × 180 mm",
    price: "$4,250",
  },
  {
    name: "TURRET MILL — TM-450",
    summary:
      "Workshop standard. Turret head and quill feed for versatile general machining.",
    power: "3.7 kW",
    spindle: "60 – 4,200 rpm",
    table: "1,270 × 254 mm",
    price: "$11,800",
  },
  {
    name: "CNC VERTICAL MACHINING CENTRE — VMC-650",
    summary:
      "Three-axis machining centre with automatic tool changer for repeat production runs.",
    power: "11 kW",
    spindle: "50 – 8,000 rpm",
    table: "800 × 400 mm",
    price: "$48,500",
  },
  {
    name: "HORIZONTAL BORING MILL — HB-900",
    summary:
      "Heavy frame boring mill for large workpieces and deep cuts in steel and cast iron.",
    power: "15 kW",
    spindle: "20 – 1,600 rpm",
    table: "1,000 × 900 mm",
    price: "$62,000",
  },
];

export default function MachinesPage() {
  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-16">
      <div className="mx-auto max-w-6xl">

        <p className="font-black tracking-widest text-orange">
          IRON &amp; OAK MACHINES
        </p>

        <h1 className="industrial-title mt-3 text-5xl md:text-6xl">
          MILLING MACHINES
        </h1>

        <p className="mt-6 max-w-3xl text-lg leading-8 text-cream/70">
          From benchtop mills to full machining centres. Every machine we
          sell is supported by our own technicians, with installation,
          calibration and scheduled maintenance available at point of sale.
        </p>

        <div className="mt-12 grid gap-5 md:grid-cols-2">

          {machines.map((machine) => (
            <article
              key={machine.name}
              className="flex flex-col border border-orange/30 bg-[#17110a] p-6 transition-colors hover:border-orange"
            >
              <h2 className="industrial-title text-2xl leading-tight text-cream">
                {machine.name}
              </h2>

              <p className="mt-3 text-sm leading-6 text-cream/70">
                {machine.summary}
              </p>

              <dl className="mt-5 grid grid-cols-3 gap-4 border-t border-orange/20 pt-4 text-sm">

                <div>
                  <dt className="text-xs font-bold tracking-wide text-cream/50">
                    POWER
                  </dt>
                  <dd className="mt-1 font-black text-cream">
                    {machine.power}
                  </dd>
                </div>

                <div>
                  <dt className="text-xs font-bold tracking-wide text-cream/50">
                    SPINDLE
                  </dt>
                  <dd className="mt-1 font-black text-cream">
                    {machine.spindle}
                  </dd>
                </div>

                <div>
                  <dt className="text-xs font-bold tracking-wide text-cream/50">
                    TABLE
                  </dt>
                  <dd className="mt-1 font-black text-cream">
                    {machine.table}
                  </dd>
                </div>

              </dl>

              <div className="mt-5 flex items-center justify-between">
                <span className="text-2xl font-black text-orange">
                  {machine.price}
                </span>

                <ShopNowButton
                  size="sm"
                  label="ENQUIRE"
                  href="/services"
                  variant="outline"
                />
              </div>
            </article>
          ))}

        </div>

      </div>
    </section>
  );
}
