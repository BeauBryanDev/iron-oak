/**
 * Where the storefront's images live. In production NEXT_PUBLIC_ASSET_URL is the CloudFront
 * address in front of the private S3 bucket (the photos are not in git, so Vercel does not
 * have them); unset, e.g. on a laptop, the same paths are served from frontend/public/.
 * The bucket mirrors public/: productImages/*, iron_oak_hero.jpg, worker_image.jpg, ...
 */
const ASSET_BASE = (process.env.NEXT_PUBLIC_ASSET_URL ?? "").replace(/\/+$/, "");

/** "/productImages/x.webp" -> "https://<cloudfront>/productImages/x.webp" in production. */
export function assetUrl(path: string): string {
  return path.startsWith("/") ? `${ASSET_BASE}${path}` : path;
}
