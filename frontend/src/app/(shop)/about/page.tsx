import type { Metadata } from "next";
import Link from "next/link";
import {
  BadgeCheck,
  Ban,
  Cog,
  Eye,
  Globe,
  Handshake,
  HardHat,
  MessageSquare,
  RefreshCw,
  ScrollText,
  ShieldCheck,
  Truck,
  UserCheck,
  Wrench,
} from "lucide-react";

export const metadata: Metadata = {
  title: "About us | Iron & Oak",
  description:
    "Iron & Oak Hardware Supply Co.: professional tools, industrial machinery and technical service, "
    + "with Piper, our AI hardware assistant.",
};

/** Company Book, section 3.1. */
const MISSION = [
  "Reliable tools made from quality materials.",
  "Professional and industrial equipment suited to real working conditions.",
  "Practical solutions, not the most expensive option.",
  "Honest communication about price, availability, delivery and warranty.",
  "Maintenance and technical support behind the equipment we sell.",
  "Long-term relationships built on trust and industrial reliability.",
];

/** Company Book, section 3.2. */
const VISION = [
  "Grow our technical services, led by milling machine maintenance.",
  "Support professionals with equipment, parts and service across the whole life of a machine.",
  "Supply reliable machinery and parts through responsible manufacturer relationships.",
  "Combine technology and people: technology to gather information fast, "
    + "qualified people to make the technical decisions.",
];

/** Company Book, section 3.3: the ten principles, in their own order. */
const PRINCIPLES = [
  { icon: HardHat, title: "Safety first",
    text: "Safety comes before sales, speed and convenience. A safety concern goes to a specialist at once." },
  { icon: MessageSquare, title: "Honest communication",
    text: "We state the facts on price, stock, delivery and warranty, even when the answer is unwelcome." },
  { icon: Ban, title: "No false promises",
    text: "No delivery date, repair outcome or refund is promised until an authorized person confirms it." },
  { icon: BadgeCheck, title: "Product reliability",
    text: "We sell what we can describe accurately and support after the sale." },
  { icon: Cog, title: "Technical responsibility",
    text: "Qualified people make technical decisions. AI advice is only advice." },
  { icon: Handshake, title: "Customer respect",
    text: "Clear, courteous, unhurried service, whatever the size of the order." },
  { icon: Eye, title: "Transparency",
    text: "Fees, conditions and limits are disclosed before you commit." },
  { icon: UserCheck, title: "Professionalism",
    text: "Clear, direct and businesslike, from our staff and from our AI." },
  { icon: ScrollText, title: "Accountability",
    text: "Every decision, promise and record can be traced to a person or a system." },
  { icon: RefreshCw, title: "Continuous improvement",
    text: "We review complaints, service results and AI behaviour, and revise our policies on schedule." },
];

const DIVISIONS = [
  { icon: Wrench, title: "Retail hardware",
    text: "Power tools, hand tools, workshop and safety equipment, building hardware, electrical "
      + "and paint supplies, plus solid carbide end mills for CNC work." },
  { icon: Cog, title: "Industrial machinery",
    text: "Milling machines, our machinery specialty, supplied through quotation and delivered to your site." },
  { icon: ShieldCheck, title: "Technical services",
    text: "Preventive maintenance, emergency repair, installation, calibration and operator training, "
      + "on site in Bogotá and Medellín." },
  { icon: Truck, title: "Delivery",
    text: "From our Bogotá warehouse to Colombia, Ecuador, Peru, Brazil, Argentina, Bolivia, Panama, "
      + "Costa Rica, Mexico and the United States." },
];

function SectionLabel({ children }: { children: React.ReactNode }) {
  return <p className="text-xs font-black tracking-[0.3em] text-orange">{children}</p>;
}

