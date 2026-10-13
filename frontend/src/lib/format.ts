const usd = new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" });

/** All backend prices are USD. */
export function money(amount: number): string {
  return usd.format(amount);
}

const kg = new Intl.NumberFormat("en-US", { maximumFractionDigits: 3 });

/** "1.25 kg", or null when the weight has not been entered yet. */
export function formatWeight(weightKg: number | null | undefined): string | null {

  return weightKg === null || weightKg === undefined ? null : `${kg.format(weightKg)} kg`;
}

/** At or below this many units a card says "Only N left". */
export const LOW_STOCK_THRESHOLD = 5;

export const PLACEHOLDER_IMAGE = "/productImages/no-image.svg";

/** Milling machines have their own placeholder until real photos exist. */
export const MACHINE_PLACEHOLDER_IMAGE = "/assets/millingMachine.svg";

/**
 * A product's photo: its imageUrl when staff set one, otherwise the file named after its
 * vision class, /productImages/{visionName}.webp (one image per MobileNet class). A missing
 * file falls back to the placeholder in ProductImage, so no database change is needed.
 */
export function productImageUrl(product: { imageUrl: string | null; visionName: string }): string {

  return product.imageUrl && product.imageUrl.trim()
    ? product.imageUrl
    : `/productImages/${product.visionName}.webp`;
}

/**
 * An end mill's photo: its imageUrl when staff set one, otherwise /productImages/{SKU}.webp
 * (e.g. BN-002.webp). A missing file falls back to the placeholder in ProductImage.
 */
export function endMillImageUrl(endMill: { imageUrl: string | null; sku: string }): string {

  return endMill.imageUrl && endMill.imageUrl.trim()
    ? endMill.imageUrl
    : `/productImages/${endMill.sku}.webp`;
}

/** Items have no image until the backend's imageUrl is filled in; fall back to a placeholder. */
export function imageSrc(imageUrl: string | null | undefined,
  placeholder: string = PLACEHOLDER_IMAGE): string {

  return imageUrl && imageUrl.trim() ? imageUrl : placeholder;
}
