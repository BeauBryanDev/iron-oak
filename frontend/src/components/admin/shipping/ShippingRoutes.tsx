"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { CloudDownload, Pencil, Plus, RefreshCw, Search, X } from "lucide-react";

import { ConfirmDialog, ErrorNote, Panel, Skeleton, inputClass, outlineButton, primaryButton } from "@/components/admin/ui";
import { SHIPPING_SOURCE_LABEL, formatDateTime } from "@/lib/admin/labels";
import { adminListRoutes, adminRefreshRoutes, adminSetRoute } from "@/lib/api/adminShipping";
import { ApiError } from "@/lib/api/http";
import type { RouteRefreshResult, ShippingRouteResponse } from "@/lib/api/types";

/** Countries served by road (ShippingCalculator); US, MX, CR and PA ship by air at fixed prices. */
const ROAD_COUNTRIES: Record<string, string> = {
  CO: "Colombia",
  EC: "Ecuador",
  PE: "Peru",
  BR: "Brazil",
  AR: "Argentina",
  BO: "Bolivia",
};

const SOURCE_TONE: Record<string, string> = {
  GOOGLE: "border-sky-400/50 text-sky-300",
  MANUAL: "border-orange/60 text-orange",
};

const km = new Intl.NumberFormat("en-US", { maximumFractionDigits: 1 });

/** Add a city or override a stored distance; the saved row is always MANUAL. */
function RouteForm({ initial, onSaved, onCancel }: {
  /** null = new city */
  initial: ShippingRouteResponse | null;
  onSaved: (route: ShippingRouteResponse) => void;
  onCancel: () => void;
}) {
  const [country, setCountry] = useState(initial?.country ?? "CO");
  const [city, setCity] = useState(initial?.city ?? "");
  const [distance, setDistance] = useState(initial ? String(initial.distanceKm) : "");
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const distanceRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (initial) distanceRef.current?.select();
  }, [initial]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    const value = Number(distance);
    if (!Number.isFinite(value) || value < 0) {
      setError("Enter a road distance of 0 km or more.");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      onSaved(await adminSetRoute({ country, city: city.trim(), distanceKm: value }));
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "Could not save the route.");
      setSaving(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4 border border-orange/40 bg-[#17110a] p-4">
      <p className="text-sm font-black tracking-wider">{initial ? `CHANGE ${initial.city.toUpperCase()}` : "ADD A CITY"}</p>
      <div className="grid grid-cols-1 gap-3 sm:grid-cols-[1fr_2fr_1fr]">
        <label className="block">
          <span className="text-xs font-bold text-cream/60">COUNTRY</span>
          <select
            value={country}
            disabled={Boolean(initial)}
            onChange={(e) => setCountry(e.target.value)}
            className={`${inputClass} mt-1`}
          >
            {Object.entries(ROAD_COUNTRIES).map(([code, name]) => <option key={code} value={code}>{name}</option>)}
          </select>
        </label>
        <label className="block">
          <span className="text-xs font-bold text-cream/60">CITY</span>
          <input
            required
            maxLength={100}
            value={city}
            disabled={Boolean(initial)}
            onChange={(e) => setCity(e.target.value)}
            placeholder="e.g. Tunja"
            className={`${inputClass} mt-1`}
            autoFocus={!initial}
          />
        </label>
        <label className="block">
          <span className="text-xs font-bold text-cream/60">ROAD KM</span>
          <input
            ref={distanceRef}
            required
            inputMode="decimal"
            value={distance}
            onChange={(e) => setDistance(e.target.value.replace(",", "."))}
            placeholder="0.0"
            className={`${inputClass} mt-1 tabular-nums`}
          />
        </label>
      </div>
      <p className="text-xs text-cream/50">
        Road kilometres from the warehouse. A saved distance is marked as set by staff and Google never replaces it.
        {!initial && country === "CO" && " Adding a Colombian city opens it for delivery."}
      </p>
      {error && <ErrorNote message={error} />}
      <div className="flex gap-3">
        <button type="submit" disabled={saving || !city.trim() || distance === ""} className={primaryButton}>
          {saving ? "SAVING…" : "SAVE ROUTE"}
        </button>
        <button type="button" onClick={onCancel} disabled={saving} className="px-3 text-xs font-black text-cream/60 hover:text-cream">
          CANCEL
        </button>
      </div>
    </form>
  );
}

