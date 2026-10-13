"use client";

import { useCallback, useEffect, useState, type ComponentType } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  Bot,
  CalendarCheck,
  CircleDollarSign,
  ClipboardCheck,
  Globe,
  Hourglass,
  LifeBuoy,
  MessageSquareWarning,
  PackageCheck,
  RefreshCw,
  ShieldCheck,
  Truck,
} from "lucide-react";

import OrderStatusBadge from "@/components/admin/OrderStatusBadge";
import { adminListEndMills } from "@/lib/api/adminEndMills";
import { adminListProducts, getDashboardKpis } from "@/lib/api/adminDashboard";
import { adminListOrders } from "@/lib/api/adminOrders";
import { ApiError } from "@/lib/api/http";
import type { DashboardKPIResponse, OrderResponse } from "@/lib/api/types";
import { useAdminSession } from "@/lib/auth/useAdminSession";
import { LOW_STOCK_THRESHOLD, money } from "@/lib/format";

/** KPIs are re-read this often while the tab is visible. */
const AUTO_REFRESH_MS = 60_000;
const RECENT_ORDERS = 8;
const LOW_STOCK_ROWS = 8;

type Section<T> = { data: T; error: null } | { data: null; error: string };

interface LowStockRow {
  kind: "Product" | "End mill";
  id: number;
  sku: string;
  name: string;
  stock: number;
}

interface DashboardData {
  kpis: Section<DashboardKPIResponse>;
  orders: Section<OrderResponse[]>;
  lowStock: Section<LowStockRow[]>;
  loadedAt: Date;
}

function errorText(reason: unknown): string {
  return reason instanceof ApiError ? reason.message : "Could not load this section.";
}

function settle<T>(result: PromiseSettledResult<T>): Section<T> {
  return result.status === "fulfilled"
    ? { data: result.value, error: null }
    : { data: null, error: errorText(result.reason) };
}

async function loadDashboard(): Promise<DashboardData> {
  const [kpis, orders, products, endMills] = await Promise.allSettled([
    getDashboardKpis(),
    adminListOrders({ size: RECENT_ORDERS, sort: "createdAt,desc" }),
    adminListProducts({ active: true, maxStock: LOW_STOCK_THRESHOLD, size: LOW_STOCK_ROWS, sort: "stockQuantity,asc" }),
    adminListEndMills({ active: true, maxStock: LOW_STOCK_THRESHOLD, size: LOW_STOCK_ROWS, sort: "stockQuantity,asc" }),
  ]);

  let lowStock: Section<LowStockRow[]>;
  if (products.status === "rejected" && endMills.status === "rejected") {
    lowStock = { data: null, error: errorText(products.reason) };
  } else {
    const rows: LowStockRow[] = [
      ...(products.status === "fulfilled"
        ? products.value.content.map((p) => ({ kind: "Product" as const, id: p.id, sku: p.sku, name: p.name, stock: p.stockQuantity }))
        : []),
      ...(endMills.status === "fulfilled"
        ? endMills.value.content.map((e) => ({ kind: "End mill" as const, id: e.id, sku: e.sku, name: e.name, stock: e.stockQuantity }))
        : []),
    ];
    rows.sort((a, b) => a.stock - b.stock || a.name.localeCompare(b.name));
    lowStock = { data: rows.slice(0, LOW_STOCK_ROWS), error: null };
  }

  return {
    kpis: settle(kpis),
    orders: orders.status === "fulfilled"
      ? { data: orders.value.content, error: null }
      : { data: null, error: errorText(orders.reason) },
    lowStock,
    loadedAt: new Date(),
  };
}

const integer = new Intl.NumberFormat("en-US");
const time = new Intl.DateTimeFormat("en-US", { hour: "2-digit", minute: "2-digit" });
const dateTime = new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" });

function percent(part: number, whole: number): string {
  return whole > 0 ? `${Math.round((part / whole) * 100)}%` : "0%";
}

// building blocks

function Panel({ title, subtitle, children, className = "" }: {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`border border-orange/20 bg-iron p-5 ${className}`}>
      <h2 className="text-sm font-black tracking-[0.15em] text-cream">{title}</h2>
      {subtitle && <p className="mt-1 text-xs text-cream/50">{subtitle}</p>}
      <div className="mt-4">{children}</div>
    </section>
  );
}

function SectionError({ message }: { message: string }) {
  return (
    <p role="alert" className="flex items-start gap-2 border border-red-400/40 bg-red-950/30 p-3 text-sm text-red-200">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {message}
    </p>
  );
}

