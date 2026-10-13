"use client";

import { useEffect } from "react";
import { usePathname, useSearchParams } from "next/navigation";

import { recordPageView } from "@/lib/navigation/history";

/** Counts storefront page views, so BackButton knows whether "back" stays in the shop. */
export default function NavigationTracker() {
  const pathname = usePathname();
  const searchParams = useSearchParams();

  useEffect(() => {
    recordPageView();
  }, [pathname, searchParams]);

  return null;
}
