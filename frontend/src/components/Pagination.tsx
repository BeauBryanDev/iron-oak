import Link from "next/link";

type Props = {
  basePath: string;
  page: number; // zero-based, as the backend counts
  totalPages: number;
  /** other query params to keep on every link, e.g. { q: "drill" } */
  params?: Record<string, string | undefined>;
};

function href(basePath: string, page: number, params: Props["params"]): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params ?? {})) {
    if (value) search.set(key, value);
  }
  if (page > 0) search.set("page", String(page + 1)); // URLs are one-based
  const query = search.toString();
  return query ? `${basePath}?${query}` : basePath;
}

export default function Pagination({ basePath, page, totalPages, params }: Props) {
  if (totalPages <= 1) return null;

  const linkClass = "border border-orange/40 px-4 py-2 text-sm font-black text-orange hover:bg-orange hover:text-black";
  const disabledClass = "border border-cream/10 px-4 py-2 text-sm font-black text-cream/30";

  return (
    <nav aria-label="Pagination" className="mt-8 flex items-center justify-center gap-4">
      {page > 0 ? (
        <Link href={href(basePath, page - 1, params)} className={linkClass}>
          ← PREVIOUS
        </Link>
      ) : (
        <span className={disabledClass}>← PREVIOUS</span>
      )}

      <span className="text-sm font-bold text-cream/70">
        PAGE {page + 1} OF {totalPages}
      </span>

      {page + 1 < totalPages ? (
        <Link href={href(basePath, page + 1, params)} className={linkClass}>
          NEXT →
        </Link>
      ) : (
        <span className={disabledClass}>NEXT →</span>
      )}
    </nav>
  );
}