export default function AboutPage() {
  return (
    <div className="bg-[#17110a] text-cream">

      {/* who we are */}
      <section className="border-b border-orange/20 bg-[#241b0e]">
        <div className="mx-auto grid max-w-6xl gap-10 px-6 py-16 lg:grid-cols-[1.4fr_1fr] lg:items-center lg:px-8">
          <div>
            <SectionLabel>WHO WE ARE</SectionLabel>
            <h1 className="industrial-title mt-3 text-5xl sm:text-6xl">BUILT TO WORK</h1>

            <p className="mt-8 max-w-2xl text-lg leading-8 text-cream/80">
              Iron &amp; Oak Hardware Supply Co. is an industrial hardware supplier that works
              primarily online. We are a hardware and tools retailer, an industrial equipment
              supplier and a technical-service provider in one, with milling machines as our
              machinery specialty.
            </p>
            <p className="mt-5 max-w-2xl leading-7 text-cream/70">
              We serve tradespeople and professionals, contractors and construction companies,
              workshops and fabrication shops, manufacturers, maintenance companies and schools:
              the people who build, repair and maintain the things that matter.
            </p>

            <div className="mt-8 flex flex-wrap gap-3">
              <Link
                href="/catalog"
                className="bg-orange px-6 py-3 text-sm font-black text-black hover:bg-orange-light"
              >
                BROWSE THE CATALOG
              </Link>
              <Link
                href="/services"
                className="border border-orange px-6 py-3 text-sm font-black text-orange hover:bg-orange hover:text-black"
              >
                OUR SERVICES
              </Link>
            </div>
          </div>

          <div className="relative mx-auto w-full max-w-sm overflow-hidden border border-orange/30">
            <img
              src="/worker_image.jpg"
              alt="Carpenter in a yellow hard hat and green overalls hammering at a workbench in a tool-lined workshop"
              className="h-full w-full object-cover"
            />
          </div>
        </div>
      </section>

      {/* mission and vision */}
      <section className="mx-auto grid max-w-6xl gap-6 px-6 py-16 md:grid-cols-2 lg:px-8">
        <div className="border border-orange/20 bg-iron p-8">
          <SectionLabel>OUR MISSION</SectionLabel>
          <h2 className="industrial-title mt-3 text-3xl">RELIABLE TOOLS, HONEST SERVICE</h2>
          <p className="mt-4 text-cream/70">
            We supply reliable tools and professional equipment to the people who build, repair and
            maintain things, and we stand behind that equipment with honest service and technical support.
          </p>
          <ul className="mt-6 space-y-3">
            {MISSION.map((item) => (
              <li key={item} className="flex gap-3 text-sm text-cream/85">
                <span className="mt-2 h-1.5 w-1.5 shrink-0 bg-orange" />
                {item}
              </li>
            ))}
          </ul>
        </div>

        <div className="border border-orange/20 bg-iron p-8">
          <SectionLabel>OUR VISION</SectionLabel>
          <h2 className="industrial-title mt-3 text-3xl">A SUPPLIER YOU CAN TRUST</h2>
          <p className="mt-4 text-cream/70">
            We want to be a trusted industrial hardware supplier, recognized for dependable products,
            accurate information and technical competence.
          </p>
          <ul className="mt-6 space-y-3">
            {VISION.map((item) => (
              <li key={item} className="flex gap-3 text-sm text-cream/85">
                <span className="mt-2 h-1.5 w-1.5 shrink-0 bg-orange" />
                {item}
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* what we do */}
      <section className="border-y border-orange/20 bg-[#241b0e]">
        <div className="mx-auto max-w-6xl px-6 py-16 lg:px-8">
          <SectionLabel>WHAT WE DO</SectionLabel>
          <h2 className="industrial-title mt-3 text-4xl">FROM THE TOOLBOX TO THE MACHINE SHOP</h2>

          <div className="mt-10 grid gap-5 sm:grid-cols-2 lg:grid-cols-4">
            {DIVISIONS.map(({ icon: Icon, title, text }) => (
              <div key={title} className="border border-orange/20 bg-[#17110a] p-6">
                <Icon className="h-8 w-8 text-orange" />
                <h3 className="mt-4 font-black tracking-wide">{title.toUpperCase()}</h3>
                <p className="mt-2 text-sm leading-6 text-cream/70">{text}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* principles */}
      <section className="mx-auto max-w-6xl px-6 py-16 lg:px-8">
        <SectionLabel>HOW WE WORK</SectionLabel>
        <h2 className="industrial-title mt-3 text-4xl">TEN PRINCIPLES</h2>
        <p className="mt-4 max-w-3xl text-cream/70">
          When the rules do not cover a situation, these principles decide it, in this order of priority
          when they conflict: safety first, then honest communication, then technical responsibility,
          and only then convenience and sales.
        </p>

        <ol className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
          {PRINCIPLES.map(({ icon: Icon, title, text }, index) => (
            <li key={title} className="flex flex-col border border-orange/20 bg-iron p-5">
              <div className="flex items-center justify-between">
                <Icon className="h-6 w-6 text-orange" />
                <span className="industrial-title text-2xl text-cream/25">
                  {String(index + 1).padStart(2, "0")}
                </span>
              </div>
              <h3 className="mt-4 text-sm font-black tracking-wide">{title.toUpperCase()}</h3>
              <p className="mt-2 text-sm leading-6 text-cream/70">{text}</p>
            </li>
          ))}
        </ol>
      </section>

      {/* Piper */}
      <section className="border-t border-orange/20 bg-[#241b0e]">
        <div className="mx-auto grid max-w-6xl gap-10 px-6 py-16 lg:grid-cols-[auto_1fr] lg:items-center lg:px-8">
          <img src="/assets/piper_avatar.svg" alt="" className="mx-auto h-36 w-36 lg:h-44 lg:w-44" />
          <div>
            <SectionLabel>MEET PIPER</SectionLabel>
            <h2 className="industrial-title mt-3 text-4xl">YOUR AI HARDWARE ASSISTANT</h2>
            <p className="mt-5 max-w-3xl leading-7 text-cream/80">
              Piper answers questions about our products, stock, orders, shipping, taxes and services,
              using our own company documents and technical manuals, and tells you where an answer
              comes from. Send a photo and Piper recognizes the tool, or the end mill, and finds it in
              our catalog. Piper can also run a model-based health check on a CNC machine from its
              readings, and calculate cutting parameters for your material.
            </p>
            <p className="mt-4 max-w-3xl leading-7 text-cream/70">
              Piper gathers the information fast; our technicians make the technical calls. An AI
              estimate is advice, never a guarantee.
            </p>
            <div className="mt-6 flex flex-wrap gap-6 text-sm text-cream/60">
              <span className="flex items-center gap-2"><Globe className="h-4 w-4 text-orange" /> English and Spanish</span>
              <span className="flex items-center gap-2"><MessageSquare className="h-4 w-4 text-orange" /> Open the chat on any page</span>
            </div>
          </div>
        </div>
      </section>

      {/* Company Book 1.5 */}
      <p className="mx-auto max-w-6xl px-6 py-8 text-xs leading-5 text-cream/40 lg:px-8">
        Iron &amp; Oak Hardware Supply Co. is a fictional company created for a software engineering and
        AI demonstration project. Its policies, prices and organization are realistic but fictional.
      </p>
    </div>
  );
}
