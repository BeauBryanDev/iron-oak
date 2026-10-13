"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import Link from "next/link";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import { Bot, RefreshCw, Search, Truck, X } from "lucide-react";

import OrderStatusBadge from "@/components/admin/OrderStatusBadge";
import { ErrorNote, Skeleton, inputClass, outlineButton } from "@/components/admin/ui";
import Pagination from "@/components/Pagination";
import { CHANNEL_LABEL, ITEM_TYPE_LABEL, ORDER_PRESETS, SHIPMENT_LABEL, formatDateTime } from "@/lib/admin/labels";
import { adminListOrders } from "@/lib/api/adminOrders";
import { ApiError } from "@/lib/api/http";
import type { OrderChannel, OrderItemType, OrderResponse, OrderStatus, Paged } from "@/lib/api/types";
import { money } from "@/lib/format";

const PAGE_SIZE = 20;
const CHANNELS: OrderChannel[] = ["WEB_CHECKOUT", "PIPER", "ADMIN_MANUAL"];
const ITEM_TYPES: OrderItemType[] = ["PRODUCT", "END_MILL", "MACHINE", "SERVICE"];

/** Filters live in the URL so a filtered view survives reloads, back/forward and sharing. */
interface Filters {
  preset: string;
  channel: string;
  itemType: string;
  from: string;
  to: string;
  q: string;
  page: number; // zero-based
}

function readFilters(params: URLSearchParams): Filters {
  const page = Number.parseInt(params.get("page") ?? "1", 10);
  const preset = params.get("view") ?? "all";
  return {
    preset: ORDER_PRESETS.some((p) => p.key === preset) ? preset : "all",
    channel: params.get("channel") ?? "",
    itemType: params.get("itemType") ?? "",
    from: params.get("from") ?? "",
    to: params.get("to") ?? "",
    q: params.get("q") ?? "",
    page: Number.isFinite(page) && page > 1 ? page - 1 : 0,
  };
}

function toParams(filters: Filters): Record<string, string | undefined> {
  return {
    view: filters.preset !== "all" ? filters.preset : undefined,
    channel: filters.channel || undefined,
    itemType: filters.itemType || undefined,
    from: filters.from || undefined,
    to: filters.to || undefined,
    q: filters.q || undefined,
  };
}

function itemSummary(order: OrderResponse): string {
  const units = order.items.reduce((sum, item) => sum + item.quantity, 0);
  const types = [...new Set(order.items.map((item) => ITEM_TYPE_LABEL[item.itemType]))].join(", ");
  return `${units} ${units === 1 ? "unit" : "units"} · ${types}`;
}

