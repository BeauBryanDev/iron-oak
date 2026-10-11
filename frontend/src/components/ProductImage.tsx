"use client";

import { useState } from "react";

import { PLACEHOLDER_IMAGE, imageSrc } from "@/lib/format";

/**
 * Product photo with a placeholder. The backend sends imageUrl = null until real images
 * exist, and a URL that fails to load falls back to the placeholder too.
 */
export default function ProductImage({ src, alt, className = "" }: { src: string | null; alt: string; className?: string }) {
  const [failed, setFailed] = useState(false);

  return (
    <img
      src={failed ? PLACEHOLDER_IMAGE : imageSrc(src)}
      alt={alt}
      onError={() => setFailed(true)}
      className={className}
    />
  );
}