function Skeleton({ className }: { className: string }) {
  return <div className={`animate-pulse bg-white/5 ${className}`} />;
}

function KpiCard({ label, value, hint, icon: Icon, accent = false }: {
  label: string;
  value: string;
  hint: string;
  icon: ComponentType<{ className?: string }>;
  accent?: boolean;
}) {
  return (
    <div className={`border p-5 ${accent ? "border-orange/60 bg-orange/10" : "border-orange/20 bg-iron"}`}>
      <div className="flex items-start justify-between gap-3">
        <p className="text-xs font-black tracking-[0.15em] text-cream/60">{label}</p>
        <Icon className="h-5 w-5 shrink-0 text-orange" />
      </div>
      <p className="industrial-title mt-3 text-4xl text-cream">{value}</p>
      <p className="mt-1 text-xs text-cream/50">{hint}</p>
    </div>
  );
}

function QueueCard({ label, count, hint, icon: Icon }: {
  label: string;
  count: number;
  hint: string;
  icon: ComponentType<{ className?: string }>;
}) {
  const waiting = count > 0;
  return (
    <div className={`flex items-center gap-4 border p-4 ${waiting ? "border-orange/60 bg-orange/10" : "border-orange/20 bg-iron"}`}>
      <span className={`flex h-11 w-11 shrink-0 items-center justify-center ${waiting ? "bg-orange text-black" : "bg-white/5 text-cream/40"}`}>
        <Icon className="h-5 w-5" />
      </span>
      <div className="min-w-0">
        <p className="text-sm font-bold text-cream">{label}</p>
        <p className="truncate text-xs text-cream/50">{waiting ? hint : "All clear"}</p>
      </div>
      <p className={`industrial-title ml-auto text-3xl ${waiting ? "text-orange-light" : "text-cream/30"}`}>{integer.format(count)}</p>
    </div>
  );
}

// sections

const PIPELINE: { key: keyof DashboardKPIResponse; label: string; color: string }[] = [
  { key: "pendingPaymentOrders", label: "Awaiting payment", color: "bg-amber-400" },
  { key: "openOrders", label: "Paid, to fulfil", color: "bg-orange" },
  { key: "completedOrders", label: "Completed", color: "bg-emerald-400" },
  { key: "cancelledOrders", label: "Cancelled", color: "bg-red-400" },
  { key: "expiredOrders", label: "Expired", color: "bg-cream/30" },
];

function OrderPipeline({ kpis }: { kpis: DashboardKPIResponse }) {
  const counts = PIPELINE.map((stage) => ({ ...stage, count: kpis[stage.key] as number }));
  const total = counts.reduce((sum, stage) => sum + stage.count, 0);

  return (
    <>
      <div className="flex h-4 w-full overflow-hidden bg-white/5" role="img" aria-label="Orders by state">
        {total > 0 &&
          counts.map((stage) =>
            stage.count > 0 ? (
              <div key={stage.key} className={stage.color} style={{ width: `${(stage.count / total) * 100}%` }} title={`${stage.label}: ${stage.count}`} />
            ) : null,
          )}
      </div>
      <ul className="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-2">
        {counts.map((stage) => (
          <li key={stage.key} className="flex items-center gap-2 text-sm">
            <span className={`h-3 w-3 shrink-0 ${stage.color}`} />
            <span className="text-cream/70">{stage.label}</span>
            <span className="ml-auto font-bold tabular-nums">{integer.format(stage.count)}</span>
            <span className="w-10 text-right text-xs tabular-nums text-cream/40">{percent(stage.count, total)}</span>
          </li>
        ))}
      </ul>
    </>
  );
}

function ChannelSplit({ kpis }: { kpis: DashboardKPIResponse }) {
  const total = kpis.webCheckoutOrders + kpis.piperOrders;
  const rows = [
    { label: "Web checkout", count: kpis.webCheckoutOrders, icon: Globe, color: "bg-sky-400" },
    { label: "Piper (AI assistant)", count: kpis.piperOrders, icon: Bot, color: "bg-orange" },
  ];
  return (
    <ul className="space-y-4">
      {rows.map(({ label, count, icon: Icon, color }) => (
        <li key={label}>
          <div className="flex items-center gap-2 text-sm">
            <Icon className="h-4 w-4 text-cream/60" />
            <span className="text-cream/80">{label}</span>
            <span className="ml-auto font-bold tabular-nums">{integer.format(count)}</span>
            <span className="w-10 text-right text-xs tabular-nums text-cream/40">{percent(count, total)}</span>
          </div>
          <div className="mt-1.5 h-2 bg-white/5">
            <div className={`h-full ${color}`} style={{ width: total > 0 ? `${(count / total) * 100}%` : "0%" }} />
          </div>
        </li>
      ))}
    </ul>
  );
}

