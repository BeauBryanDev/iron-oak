import Header from "@/components/Header";
import Piper from "@/components/PiperChat";
import Footer from "@/components/Footer";

export default function ShopLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <>
      <Header />

      <main className="min-h-screen">{children}</main>

      <Piper />
      <Footer />
    </>
  );
}
