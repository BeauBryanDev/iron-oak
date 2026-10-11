/** "Fasteners & Hardware" -> "fasteners-and-hardware". Used for /catalog/[slug] URLs. */
export function slugify(category: string): string {
  return category
    .toLowerCase()
    .replace(/&/g, "and")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-|-$/g, "");
}
