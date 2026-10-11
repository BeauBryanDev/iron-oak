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

/** Items have no image until the backend's imageUrl is filled in; fall back to a placeholder. */
export function imageSrc(imageUrl: string | null | undefined,
  placeholder: string = PLACEHOLDER_IMAGE): string {

  return imageUrl && imageUrl.trim() ? imageUrl : placeholder;
}