function TopProducts({ kpis }: { kpis: DashboardKPIResponse }) {
  if (kpis.topProducts.length === 0) {
    return <p className="text-sm text-cream/50">No products sold yet.</p>;
  }
  const best = Math.max(...kpis.topProducts.map((p) => p.unitsSold), 1);
  return (
    <ol className="space-y-3">
      {kpis.topProducts.map((product, index) => (
        <li key={product.productId}>
          <div className="flex items-baseline gap-3 text-sm">
            <span className="w-5 shrink-0 text-right font-black text-orange">{index + 1}</span>
            <span className="min-w-0 flex-1 truncate text-cream/90" title={product.name}>{product.name}</span>
            <span className="shrink-0 font-bold tabular-nums">{integer.format(product.unitsSold)}</span>
          </div>
          <div className="ml-8 mt-1 h-1.5 bg-white/5">
            <div className="h-full bg-orange" style={{ width: `${(product.unitsSold / best) * 100}%` }} />
          </div>
        </li>
      ))}
    </ol>
  );
}

function LowStock({ rows }: { rows: LowStockRow[] }) {
  if (rows.length === 0) {
    return <p className="text-sm text-cream/50">Everything on sale has more than {LOW_STOCK_THRESHOLD} units.</p>;
  }
  return (
    <ul className="divide-y divide-orange/10">
      {rows.map((row) => (
        <li key={`${row.kind}-${row.id}`} className="flex items-center gap-3 py-2 text-sm">
          <div className="min-w-0 flex-1">
            <p className="truncate text-cream/90" title={row.name}>{row.name}</p>
            <p className="text-xs text-cream/40">{row.kind} · {row.sku}</p>
          </div>
          <span className={`shrink-0 border px-2 py-0.5 text-xs font-bold tabular-nums ${
            row.stock === 0 ? "border-red-400/60 bg-red-500/10 text-red-300" : "border-amber-400/60 bg-amber-400/10 text-amber-300"
          }`}>
            {row.stock === 0 ? "OUT" : `${row.stock} left`}
          </span>
        </li>
      ))}
    </ul>
  );
}

const CHANNEL_LABEL: Record<OrderResponse["channel"], string> = {
  WEB_CHECKOUT: "Web",
  PIPER: "Piper",
  ADMIN_MANUAL: "Staff",
  AGENT_CHAT: "Web",
};

