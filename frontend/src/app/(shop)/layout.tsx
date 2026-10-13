import { Suspense } from "react";

import Header from "@/components/Header";
import NavigationTracker from "@/components/NavigationTracker";
import Piper from "@/components/PiperChat";
import Footer from "@/components/Footer";

export default function ShopLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <>
      <Suspense fallback={null}>
        <NavigationTracker />
      </Suspense>

      <Header />

      {/* piper-offset reserves the chat rail's width on wide screens, and only while it is open */}
      <div className="piper-offset">
        <main className="min-h-screen">{children}</main>

        <Footer />
      </div>

      <Piper />
    </>
  );
}
