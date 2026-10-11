"use client";

import { useCallback, useEffect, useRef, useState, type ReactNode } from "react";
import Link from "next/link";

// Inline arrows instead of lucide-react icons: importing ChevronLeft/ChevronRight here made Next 15.5's
// production build drop this component from the client manifest (/catalog then answered 500).
function Chevron({ direction }: { direction: "left" | "right" }) {
  return (
    <svg viewBox="0 0 24 24" className="h-6 w-6" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d={direction === "left" ? "M15 18l-6-6 6-6" : "M9 6l6 6-6 6"} />
    </svg>
  );
}

type Props = {
  title: string;
  /** "VIEW ALL (27) →" target */
  href?: string;
  linkLabel?: string;
  /** one slide per child, e.g. <ProductCard /> */
  children: ReactNode;
};

/** A titled row of cards that scrolls sideways: swipe, wheel, or the arrow buttons. */
export default function ProductCarousel({ title, href, linkLabel = "VIEW ALL →", children }: Props) {
  const scroller = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  const updateArrows = useCallback(() => {
    const el = scroller.current;
    if (!el) return;
    setCanScrollLeft(el.scrollLeft > 4);
    setCanScrollRight(el.scrollLeft + el.clientWidth < el.scrollWidth - 4);
  }, []);

  useEffect(() => {
    const el = scroller.current;
    if (!el) return;
    updateArrows();
    el.addEventListener("scroll", updateArrows, { passive: true });
    const observer = new ResizeObserver(updateArrows);
    observer.observe(el);
    return () => {
      el.removeEventListener("scroll", updateArrows);
      observer.disconnect();
    };
  }, [updateArrows]);

  function scrollByPage(direction: -1 | 1) {
    const el = scroller.current;
    if (!el) return;
    el.scrollBy({ left: direction * el.clientWidth * 0.9, behavior: "smooth" });
  }

  const arrowClass =
    "absolute top-1/2 z-10 -translate-y-1/2 border border-orange bg-[#17110a]/90 p-2 text-orange shadow-lg transition hover:bg-orange hover:text-black disabled:hidden";

  return (
    <section className="border border-orange/30 bg-[#241b0e] p-6">
      <div className="mb-5 flex items-end justify-between gap-4">
        <h2 className="industrial-title text-3xl">{title}</h2>

        {href && (
          <Link href={href} className="shrink-0 text-sm font-bold text-orange hover:text-orange-light">
            {linkLabel}
          </Link>
        )}
      </div>

      <div className="relative">
        <button
          type="button"
          aria-label={`Scroll ${title} left`}
          onClick={() => scrollByPage(-1)}
          disabled={!canScrollLeft}
          className={`${arrowClass} -left-3`}
        >
          <Chevron direction="left" />
        </button>

        <div
          ref={scroller}
          role="list"
          aria-label={title}
          className="flex snap-x snap-mandatory gap-5 overflow-x-auto scroll-smooth pb-2 [-ms-overflow-style:none] [scrollbar-width:none] [&::-webkit-scrollbar]:hidden"
        >
          {Array.isArray(children)
            ? children.map((child, index) => (
                <div key={index} role="listitem" className="w-72 shrink-0 snap-start">
                  {child}
                </div>
              ))
            : (
              <div role="listitem" className="w-72 shrink-0 snap-start">
                {children}
              </div>
            )}
        </div>

        <button
          type="button"
          aria-label={`Scroll ${title} right`}
          onClick={() => scrollByPage(1)}
          disabled={!canScrollRight}
          className={`${arrowClass} -right-3`}
        >
          <Chevron direction="right" />
        </button>
      </div>
    </section>
  );
}
