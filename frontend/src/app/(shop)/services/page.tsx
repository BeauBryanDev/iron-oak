import {
  Wrench,
  Gauge,
  AlertTriangle,
  Settings2,
  GraduationCap,
  ClipboardCheck,
  type LucideIcon,
} from "lucide-react";

import ApiUnavailable from "@/components/ApiUnavailable";
import ShopNowButton from "@/components/ShopNowButton";
import { listServices } from "@/lib/api/catalog";
import type { ServiceOfferingResponse } from "@/lib/api/types";
import { money } from "@/lib/format";

// Icons are presentation, so they live here keyed by the backend's service code.
const ICONS: Record<string, LucideIcon> = {
  PREVENTIVE_MAINTENANCE: ClipboardCheck,
  EMERGENCY_REPAIR: AlertTriangle,
  SPINDLE_TOOLING_SERVICE: Wrench,
  POWER_CONSUMPTION_AUDIT: Gauge,
  INSTALLATION_CALIBRATION: Settings2,
  OPERATOR_TRAINING: GraduationCap,
};

function pricingLabel(service: ServiceOfferingResponse): string {
  const unit = service.priceUnit ? ` / ${service.priceUnit}` : "";
  switch (service.pricingType) {
    case "FIXED":
      return service.fixedPrice !== null ? `From ${money(service.fixedPrice)}${unit}` : "Contact us";
    case "HOURLY":
      return service.hourlyRate !== null ? `From ${money(service.hourlyRate)}${unit || " / hour"}` : "Contact us";
    case "QUOTE":
      return "Request a quote";
  }
}

export default async function ServicesPage() {
  let services: ServiceOfferingResponse[] | null = null;
  let failure: unknown;
  try {
    services = await listServices();
  } catch (error) {
    failure = error;
  }

  return (
    <section className="min-h-screen bg-[#241b0e] px-8 py-16">
      <div className="mx-auto max-w-6xl">

        <p className="font-black tracking-widest text-orange">
          IRON &amp; OAK SERVICES
        </p>

        <h1 className="industrial-title mt-3 text-5xl md:text-6xl">
          MILLING MACHINE MAINTENANCE
        </h1>

        <p className="mt-6 max-w-3xl text-lg leading-8 text-cream/70">
          Keep your machines cutting. Our technicians service, repair and
          calibrate milling equipment for workshops, fabricators and
          production floors — scheduled or on call.
        </p>

        <div className="mt-12">
          {services ? (
            <div className="grid gap-5 md:grid-cols-2 xl:grid-cols-3">
              {services.map((service) => {
                const Icon = ICONS[service.code] ?? Wrench;
                return (
                  <article
                    key={service.id}
                    className="flex flex-col border border-orange/30 bg-[#17110a] p-6 transition-colors hover:border-orange"
                  >
                    <Icon className="h-10 w-10 text-orange" />

                    <h2 className="mt-5 text-lg font-black tracking-wide text-cream">
                      {service.name.toUpperCase()}
                    </h2>

                    <p className="mt-3 flex-1 text-sm leading-6 text-cream/70">
                      {service.description}
                    </p>

                    <div className="mt-5 border-t border-orange/20 pt-4 text-sm font-black text-orange">
                      {pricingLabel(service)}
                    </div>
                  </article>
                );
              })}
            </div>
          ) : (
            <ApiUnavailable error={failure} what="services" />
          )}
        </div>

        {/* CALLOUT */}
        <div className="mt-12 flex flex-col gap-6 border border-orange/30 bg-[#17110a] p-8 xl:flex-row xl:items-center xl:justify-between">

          <div>
            <h2 className="industrial-title text-3xl text-cream">
              NOT SURE WHAT YOU NEED?
            </h2>

            <p className="mt-3 max-w-2xl text-cream/70">
              Send Piper a photo of your machine or the part giving you
              trouble. It will identify the equipment and point you at the
              right service.
            </p>
          </div>

          <ShopNowButton
            href="/machines"
            label="BROWSE MACHINES"
            size="lg"
            className="shrink-0"
          />
        </div>

      </div>
    </section>
  );
}
