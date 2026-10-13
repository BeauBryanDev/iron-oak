import Link from "next/link";

import AddToCartButton from "@/components/AddToCartButton";
import ApiUnavailable from "@/components/ApiUnavailable";
import ShopNowButton from "@/components/ShopNowButton";
import { listMachines } from "@/lib/api/catalog";
import type { MillingMachineResponse } from "@/lib/api/types";
import { money } from "@/lib/format";

const rpm = new Intl.NumberFormat("en-US");

function MachineCard({ machine }: { machine: MillingMachineResponse }) {
  return (
    <article className="flex flex-col border border-orange/30 bg-[#17110a] p-6 transition-colors hover:border-orange">
      <h2 className="industrial-title text-2xl leading-tight text-cream">
        {machine.name.toUpperCase()} — {machine.modelCode}
      </h2>

      <p className="mt-3 text-sm leading-6 text-cream/70">{machine.description}</p>

      <dl className="mt-5 grid grid-cols-3 gap-4 border-t border-orange/20 pt-4 text-sm">
        <div>
          <dt className="text-xs font-bold tracking-wide text-cream/50">POWER</dt>
          <dd className="mt-1 font-black text-cream">{machine.powerKw} kW</dd>
        </div>

        <div>
          <dt className="text-xs font-bold tracking-wide text-cream/50">SPINDLE</dt>
          <dd className="mt-1 font-black text-cream">
            {rpm.format(machine.spindleMinRpm)} – {rpm.format(machine.spindleMaxRpm)} rpm
          </dd>
        </div>

        <div>
          <dt className="text-xs font-bold tracking-wide text-cream/50">TABLE</dt>
          <dd className="mt-1 font-black text-cream">
            {rpm.format(machine.tableLengthMm)} × {rpm.format(machine.tableWidthMm)} mm
          </dd>
        </div>
      </dl>

      <div className="mt-5 flex flex-wrap items-center justify-between gap-3">
        <span className="text-2xl font-black text-orange">{money(machine.price)}</span>

        <div className="flex flex-wrap items-center gap-2">
          <Link
            href={`/machines/${encodeURIComponent(machine.modelCode)}`}
            className="border-2 border-cream/60 px-4 py-2 text-xs font-black tracking-wide text-cream transition-colors hover:border-orange hover:bg-orange hover:text-black"
          >
            DETAILS
          </Link>

          <ShopNowButton size="sm" label="ENQUIRE" href="/services" variant="outline" />

          <AddToCartButton
            label="ADD TO ORDER"
            className="!flex-none px-4 !py-2 text-xs"
            item={{
              itemType: "MACHINE",
              referenceId: machine.id,
              name: `${machine.name} — ${machine.modelCode}`,
              code: machine.modelCode,
              unitPrice: machine.price,
              imageUrl: machine.imageUrl,
              maxQuantity: null,
            }}
          />
        </div>
      </div>

      <p className="mt-3 text-xs text-cream/50">
        {machine.warrantyMonths}-month warranty. Freight is quoted by our team after you order.
      </p>
    </article>
  );
}

export default async function MachinesPage() {
  let machines: MillingMachineResponse[] | null = null;
  let failure: unknown;
  try {
    machines = await listMachines();
  } catch (error) {
    failure = error;
  }

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

        <div className="mt-12">
          {machines ? (
            <div className="grid gap-5 md:grid-cols-2">
              {machines.map((machine) => (
                <MachineCard key={machine.id} machine={machine} />
              ))}
            </div>
          ) : (
            <ApiUnavailable error={failure} what="machines" />
          )}
        </div>

      </div>
    </section>
  );
}