function RecentOrders({ orders }: { orders: OrderResponse[] }) {
  if (orders.length === 0) {
    return <p className="text-sm text-cream/50">No orders yet.</p>;
  }
  return (
    <div className="-mx-5 overflow-x-auto">
      <table className="w-full min-w-[640px] text-sm">
        <thead>
          <tr className="border-b border-orange/20 text-left text-xs tracking-wider text-cream/50">
            <th className="px-5 py-2 font-bold">ORDER</th>
            <th className="px-3 py-2 font-bold">CUSTOMER</th>
            <th className="px-3 py-2 font-bold">CHANNEL</th>
            <th className="px-3 py-2 font-bold">STATUS</th>
            <th className="px-3 py-2 text-right font-bold">TOTAL</th>
            <th className="px-5 py-2 text-right font-bold">PLACED</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-orange/10">
          {orders.map((order) => (
            <tr key={order.id} className="hover:bg-white/[0.03]">
              <td className="px-5 py-2.5 font-mono text-xs"><Link href={`/admin/orders/${order.id}`} className="text-orange-light hover:underline">{order.orderNumber}</Link></td>
              <td className="max-w-[14rem] px-3 py-2.5">
                <p className="truncate">{order.customerName ?? "—"}</p>
                <p className="truncate text-xs text-cream/40">{order.city ? `${order.city}, ${order.country}` : order.country ?? ""}</p>
              </td>
              <td className="px-3 py-2.5 text-cream/70">{CHANNEL_LABEL[order.channel]}</td>
              <td className="px-3 py-2.5"><OrderStatusBadge status={order.status} /></td>
              <td className="px-3 py-2.5 text-right font-bold tabular-nums">{money(order.grandTotal)}</td>
              <td className="whitespace-nowrap px-5 py-2.5 text-right text-cream/60">{dateTime.format(new Date(order.createdAt))}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// page

export default function Dashboard() {
  const session = useAdminSession();
  const [data, setData] = useState<DashboardData | null>(null);
  const [refreshing, setRefreshing] = useState(false);

  const refresh = useCallback(async () => {
    setRefreshing(true);
    try {
      setData(await loadDashboard());
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible") void refresh();
    }, AUTO_REFRESH_MS);
    return () => window.clearInterval(timer);
  }, [refresh]);

  const kpis = data?.kpis.data ?? null;
  const name = (session.fullName ?? session.username ?? "").split(" ")[0];

  return (
    <div className="mx-auto max-w-7xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <p className="text-xs font-black tracking-[0.25em] text-orange">DASHBOARD</p>
          <h1 className="industrial-title mt-1 text-4xl sm:text-5xl">WELCOME BACK{name ? `, ${name.toUpperCase()}` : ""}</h1>
        </div>
        <div className="flex items-center gap-3">
          {data && <span className="text-xs text-cream/40">Updated {time.format(data.loadedAt)}</span>}
          <button
            type="button"
            onClick={() => void refresh()}
            disabled={refreshing}
            className="flex items-center gap-2 border border-orange px-4 py-2 text-xs font-black text-orange hover:bg-orange hover:text-black disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 ${refreshing ? "animate-spin" : ""}`} /> REFRESH
          </button>
        </div>
      </div>

      {data?.kpis.error && <SectionError message={data.kpis.error} />}

      {/* headline numbers */}
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {kpis ? (
          <>
            <KpiCard accent label="COMPLETED REVENUE" value={money(kpis.completedRevenue)} hint="Completed orders, shipping and taxes included" icon={CircleDollarSign} />
            <KpiCard label="TO FULFIL" value={integer.format(kpis.openOrders)} hint="Paid orders being prepared or shipped" icon={Truck} />
            <KpiCard label="AWAITING PAYMENT" value={integer.format(kpis.pendingPaymentOrders)} hint="Holding stock until paid or expired" icon={Hourglass} />
            <KpiCard label="COMPLETED ORDERS" value={integer.format(kpis.completedOrders)} hint="Delivered or finished" icon={PackageCheck} />
          </>
        ) : (
          !data?.kpis.error && Array.from({ length: 4 }, (_, i) => <Skeleton key={i} className="h-[134px]" />)
        )}
      </div>

      {/* work queues */}
      {kpis && (
        <div>
          <h2 className="mb-3 text-sm font-black tracking-[0.15em] text-cream">NEEDS ATTENTION</h2>
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <QueueCard label="Bookings to confirm" count={kpis.requestedBookings} hint="Check a technician is free, then confirm" icon={CalendarCheck} />
            <QueueCard label="Warranty claims" count={kpis.openWarrantyClaims} hint="Open or in review" icon={ShieldCheck} />
            <QueueCard label="Complaints" count={kpis.pendingComplaints} hint="Waiting for a decision" icon={MessageSquareWarning} />
            <QueueCard label="Support tickets" count={kpis.openSupportTickets} hint="Open or in progress" icon={LifeBuoy} />
          </div>
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <Panel title="ORDER PIPELINE" subtitle="Every order by state" className="lg:col-span-2">
          {kpis ? <OrderPipeline kpis={kpis} /> : <Skeleton className="h-28" />}
        </Panel>
        <Panel title="SALES CHANNELS" subtitle="Orders placed on the website vs. by Piper">
          {kpis ? <ChannelSplit kpis={kpis} /> : <Skeleton className="h-28" />}
        </Panel>
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <Panel title="TOP PRODUCTS" subtitle="Units sold on completed orders">
          {kpis ? <TopProducts kpis={kpis} /> : <Skeleton className="h-48" />}
        </Panel>
        <Panel title="LOW STOCK" subtitle={`Products and end mills on sale with ${LOW_STOCK_THRESHOLD} units or fewer`}>
          {data?.lowStock.error ? (
            <SectionError message={data.lowStock.error} />
          ) : data?.lowStock.data ? (
            <LowStock rows={data.lowStock.data} />
          ) : (
            <Skeleton className="h-48" />
          )}
        </Panel>
      </div>

      <Panel title="RECENT ORDERS" subtitle={`The ${RECENT_ORDERS} newest orders`}>
        {data?.orders.error ? (
          <SectionError message={data.orders.error} />
        ) : data?.orders.data ? (
          <RecentOrders orders={data.orders.data} />
        ) : (
          <Skeleton className="h-64" />
        )}
      </Panel>

      <p className="flex items-center gap-2 pb-4 text-xs text-cream/30">
        <ClipboardCheck className="h-3.5 w-3.5" /> Figures refresh every minute while this tab is open.
      </p>
    </div>
  );
}
