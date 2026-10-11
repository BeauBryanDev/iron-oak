import { ApiError } from "@/lib/api/http";

/** Shown when a server component cannot load its data, instead of crashing the whole page. */
export default function ApiUnavailable({ error, what = "this page" }: { error?: unknown; what?: string }) {
  const message = error instanceof ApiError ? error.message : "Something went wrong while loading data.";
  return (
    <div className="border border-orange/40 bg-[#17110a] p-6 text-cream">
      <p className="font-black tracking-wide text-orange">COULD NOT LOAD {what.toUpperCase()}</p>
      <p className="mt-2 text-sm text-cream/70">{message}</p>
    </div>
  );
}