export default function ShippingRoutes() {
  const [routes, setRoutes] = useState<ShippingRouteResponse[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [country, setCountry] = useState("");
  const [search, setSearch] = useState("");
  /** undefined = no form, null = new city, a route = editing it */
  const [editing, setEditing] = useState<ShippingRouteResponse | null | undefined>(undefined);
  const [refreshing, setRefreshing] = useState(false);
  const [refreshResult, setRefreshResult] = useState<RouteRefreshResult | null>(null);
  const [saved, setSaved] = useState<string | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setRoutes(await adminListRoutes());
      setError(null);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : "Could not load the shipping routes.");
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const counts = useMemo(() => {
    const byCountry: Record<string, number> = {};
    for (const route of routes ?? []) byCountry[route.country] = (byCountry[route.country] ?? 0) + 1;
    return byCountry;
  }, [routes]);

  const visible = useMemo(() => {
    const needle = search.trim().toLowerCase();
    return (routes ?? []).filter((route) =>
      (!country || route.country === country) && (!needle || route.city.toLowerCase().includes(needle)));
  }, [routes, country, search]);

  function onSaved(route: ShippingRouteResponse) {
    setRoutes((current) => {
      const rest = (current ?? []).filter((r) => r.id !== route.id);
      return [...rest, route].sort((a, b) => a.country.localeCompare(b.country) || a.city.localeCompare(b.city));
    });
    setEditing(undefined);
    setSaved(`${route.city}, ${route.country}: ${km.format(route.distanceKm)} km saved.`);
  }

  async function refresh() {
    setRefreshResult(await adminRefreshRoutes());
    await load();
  }

  return (
    <div className="mx-auto max-w-6xl space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-black tracking-[0.25em] text-orange">SALES</p>
          <h1 className="industrial-title mt-1 text-4xl sm:text-5xl">SHIPPING ROUTES</h1>
        </div>
        <div className="flex flex-wrap gap-3">
          <button type="button" onClick={() => void load()} disabled={loading} aria-label="Reload" className={outlineButton}>
            <RefreshCw className={`h-4 w-4 ${loading ? "animate-spin" : ""}`} />
          </button>
          <button type="button" onClick={() => setRefreshing(true)} className={`flex items-center gap-2 ${outlineButton}`}>
            <CloudDownload className="h-4 w-4" /> FILL FROM GOOGLE
          </button>
          <button type="button" onClick={() => { setSaved(null); setEditing(null); }} className={`flex items-center gap-2 ${primaryButton}`}>
            <Plus className="h-4 w-4" /> ADD CITY
          </button>
        </div>
      </div>

      <p className="max-w-3xl text-sm text-cream/60">
        Road shipping is priced from these distances. A city missing here costs a Google lookup on its first real order
        (or a straight-line estimate); inside Colombia, only the listed cities are served. The United States, Mexico,
        Costa Rica and Panama ship by air at fixed prices and have no routes.
      </p>

      {refreshResult && (
        <div className="flex items-start justify-between gap-3 border border-emerald-400/40 bg-emerald-950/30 p-3 text-sm text-emerald-200">
          <p>
            Checked {refreshResult.known} known cities: {refreshResult.alreadyStored} already stored,{" "}
            {refreshResult.fetched} fetched, {refreshResult.notFound} not found. Google calls left today:{" "}
            {refreshResult.googleCallsLeft}.
          </p>
          <button type="button" aria-label="Dismiss" onClick={() => setRefreshResult(null)}><X className="h-4 w-4" /></button>
        </div>
      )}
      {saved && (
        <div className="flex items-start justify-between gap-3 border border-emerald-400/40 bg-emerald-950/30 p-3 text-sm text-emerald-200">
          <p>{saved}</p>
          <button type="button" aria-label="Dismiss" onClick={() => setSaved(null)}><X className="h-4 w-4" /></button>
        </div>
      )}
      {error && <ErrorNote message={error} />}

      {editing !== undefined && (
        <RouteForm key={editing?.id ?? "new"} initial={editing} onSaved={onSaved} onCancel={() => setEditing(undefined)} />
      )}

      {/* country tabs with counts */}
      <div className="flex flex-wrap gap-2" role="tablist" aria-label="Countries">
        {[["", "All"], ...Object.entries(ROAD_COUNTRIES)].map(([code, name]) => {
          const active = country === code;
          const count = code ? counts[code] ?? 0 : routes?.length ?? 0;
          return (
            <button
              key={code || "all"}
              type="button"
              role="tab"
              aria-selected={active}
              onClick={() => setCountry(code)}
              className={`border px-4 py-2 text-xs font-black tracking-wide ${
                active ? "border-orange bg-orange text-black" : "border-orange/30 text-cream/70 hover:border-orange hover:text-cream"
              }`}
            >
              {name.toUpperCase()} <span className={active ? "text-black/60" : "text-cream/40"}>{count}</span>
            </button>
          );
        })}
      </div>

      <Panel
        title="ROUTES"
        subtitle={routes ? `${visible.length} of ${routes.length} cities` : undefined}
        actions={
          <div className="relative w-64 max-w-full">
            <label className="sr-only" htmlFor="route-search">Search cities</label>
            <input
              id="route-search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search city"
              className={`${inputClass} pr-9`}
            />
            <Search className="pointer-events-none absolute right-3 top-2.5 h-4 w-4 text-cream/40" />
          </div>
        }
      >
        {!routes && loading ? (
          <div className="space-y-2">{Array.from({ length: 8 }, (_, i) => <Skeleton key={i} className="h-10" />)}</div>
        ) : visible.length === 0 ? (
          <p className="py-6 text-center text-sm text-cream/50">No routes match.</p>
        ) : (
          <div className="-mx-5 overflow-x-auto">
            <table className="w-full min-w-[640px] text-sm">
              <thead>
                <tr className="border-b border-orange/20 text-left text-xs tracking-wider text-cream/50">
                  <th className="px-5 py-2 font-bold">CITY</th>
                  <th className="px-3 py-2 font-bold">COUNTRY</th>
                  <th className="px-3 py-2 text-right font-bold">ROAD KM</th>
                  <th className="px-3 py-2 font-bold">SOURCE</th>
                  <th className="px-3 py-2 text-right font-bold">UPDATED</th>
                  <th className="px-5 py-2"><span className="sr-only">Actions</span></th>
                </tr>
              </thead>
              <tbody className="divide-y divide-orange/10">
                {visible.map((route) => (
                  <tr key={route.id} className="hover:bg-white/[0.03]">
                    <td className="px-5 py-2.5 font-bold">{route.city}</td>
                    <td className="px-3 py-2.5 text-cream/70">{ROAD_COUNTRIES[route.country] ?? route.country}</td>
                    <td className="px-3 py-2.5 text-right tabular-nums">{km.format(route.distanceKm)}</td>
                    <td className="px-3 py-2.5">
                      <span
                        title={SHIPPING_SOURCE_LABEL[route.source]}
                        className={`border px-2 py-0.5 text-[11px] font-bold ${SOURCE_TONE[route.source] ?? "border-cream/30 text-cream/60"}`}
                      >
                        {route.source === "MANUAL" ? "STAFF" : route.source}
                      </span>
                    </td>
                    <td className="whitespace-nowrap px-3 py-2.5 text-right text-xs text-cream/50">{formatDateTime(route.updatedAt)}</td>
                    <td className="px-5 py-2.5 text-right">
                      <button
                        type="button"
                        aria-label={`Change ${route.city}`}
                        onClick={() => { setSaved(null); setEditing(route); }}
                        className="text-cream/50 hover:text-orange"
                      >
                        <Pencil className="h-4 w-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Panel>

      {refreshing && (
        <ConfirmDialog
          title="FILL FROM GOOGLE"
          body={
            <>
              <p>
                Looks up the road distance of every known city that is not stored yet (Colombia plus the major cities
                of Ecuador, Peru, Brazil, Argentina and Bolivia).
              </p>
              <p className="mt-2 text-cream/60">
                Stored routes are not touched. Each lookup uses one of the day’s Google calls, which are shared with
                real orders.
              </p>
            </>
          }
          confirmLabel="FETCH MISSING"
          onConfirm={refresh}
          onClose={() => setRefreshing(false)}
        />
      )}
    </div>
  );
}
