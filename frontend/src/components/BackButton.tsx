"use client";

import { useRouter } from "next/navigation";

import { hasInAppHistory } from "@/lib/navigation/history";

/**
 * "← BACK" for detail pages: returns to the previous page of this site (a catalog row, a
 * category page, a search), keeping its place. A page opened directly (shared link, new tab)
 * has no such previous page, so it goes to `fallback` instead of leaving the shop.
 */
export default function BackButton({ fallback, label = "BACK" }: { fallback: string; label?: string }) {
  const router = useRouter();

  function goBack() {
    if (hasInAppHistory()) {
      router.back();
    } else {
      router.push(fallback);
    }
  }

  return (
    <button
      type="button"
      onClick={goBack}
      className="mb-4 inline-flex items-center gap-2 border border-orange/40 px-4 py-2 text-xs font-black tracking-wide text-orange hover:bg-orange hover:text-black"
    >
      <span aria-hidden="true">←</span> {label}
    </button>
  );
}
