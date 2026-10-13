import Link from "next/link";
import { notFound } from "next/navigation";

import AddToCartButton from "@/components/AddToCartButton";
import ApiUnavailable from "@/components/ApiUnavailable";
import BackButton from "@/components/BackButton";
import ProductImage from "@/components/ProductImage";
import ShopNowButton from "@/components/ShopNowButton";
import { getMachine } from "@/lib/api/catalog";
import { ApiError } from "@/lib/api/http";
import type { MillingMachineResponse } from "@/lib/api/types";
import { MACHINE_PLACEHOLDER_IMAGE, money } from "@/lib/format";

const number = new Intl.NumberFormat("en-US");

export default async function MachinePage({ params }: { params: Promise<{ modelCode: string }> }) {
  const { modelCode } = await params;

  let machine: MillingMachineResponse;
  try {
    machine = await getMachine(decodeURIComponent(modelCode));
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) notFound();
    return (
      <div className="min-h-screen bg-[#241b0e] px-8 py-16">
        <ApiUnavailable error={error} what="this machine" />
      </div>
    );
  }

  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-12">
      <div className="mx-auto max-w-6xl">
        <BackButton fallback="/machines" />

        <p className="text-sm font-bold tracking-wide text-cream/60">
          <Link href="/machines" className="hover:text-orange">
            MACHINES
          </Link>{" "}
          / {machine.modelCode}
        </p>

        <div className="mt-6 grid gap-10 md:grid-cols-2">
          <div className="flex h-96 items-center justify-center border border-orange/30 bg-white">
            <ProductImage
              src={machine.imageUrl}
              alt={`${machine.name} ${machine.modelCode}`}
              placeholder={MACHINE_PLACEHOLDER_IMAGE}
              className="h-full w-full object-contain p-8"
            />
          </div>

          <div>
            <p className="font-black tracking-widest text-orange">{machine.modelCode}</p>

            <h1 className="industrial-title mt-2 text-5xl leading-tight">{machine.name.toUpperCase()}</h1>

            <p className="mt-6 text-lg leading-8 text-cream/70">{machine.description}</p>

            <div className="mt-6 text-4xl font-black text-orange">{money(machine.price)}</div>

            <dl className="mt-6 grid grid-cols-2 gap-4 border-t border-orange/20 pt-5 text-sm">
              <div>
                <dt className="text-xs font-bold tracking-wide text-cream/50">POWER</dt>
                <dd className="mt-1 font-black">{machine.powerKw} kW</dd>
              </div>
              <div>
                <dt className="text-xs font-bold tracking-wide text-cream/50">SPINDLE SPEED</dt>
                <dd className="mt-1 font-black">
                  {number.format(machine.spindleMinRpm)} – {number.format(machine.spindleMaxRpm)} rpm
                </dd>
              </div>
              <div>
                <dt className="text-xs font-bold tracking-wide text-cream/50">TABLE</dt>
                <dd className="mt-1 font-black">
                  {number.format(machine.tableLengthMm)} × {number.format(machine.tableWidthMm)} mm
                </dd>
              </div>
              <div>
                <dt className="text-xs font-bold tracking-wide text-cream/50">WARRANTY</dt>
                <dd className="mt-1 font-black">
                  {machine.warrantyMonths > 0 ? `${machine.warrantyMonths} months` : "None"}
                </dd>
              </div>
            </dl>

            <p className="mt-6 text-sm text-cream/60">
              Freight is quoted by our team after you place the order, and installation, calibration and maintenance are
              available from our technicians.
            </p>

            <div className="mt-8 space-y-4">
              <AddToCartButton
                withQuantity
                label="ADD TO ORDER"
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

              <ShopNowButton size="sm" label="ENQUIRE ABOUT SERVICE" href="/services" variant="outline" />
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
