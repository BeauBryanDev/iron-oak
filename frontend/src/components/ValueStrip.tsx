import { Truck, RotateCcw, BadgePercent, Headset } from "lucide-react";

const values = [
  {
    icon: Truck,
    title: "FREE SHIPPING",
    detail: "ON ORDERS OVER $99",
  },
  {
    icon: RotateCcw,
    title: "30-DAY RETURNS",
    detail: "NO QUESTIONS ASKED",
  },
  {
    icon: BadgePercent,
    title: "PRO ACCOUNT PRICING",
    detail: "TRADE RATES DAILY",
  },
  {
    icon: Headset,
    title: "EXPERT SUPPORT",
    detail: "REAL PEOPLE, REAL ANSWERS",
  },
];

export default function ValueStrip() {
  return (
    <section className="mt-4 border border-orange/30 bg-[#17110a] p-6">

      <div className="flex flex-col gap-6 xl:flex-row xl:items-center">

        <div className="shrink-0 border-orange/30 xl:border-r xl:pr-8">
          <div className="industrial-title text-2xl leading-tight text-cream">
            BUILT TO WORK.
          </div>

          <div className="industrial-title text-2xl leading-tight text-orange">
            EVERY ORDER.
          </div>
        </div>

        <div className="grid flex-1 grid-cols-2 gap-6 xl:grid-cols-4">

          {values.map((value) => (
            <div
              key={value.title}
              className="flex items-center gap-3"
            >
              <value.icon className="h-8 w-8 shrink-0 text-orange" />

              <div className="min-w-0">
                <div className="text-sm font-black tracking-wide text-cream">
                  {value.title}
                </div>

                <div className="text-xs font-bold tracking-wide text-cream/60">
                  {value.detail}
                </div>
              </div>
            </div>
          ))}

        </div>
      </div>
    </section>
  );
}
