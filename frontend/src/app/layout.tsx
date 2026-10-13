import type { Metadata } from "next";
import "./globals.css";

import { CartProvider } from "@/lib/cart/CartContext";
import { PIPER_INIT_SCRIPT } from "@/lib/piper/visibility";

export const metadata: Metadata = {
  title: "Iron & Oak Hardware Supply Co.",
  description: "Professional tools, materials and hardware.",
  icons: {
    icon: "/assets/metal_oak_icon.svg",
  },
};

/** Shell shared by the storefront and the admin area; each has its own layout inside a route group. */
export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    // data-piper is set by the inline script before React hydrates, hence suppressHydrationWarning.
    <html lang="en" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: PIPER_INIT_SCRIPT }} />
      </head>
      <body>
        <CartProvider>{children}</CartProvider>
      </body>
    </html>
  );
}
