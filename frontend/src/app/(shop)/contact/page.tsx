import type { Metadata } from "next";
import Link from "next/link";
import {
  Bot,
  Clock,
  Factory,
  Mail,
  PackageSearch,
  Phone,
  ShieldAlert,
  Wrench,
} from "lucide-react";

import ContactForm from "@/components/ContactForm";

export const metadata: Metadata = {
  title: "Contact us | Iron & Oak",
  description: "Reach Iron & Oak Customer Service, Technical Services or Piper, our AI hardware assistant.",
};

/** Customer Service contact (also given out by Piper and its escalation tool). */
const SUPPORT_EMAIL = "contact@tensorgeek.com";
/** Bogotá landline (601 area code); TEL is the international form for tel: links. */
const SUPPORT_PHONE = "(601) 656 8364";
const SUPPORT_PHONE_TEL = "+576016568364";

/** Company Book, section 27.2 (local time in Bogotá). */
const HOURS = [
  { team: "Piper, AI hardware assistant", when: "24 hours a day, 7 days a week" },
  { team: "Customer Service", when: "Monday to Friday 8:00–18:00, Saturday 9:00–13:00" },
  { team: "Technical Services", when: "Monday to Friday 8:00–17:00" },
];

const CHANNELS = [
  { icon: Bot, title: "Ask Piper",
    text: "Products, stock, prices, shipping, taxes and services, answered at once. Piper can also "
      + "identify a tool from a photo and open a case with our team for you.",
    action: { label: "Open the chat on any page", href: null } },
  { icon: PackageSearch, title: "Track an order",
    text: "Your order number (IO-…) shows the status, payment and shipment, with the expected delivery date.",
    action: { label: "Find my order", href: "/orders" } },
  { icon: Wrench, title: "Technical services",
    text: "Maintenance, repair, installation, calibration and training, on site in Bogotá and Medellín.",
    action: { label: "See our services", href: "/services" } },
  { icon: Mail, title: "Email",
    text: "For anything else, write to Customer Service or use the form below.",
    action: { label: SUPPORT_EMAIL, href: `mailto:${SUPPORT_EMAIL}` } },
  { icon: Phone, title: "Phone",
    text: "Call Customer Service in office hours. From outside Colombia dial +57 601 656 8364.",
    action: { label: SUPPORT_PHONE, href: `tel:${SUPPORT_PHONE_TEL}` } },
];

/**
 * The brands in our catalog and what each one supplies. These are Iron & Oak's (fictional)
 * supplier brands; the list names no real manufacturer and implies no authorization
 * (Company Book, section 31).
 */
const BRANDS = [
  { name: "IronClad mecanics Co", supplies: "Power tools, hand tools and workshop equipment",
    note: "Our main supplier: compressors, grinders, drills, chainsaws, welders, generators and the core hand-tool range." },
  { name: "Vanguard Safety & Gear", supplies: "Safety equipment, outdoor and garden",
    note: "Helmets, respirators, gloves, hearing and eye protection, first aid, fire extinguishers, garden tools and flame weeders." },
  { name: "TitanForge Materials", supplies: "Building materials, paint and finishes",
    note: "Trowels, floats, putty knives, brushes, rollers, spray guns and caulking guns." },
  { name: "ApacheEletronics", supplies: "Electrical",
    note: "Multimeters, voltage testers, soldering irons, extension cords, sockets, flashlights and bulbs." },
  { name: "ApexFix Fasteners", supplies: "Fasteners, hardware and machine parts",
    note: "Nails, nuts, washers, springs, bearings and industrial chain." },
  { name: "Crazy-Gear", supplies: "Measuring instruments",
    note: "Digital calipers, infrared thermometers, moisture meters, stud finders and measuring wheels." },
  { name: "HydroVolt Systems", supplies: "Plumbing",
    note: "Ball valves, tubing cutters, plungers and utility torches." },
];

function SectionLabel({ children }: { children: React.ReactNode }) {
  return <p className="text-xs font-black tracking-[0.3em] text-orange">{children}</p>;
}