export default function OrdersList() {
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const filters = useMemo(() => readFilters(new URLSearchParams(searchParams.toString())), [searchParams]);

  const [result, setResult] = useState<Paged<OrderResponse> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [search, setSearch] = useState(filters.q);

  useEffect(() => setSearch(filters.q), [filters.q]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    const status: OrderStatus[] = ORDER_PRESETS.find((p) => p.key === filters.preset)?.status ?? [];

    adminListOrders({
      status,
      channel: (filters.channel || undefined) as OrderChannel | undefined,
      itemType: (filters.itemType || undefined) as OrderItemType | undefined,
      from: filters.from || undefined,
      to: filters.to || undefined,
      q: filters.q || undefined,
      page: filters.page,
      size: PAGE_SIZE,
      sort: "createdAt,desc",
    })
      .then((page) => !cancelled && setResult(page))
      .catch((cause) => !cancelled && setError(cause instanceof ApiError ? cause.message : "Could not load orders."))
      .finally(() => !cancelled && setLoading(false));

    return () => {
      cancelled = true;
    };
  }, [filters, reloadKey]);

  /** Any filter change goes back to the first page. */
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

  const hasExtraFilters = Boolean(filters.channel || filters.itemType || filters.from || filters.to || filters.q);

  return (
    <div className="mx-auto max-w-7xl space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-black tracking-[0.25em] text-orange">SALES</p>
          <h1 className="industrial-title mt-1 text-4xl sm:text-5xl">ORDERS</h1>
        </div>
        <button type="button" onClick={() => setReloadKey((k) => k + 1)} disabled={loading} className={`flex items-center gap-2 ${outlineButton}`}>
          <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} /> REFRESH
        </button>
      </div>

      {/* quick views */}
      <div className="flex flex-wrap gap-2" role="tablist" aria-label="Order views">
        {ORDER_PRESETS.map((preset) => {
          const active = filters.preset === preset.key;
          return (
            <button
              key={preset.key}
              type="button"
              role="tab"
              aria-selected={active}
              onClick={() => update({ preset: preset.key })}
              className={`border px-4 py-2 text-xs font-black tracking-wide ${
                active ? "border-orange bg-orange text-black" : "border-orange/30 text-cream/70 hover:border-orange hover:text-cream"
              }`}
            >
              {preset.label.toUpperCase()}
            </button>
          );
        })}
      </div>

      {/* filters */}
      <div className="grid grid-cols-1 gap-3 border border-orange/20 bg-iron p-4 md:grid-cols-2 xl:grid-cols-[2fr_1fr_1fr_1fr_1fr_auto]">
        <form onSubmit={submitSearch} className="relative">
          <label className="sr-only" htmlFor="order-search">Search orders</label>
          <input
            id="order-search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Order number, customer name or email"
            className={`${inputClass} pr-10`}
          />
          <button type="submit" aria-label="Search" className="absolute inset-y-0 right-0 px-3 text-cream/60 hover:text-orange">
            <Search className="h-4 w-4" />
          </button>
        </form>
        <select aria-label="Channel" value={filters.channel} onChange={(e) => update({ channel: e.target.value })} className={inputClass}>
          <option value="">All channels</option>
          {CHANNELS.map((c) => <option key={c} value={c}>{CHANNEL_LABEL[c]}</option>)}
        </select>
        <select aria-label="Contains" value={filters.itemType} onChange={(e) => update({ itemType: e.target.value })} className={inputClass}>
          <option value="">Any items</option>
          {ITEM_TYPES.map((t) => <option key={t} value={t}>Has {ITEM_TYPE_LABEL[t].toLowerCase()}s</option>)}
        </select>
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
          disabled={!hasExtraFilters}
          onClick={() => update({ channel: "", itemType: "", from: "", to: "", q: "" })}
          className="flex items-center justify-center gap-1 px-3 py-2 text-xs font-black text-cream/60 hover:text-orange disabled:opacity-30"
        >
          <X className="h-4 w-4" /> CLEAR
        </button>
      </div>

      {error && <ErrorNote message={error} />}

      <section className="border border-orange/20 bg-iron">
        <div className="flex items-center justify-between border-b border-orange/20 px-5 py-3 text-xs text-cream/50">
          <span>{result ? `${result.page.totalElements.toLocaleString("en-US")} orders` : "Loading…"}</span>
          <span>Times are in your local time; date filters are UTC days.</span>
        </div>

        {!result && loading ? (
          <div className="space-y-2 p-5">{Array.from({ length: 6 }, (_, i) => <Skeleton key={i} className="h-12" />)}</div>
        ) : result && result.content.length === 0 ? (
          <p className="p-8 text-center text-sm text-cream/50">No orders match these filters.</p>
        ) : result ? (
          <div className={`overflow-x-auto ${loading ? "opacity-60" : ""}`}>
            <table className="w-full min-w-[900px] text-sm">
              <thead>
                <tr className="border-b border-orange/20 text-left text-xs tracking-wider text-cream/50">
                  <th className="px-5 py-2 font-bold">ORDER</th>
                  <th className="px-3 py-2 font-bold">CUSTOMER</th>
                  <th className="px-3 py-2 font-bold">ITEMS</th>
                  <th className="px-3 py-2 font-bold">STATUS</th>
                  <th className="px-3 py-2 font-bold">SHIPPING</th>
                  <th className="px-3 py-2 text-right font-bold">TOTAL</th>
                  <th className="px-5 py-2 text-right font-bold">PLACED</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-orange/10">
                {result.content.map((order) => (
                  <tr key={order.id} className="group cursor-pointer hover:bg-white/[0.03]" onClick={() => router.push(`/admin/orders/${order.id}`)}>
                    <td className="px-5 py-3">
                      <Link href={`/admin/orders/${order.id}`} onClick={(e) => e.stopPropagation()} className="font-mono text-xs text-orange-light group-hover:underline">
                        {order.orderNumber}
                      </Link>
                      <p className="mt-0.5 flex items-center gap-1 text-xs text-cream/40">
                        {order.channel === "PIPER" && <Bot className="h-3 w-3" />}
                        {CHANNEL_LABEL[order.channel]}
                      </p>
                    </td>
                    <td className="max-w-[16rem] px-3 py-3">
                      <p className="truncate">{order.customerName ?? "—"}</p>
                      <p className="truncate text-xs text-cream/40">{order.customerEmail ?? ""}</p>
                    </td>
                    <td className="px-3 py-3 text-xs text-cream/70">{itemSummary(order)}</td>
                    <td className="px-3 py-3"><OrderStatusBadge status={order.status} /></td>
                    <td className="px-3 py-3 text-xs">
                      {order.shippingStatus === "ON_REQUEST" ? (
                        <span className="border border-amber-400/60 bg-amber-400/10 px-2 py-0.5 font-bold text-amber-300">QUOTE NEEDED</span>
                      ) : order.shipment?.status ? (
                        <span className="flex items-center gap-1 text-cream/80"><Truck className="h-3.5 w-3.5 text-orange" />{SHIPMENT_LABEL[order.shipment.status]}</span>
                      ) : (
                        <span className="text-cream/40">{order.shippingMode === "NONE" ? "Nothing to ship" : order.city ?? "—"}</span>
                      )}
                    </td>
                    <td className="px-3 py-3 text-right font-bold tabular-nums">{money(order.grandTotal)}</td>
                    <td className="whitespace-nowrap px-5 py-3 text-right text-xs text-cream/60">{formatDateTime(order.createdAt)}</td>
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
    </div>
  );
}
