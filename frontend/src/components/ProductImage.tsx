"use client";

import { useEffect, useRef, useState } from "react";

import { PLACEHOLDER_IMAGE, imageSrc } from "@/lib/format";

/**
 * Product photo with a placeholder. The backend sends imageUrl = null until real images
 * exist, and a URL that fails to load falls back to the placeholder too.
 */
export default function ProductImage({
  src,
  alt,
  className = "",
  placeholder = PLACEHOLDER_IMAGE,
}: {
  src: string | null;
  alt: string;
  className?: string;
  /** shown for a null or broken src, e.g. MACHINE_PLACEHOLDER_IMAGE for milling machines */
  placeholder?: string;
}) {
  const [failed, setFailed] = useState(false);
  const image = useRef<HTMLImageElement>(null);

  // The page is rendered on the server, so the browser can finish (and fail) loading this image
  // before React attaches onError, and that error is never fired again. Check once we mount.
  useEffect(() => {
    const img = image.current;
    setFailed(Boolean(img && img.complete && img.naturalWidth === 0 && img.getAttribute("src") !== placeholder));
  }, [src, placeholder]);

  return (
    <img
      ref={image}
      src={failed ? placeholder : imageSrc(src, placeholder)}
      alt={alt}
      onError={() => setFailed(true)}
      className={className}
    />
  );
}