export default function ContactPage() {
  return (
    <div className="bg-[#17110a] text-cream">

      <section className="border-b border-orange/20 bg-[#241b0e]">
        <div className="mx-auto max-w-6xl px-6 py-16 lg:px-8">
          <SectionLabel>CONTACT US</SectionLabel>
          <h1 className="industrial-title mt-3 text-5xl sm:text-6xl">WE&apos;RE HERE TO HELP</h1>
          <p className="mt-6 max-w-3xl text-lg leading-8 text-cream/80">
            Questions about a product, an order, a warranty or a machine? Pick the fastest way below.
            Every email and form message gets a first answer within <b className="text-cream">1 business day</b>.
          </p>
        </div>
      </section>

      {/* channels */}
      <section className="mx-auto grid max-w-6xl gap-5 px-6 py-14 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-5 lg:px-8">
        {CHANNELS.map(({ icon: Icon, title, text, action }) => (
          <div key={title} className="flex flex-col border border-orange/20 bg-iron p-6">
            <Icon className="h-8 w-8 text-orange" />
            <h2 className="mt-4 font-black tracking-wide">{title.toUpperCase()}</h2>
            <p className="mt-2 flex-1 text-sm leading-6 text-cream/70">{text}</p>
            {action.href ? (
              <Link href={action.href} className="mt-4 break-all text-sm font-black text-orange hover:text-orange-light">
                {action.label} →
              </Link>
            ) : (
              <p className="mt-4 text-sm font-black text-cream/50">{action.label}</p>
            )}
          </div>
        ))}
      </section>

      {/* form + hours */}
      <section className="mx-auto grid max-w-6xl gap-8 px-6 pb-16 lg:grid-cols-[1.6fr_1fr] lg:px-8">
        <div>
          <SectionLabel>SEND US A MESSAGE</SectionLabel>
          <h2 className="industrial-title mb-6 mt-3 text-4xl">OPEN A CASE</h2>
          <ContactForm />
        </div>

        <aside className="space-y-6">
          <div className="border border-orange/20 bg-iron p-6">
            <h2 className="flex items-center gap-2 font-black tracking-wide">
              <Clock className="h-5 w-5 text-orange" /> OPENING HOURS
            </h2>
            <dl className="mt-4 space-y-4 text-sm">
              {HOURS.map(({ team, when }) => (
                <div key={team}>
                  <dt className="font-bold text-cream">{team}</dt>
                  <dd className="text-cream/70">{when}</dd>
                </div>
              ))}
            </dl>
            <p className="mt-4 text-sm">
              <a href={`tel:${SUPPORT_PHONE_TEL}`} className="font-black text-orange hover:text-orange-light">
                {SUPPORT_PHONE}
              </a>
              <span className="text-cream/50"> · </span>
              <a href={`mailto:${SUPPORT_EMAIL}`} className="break-all font-black text-orange hover:text-orange-light">
                {SUPPORT_EMAIL}
              </a>
            </p>
            <p className="mt-3 text-xs text-cream/40">Local time, Bogotá (UTC−5).</p>
          </div>

          <div className="border border-orange/20 bg-iron p-6">
            <h2 className="flex items-center gap-2 font-black tracking-wide">
              <Factory className="h-5 w-5 text-orange" /> WHERE WE ARE
            </h2>
            <p className="mt-3 text-sm leading-6 text-cream/70">
              We work online from our distribution center in Bogotá, Colombia, and deliver to Colombia,
              Ecuador, Peru, Brazil, Argentina, Bolivia, Panama, Costa Rica, Mexico and the United States.
              We do not run a public showroom.
            </p>
          </div>

          <div className="border border-orange/50 bg-orange/10 p-6">
            <h2 className="flex items-center gap-2 font-black tracking-wide text-orange-light">
              <ShieldAlert className="h-5 w-5" /> MACHINE SAFETY
            </h2>
            <p className="mt-3 text-sm leading-6 text-cream/80">
              If a machine makes abnormal noise, overheats or a safety device fails, stop using it and tell us
              right away. Safety concerns go straight to a technical specialist.
            </p>
          </div>
        </aside>
      </section>

      {/* brand providers */}
      <section className="border-t border-orange/20 bg-[#241b0e]">
        <div className="mx-auto max-w-6xl px-6 py-16 lg:px-8">
          <SectionLabel>OUR BRAND PROVIDERS</SectionLabel>
          <h2 className="industrial-title mt-3 text-4xl">THE BRANDS WE CARRY</h2>
          <p className="mt-4 max-w-3xl text-cream/70">
            Every product in our catalog comes from qualified suppliers, reviewed every year for product quality,
            certifications, delivery performance and warranty support.
          </p>

          <ul className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {BRANDS.map((brand) => (
              <li key={brand.name} className="border border-orange/20 bg-[#17110a] p-6">
                <p className="industrial-title text-2xl">{brand.name.toUpperCase()}</p>
                <p className="mt-1 text-xs font-black tracking-wider text-orange">{brand.supplies.toUpperCase()}</p>
                <p className="mt-3 text-sm leading-6 text-cream/70">{brand.note}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>

      <p className="mx-auto max-w-6xl px-6 py-8 text-xs leading-5 text-cream/40 lg:px-8">
        Iron &amp; Oak Hardware Supply Co. and the brands above are fictional, created for a software engineering
        and AI demonstration project.
      </p>
    </div>
  );
}
