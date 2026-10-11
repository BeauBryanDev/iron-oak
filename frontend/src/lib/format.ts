const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });

/** All backend prices are USD. */
export function money(amount: number): string {
  return usd.format(amount);
}

export const PLACEHOLDER_IMAGE = "/assets/no-image.svg";

/** Seeded products have no image yet; fall back to a placeholder. */
export function imageSrc(imageUrl: string | null | undefined): string {
  return imageUrl && imageUrl.trim() ? imageUrl : PLACEHOLDER_IMAGE;
}
