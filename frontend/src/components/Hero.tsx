import { ShieldCheck, Truck, Settings } from "lucide-react";

import ShopNowButton from "./ShopNowButton";

const trustBadges = [
  {
    icon: ShieldCheck,
    title: "TOP BRANDS",
    detail: "TRUSTED QUALITY",
  },
  {
    icon: Truck,
    title: "FAST SHIPPING",
    detail: "NATIONWIDE",
  },
  {
    icon: Settings,
    title: "EXPERT SUPPORT",
    detail: "ALWAYS HERE",
  },
];

export default function Hero() {
  return (
    <section className="relative min-h-[520px] overflow-hidden border border-orange/30 lg:aspect-[1376/785]">

      <img
        src="/iron_oak_hero.jpg"
        alt="Machinist working sparks off a milling machine in a workshop"
        className="absolute inset-0 h-full w-full object-cover brightness-110"
      />

      {/* Shade only the left edge, just enough to keep the headline readable. */}
      <div className="absolute inset-0 bg-gradient-to-r from-black/75 via-black/25 to-transparent" />

      {/* DEALS BADGE — sits bottom-right, covering the generator watermark. */}
      <div className="absolute bottom-4 right-4 hidden -rotate-6 bg-orange px-4 py-2 text-center text-black shadow-xl lg:block">

        <div className="text-[10px] font-black tracking-wide">
          TODAY'S DEALS
        </div>

        <div className="text-[9px] font-bold tracking-widest">
          UP TO
        </div>

        <div className="industrial-title text-2xl leading-none">
          40% OFF
        </div>

        <div className="text-[8px] font-bold tracking-[0.12em]">
          SELECT TOOLS &amp; EQUIPMENT
        </div>
      </div>

      {/* SCRIPT ACCENT */}
      <div className="script-accent absolute bottom-24 right-12 hidden text-right text-3xl leading-tight text-cream drop-shadow-lg xl:block">
        <div>Make</div>
        <div>Build</div>
        <div>Repair</div>
        <div>Repeat</div>
      </div>

      <div className="relative flex h-full min-h-[520px] items-center px-10">

        <div className="max-w-2xl">

          {/* EYEBROW */}
          <div className="text-lg font-black tracking-[0.2em] text-cream">
            IRON &amp; OAK
          </div>

          <div className="mt-2 h-1 w-16 bg-orange" />

          <h1 className="industrial-title mt-6 text-4xl leading-[0.95] md:text-5xl xl:text-6xl">

            <span className="block text-cream">
              BUILT FOR THE WORK,
            </span>

            <span className="block text-orange">
              READY FOR THE JOB.
            </span>

          </h1>

          <p className="mt-7 text-xl font-semibold leading-8 text-cream">
            Professional tools. Reliable materials.
            <br />
            Real Solutions.
          </p>

          <ShopNowButton
            size="lg"
            className="mt-9"
          />

          {/* TRUST ROW */}
          <div className="mt-12 flex flex-wrap gap-x-10 gap-y-5">

            {trustBadges.map((badge) => (
              <div
                key={badge.title}
                className="flex items-center gap-3"
              >
                <badge.icon className="h-8 w-8 shrink-0 text-orange" />

                <div>
                  <div className="text-sm font-black tracking-wide text-cream">
                    {badge.title}
                  </div>

                  <div className="text-xs font-bold tracking-wide text-cream/60">
                    {badge.detail}
                  </div>
                </div>
              </div>
            ))}

          </div>
        </div>
      </div>
    </section>
  );
}
