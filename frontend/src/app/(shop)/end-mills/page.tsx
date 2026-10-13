import type { Metadata } from "next";

import ApiUnavailable from "@/components/ApiUnavailable";
import EndMillCard from "@/components/EndMillCard";
import { listEndMills } from "@/lib/api/catalog";
import type { EndMillResponse } from "@/lib/api/types";

export const metadata: Metadata = {
  title: "End mills | Iron & Oak",
  description: "Solid carbide fishtail and ball nose end mills for CNC milling, 1 to 3 mm.",
};

/** What each flute type is for (Iron Oak Business Rules, section 4.2). */
const FLUTE_NOTES: Record<string, string> = {
  Fishtail: "Flat end with a fishtail point: plunges straight down and leaves a flat, sharp-cornered floor. "
    + "For pockets, slots, engraving and lettering.",
  "Ball Nose": "Fully rounded tip, radius half the diameter: for 3D contours, molds, fillets and smooth "
    + "finishing passes.",
};

export default async function EndMillsPage() {
  let endMills: EndMillResponse[];

  try {
    endMills = (await listEndMills({ size: 100, sort: "cuttingDiameterMm,asc" })).content;
  } catch (error) {
    return (
      <div className="min-h-screen bg-[#241b0e] px-8 py-16">
        <ApiUnavailable error={error} what="end mills" />
      </div>
    );
  }

  const groups = new Map<string, EndMillResponse[]>();
  for (const endMill of endMills) {
    groups.set(endMill.fluteType, [...(groups.get(endMill.fluteType) ?? []), endMill]);
  }

  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-12 text-cream">
      <div className="mx-auto max-w-6xl">
        <div className="grid items-center gap-8 lg:grid-cols-[1fr_auto]">
          <div>
            <p className="text-xs font-black tracking-[0.3em] text-orange">CNC TOOLING</p>
            <h1 className="industrial-title mt-3 text-5xl sm:text-6xl">END MILLS</h1>
            <p className="mt-6 max-w-3xl text-lg leading-8 text-cream/75">
              Solid carbide milling cutters for CNC machines, right-hand cutting, in 1, 2 and 3 mm.
              Not sure which one you have? Send Piper a photo and it will recognize it.
            </p>
          </div>

          <img
            src="/productImages/end_mills_hero.webp"
            alt="End mill held in a CNC spindle collet above the workpiece"
            width={649}
            height={473}
            className="w-full max-w-md border border-orange/30 object-cover lg:w-[420px]"
          />
        </div>

        {endMills.length === 0 && (
          <p className="mt-10 text-cream/60">No end mills are on sale right now.</p>
        )}

        {[...groups.entries()].map(([fluteType, items]) => (
          <div key={fluteType} className="mt-12">
            <h2 className="industrial-title text-3xl">{fluteType.toUpperCase()}</h2>
            {FLUTE_NOTES[fluteType] && (
              <p className="mt-2 max-w-3xl text-sm text-cream/60">{FLUTE_NOTES[fluteType]}</p>
            )}
            <div className="mt-6 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {items.map((endMill) => (
                <EndMillCard key={endMill.id} endMill={endMill} />
              ))}
            </div>
          </div>
        ))}

        <p className="mt-12 text-xs text-cream/40">
          End mills are consumable cutting tools and carry no warranty.
        </p>
      </div>
    </section>
  );
}
