"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Plus, RefreshCw, Search, X } from "lucide-react";

import CustomerForm from "@/components/admin/customers/CustomerForm";
import { ErrorNote, Skeleton, inputClass, outlineButton, primaryButton } from "@/components/admin/ui";
import Pagination from "@/components/Pagination";
import { formatDateTime } from "@/lib/admin/labels";
import { adminCreateCustomer, adminListCustomers } from "@/lib/api/adminCustomers";
import { ApiError } from "@/lib/api/http";
import type { CustomerRequest, CustomerResponse, Paged } from "@/lib/api/types";

const PAGE_SIZE = 20;

/** Filters live in the URL, like the orders list. */
interface Filters {
  q: string;
  from: string;
  to: string;
  page: number; // zero-based
}

function readFilters(params: URLSearchParams): Filters {
  const page = Number.parseInt(params.get("page") ?? "1", 10);
  return {
    q: params.get("q") ?? "",
    from: params.get("from") ?? "",
    to: params.get("to") ?? "",
    page: Number.isFinite(page) && page > 1 ? page - 1 : 0,
  };
}

function toParams(filters: Filters): Record<string, string | undefined> {
  return {
    q: filters.q || undefined,
    from: filters.from || undefined,
    to: filters.to || undefined,
  };
}

export default function CustomersList() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const filters = useMemo(() => readFilters(new URLSearchParams(searchParams.toString())), [searchParams]);

  const [result, setResult] = useState<Paged<CustomerResponse> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [search, setSearch] = useState(filters.q);
  const [creating, setCreating] = useState(false);

  useEffect(() => setSearch(filters.q), [filters.q]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    adminListCustomers({
      q: filters.q || undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
      page: filters.page,
      size: PAGE_SIZE,
      sort: "createdAt,desc",
    })
      .then((page) => !cancelled && setResult(page))
      .catch((cause) => !cancelled && setError(cause instanceof ApiError ? cause.message : "Could not load customers."))
      .finally(() => !cancelled && setLoading(false));

    return () => {
      cancelled = true;
    };
  }, [filters, reloadKey]);

  function update(change: Partial<Filters>) {
    const next = { ...filters, ...change, page: 0 };
    const query = new URLSearchParams();
    for (const [key, value] of Object.entries(toParams(next))) if (value) query.set(key, value);
    const text = query.toString();
    router.replace(text ? `${pathname}?${text}` : pathname, { scroll: false });
  }

  function submitSearch(event: FormEvent) {
    event.preventDefault();
    update({ q: search.trim() });
  }

  async function create(body: CustomerRequest) {
    const created = await adminCreateCustomer(body);
    router.push(`/admin/customers/${created.id}`);
  }

  const hasFilters = Boolean(filters.q || filters.from || filters.to);

  return (
    <div className="mx-auto max-w-7xl space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-black tracking-[0.25em] text-orange">SALES</p>
          <h1 className="industrial-title mt-1 text-4xl sm:text-5xl">CUSTOMERS</h1>
        </div>
        <div className="flex gap-3">
          <button type="button" onClick={() => setReloadKey((k) => k + 1)} disabled={loading} className={`flex items-center gap-2 ${outlineButton}`}>
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} /> REFRESH
          </button>
          <button type="button" onClick={() => setCreating(true)} className={`flex items-center gap-2 ${primaryButton}`}>
            <Plus className="h-4 w-4" /> NEW CUSTOMER
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 gap-3 border border-orange/20 bg-iron p-4 md:grid-cols-2 xl:grid-cols-[2fr_1fr_1fr_auto]">
        <form onSubmit={submitSearch} className="relative">
          <label className="sr-only" htmlFor="customer-search">Search customers</label>
          <input
            id="customer-search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Name, email or phone"
            className={`${inputClass} pr-10`}
          />
          <button type="submit" aria-label="Search" className="absolute inset-y-0 right-0 px-3 text-cream/60 hover:text-orange">
            <Search className="h-4 w-4" />
          </button>
        </form>
        <label className="flex items-center gap-2 text-xs text-cream/50">
          FROM
          <input type="date" value={filters.from} max={filters.to || undefined} onChange={(e) => update({ from: e.target.value })} className={`${inputClass} [color-scheme:dark]`} />
        </label>
        <label className="flex items-center gap-2 text-xs text-cream/50">
          TO
          <input type="date" value={filters.to} min={filters.from || undefined} onChange={(e) => update({ to: e.target.value })} className={`${inputClass} [color-scheme:dark]`} />
        </label>
        <button
          type="button"
          disabled={!hasFilters}
          onClick={() => update({ q: "", from: "", to: "" })}
          className="flex items-center justify-center gap-1 px-3 py-2 text-xs font-black text-cream/60 hover:text-orange disabled:opacity-30"
        >
          <X className="h-4 w-4" /> CLEAR
        </button>
      </div>

      {error && <ErrorNote message={error} />}

      <section className="border border-orange/20 bg-iron">
        <div className="flex items-center justify-between border-b border-orange/20 px-5 py-3 text-xs text-cream/50">
          <span>{result ? `${result.page.totalElements.toLocaleString("en-US")} customers` : "Loading…"}</span>
          <span>Guests are created at checkout; there is no customer login.</span>
        </div>

        {!result && loading ? (
          <div className="space-y-2 p-5">{Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : result && result.content.length === 0 ? (
          <p className="p-8 text-center text-sm text-cream/50">No customers match these filters.</p>
        ) : result ? (
          <div className={`overflow-x-auto ${loading ? "opacity-60" : ""}`}>
            <table className="w-full min-w-[760px] text-sm">
              <thead>
                <tr className="border-b border-orange/20 text-left text-xs tracking-wider text-cream/50">
                  <th className="px-5 py-2 font-bold">NAME</th>
                  <th className="px-3 py-2 font-bold">EMAIL</th>
                  <th className="px-3 py-2 font-bold">PHONE</th>
                  <th className="px-3 py-2 font-bold">ADDRESS</th>
                  <th className="px-5 py-2 text-right font-bold">SINCE</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-orange/10">
                {result.content.map((customer) => (
                  <tr key={customer.id} className="group cursor-pointer hover:bg-white/[0.03]" onClick={() => router.push(`/admin/customers/${customer.id}`)}>
                    <td className="max-w-[16rem] px-5 py-3">
                      <Link href={`/admin/customers/${customer.id}`} onClick={(e) => e.stopPropagation()} className="truncate font-bold text-orange-light group-hover:underline">
                        {customer.name}
                      </Link>
                      <p className="text-xs text-cream/40">#{customer.id}</p>
                    </td>
                    <td className="max-w-[16rem] truncate px-3 py-3 text-cream/80">{customer.email ?? "—"}</td>
                    <td className="whitespace-nowrap px-3 py-3 text-cream/80">{customer.phone ?? "—"}</td>
                    <td className="max-w-[18rem] truncate px-3 py-3 text-xs text-cream/60">{customer.address ?? "—"}</td>
                    <td className="whitespace-nowrap px-5 py-3 text-right text-xs text-cream/60">{formatDateTime(customer.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>

      {result && (
        <Pagination basePath={pathname} page={result.page.number} totalPages={result.page.totalPages} params={toParams(filters)} />
      )}

      {creating && <CustomerForm customer={null} onSave={create} onClose={() => setCreating(false)} />}
    </div>
  );
}
