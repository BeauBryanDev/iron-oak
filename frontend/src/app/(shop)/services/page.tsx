import {
  Wrench,
  Gauge,
  AlertTriangle,
  Settings2,
  GraduationCap,
  ClipboardCheck,
} from "lucide-react";

import ShopNowButton from "@/components/ShopNowButton";

const services = [
  {
    icon: ClipboardCheck,
    name: "PREVENTIVE MAINTENANCE",
    description:
      "Scheduled inspection, lubrication and wear checks that catch a failing spindle before it takes the job with it.",
    pricing: "From $240 / visit",
  },
  {
    icon: AlertTriangle,
    name: "EMERGENCY REPAIR",
    description:
      "Breakdown callout with a qualified technician on site. Diagnosis, parts sourcing and repair in one visit where possible.",
    pricing: "From $180 / hour",
  },
  {
    icon: Wrench,
    name: "SPINDLE & TOOLING SERVICE",
    description:
      "Spindle rebuild, bearing replacement, tool holder inspection and runout correction to factory tolerance.",
    pricing: "Request a quote",
  },
  {
    icon: Gauge,
    name: "POWER CONSUMPTION AUDIT",
    description:
      "We log your machine under real cutting load and model its power draw, flagging the setups that quietly cost you money.",
    pricing: "From $320 / machine",
  },
  {
    icon: Settings2,
    name: "INSTALLATION & CALIBRATION",
    description:
      "Delivery, levelling, alignment and first-article verification so a new machine cuts true from day one.",
    pricing: "From $650",
  },
  {
    icon: GraduationCap,
    name: "OPERATOR TRAINING",
    description:
      "Hands-on sessions covering safe operation, workholding, feeds and speeds, and daily maintenance routine.",
    pricing: "From $400 / day",
  },
];

export default function ServicesPage() {
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

        <div className="mt-12 grid gap-5 md:grid-cols-2 xl:grid-cols-3">

          {services.map((service) => (
            <article
              key={service.name}
              className="flex flex-col border border-orange/30 bg-[#17110a] p-6 transition-colors hover:border-orange"
            >
              <service.icon className="h-10 w-10 text-orange" />

              <h2 className="mt-5 text-lg font-black tracking-wide text-cream">
                {service.name}
              </h2>

              <p className="mt-3 flex-1 text-sm leading-6 text-cream/70">
                {service.description}
              </p>

              <div className="mt-5 border-t border-orange/20 pt-4 text-sm font-black text-orange">
                {service.pricing}
              </div>
            </article>
          ))}

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
