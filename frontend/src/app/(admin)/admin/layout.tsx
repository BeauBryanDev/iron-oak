import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Staff area | Iron & Oak",
  robots: { index: false, follow: false },
};

/** The admin area has no storefront header, footer or Piper chat. */
export default function AdminLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <div className="min-h-screen bg-[#17110a] text-cream">{children}</div>;
}
