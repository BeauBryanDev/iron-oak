"use client";

import { useEffect, useRef, useState, type ComponentType } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  Boxes,
  CalendarCheck,
  ChevronDown,
  ClipboardList,
  Cog,
  FileText,
  Gauge,
  Headset,
  KeyRound,
  LifeBuoy,
  LogOut,
  Menu,
  MessageSquareWarning,
  Package,
  Route,
  ScrollText,
  ShieldCheck,
  Store,
  Users,
  Wrench,
  X,
} from "lucide-react";

import { authed, logout } from "@/lib/auth/adminSession";
import { useAdminSession } from "@/lib/auth/useAdminSession";

interface NavItem {
  label: string;
  href: string;
  icon: ComponentType<{ className?: string }>;
  /** false until the screen exists; shown greyed so the panel's shape is visible */
  ready: boolean;
}

const NAV: { section: string; items: NavItem[] }[] = [
  {
    section: "OVERVIEW",
    items: [{ label: "Dashboard", href: "/admin", icon: Gauge, ready: true }],
  },
  {
    section: "SALES",
    items: [
      { label: "Orders", href: "/admin/orders", icon: ClipboardList, ready: true },
      { label: "Customers", href: "/admin/customers", icon: Users, ready: true },
      { label: "Shipping routes", href: "/admin/shipping", icon: Route, ready: true },
    ],
  },
  {
    section: "CATALOG",
    items: [
      { label: "Products", href: "/admin/products", icon: Package, ready: false },
      { label: "End mills", href: "/admin/end-mills", icon: Wrench, ready: false },
      { label: "Machines", href: "/admin/machines", icon: Cog, ready: false },
      { label: "Services", href: "/admin/services", icon: Boxes, ready: false },
    ],
  },
  {
    section: "WORK QUEUES",
    items: [
      { label: "Bookings", href: "/admin/bookings", icon: CalendarCheck, ready: false },
      { label: "Service quotes", href: "/admin/service-quotes", icon: FileText, ready: false },
      { label: "Warranty claims", href: "/admin/warranty-claims", icon: ShieldCheck, ready: false },
      { label: "Complaints", href: "/admin/complaints", icon: MessageSquareWarning, ready: false },
      { label: "Support tickets", href: "/admin/support-tickets", icon: LifeBuoy, ready: false },
    ],
  },
  {
    section: "ADMINISTRATION",
    items: [
      { label: "Staff", href: "/admin/staff", icon: Headset, ready: false },
      { label: "Audit log", href: "/admin/audit", icon: ScrollText, ready: false },
    ],
  },
];

function isActive(pathname: string, href: string): boolean {
  return href === "/admin" ? pathname === "/admin" : pathname === href || pathname.startsWith(`${href}/`);
}

function SideNav({ pathname, onNavigate }: { pathname: string; onNavigate: () => void }) {
  return (
    <nav aria-label="Staff navigation" className="space-y-6">
      {NAV.map((group) => (
        <div key={group.section}>
          <p className="mb-2 px-3 text-[11px] font-black tracking-[0.2em] text-cream/40">{group.section}</p>
          <ul className="space-y-0.5">
            {group.items.map(({ label, href, icon: Icon, ready }) => {
              const active = isActive(pathname, href);
              if (!ready) {
                return (
                  <li key={href}>
                    <span
                      aria-disabled="true"
                      title="Coming soon"
                      className="flex cursor-not-allowed items-center gap-3 px-3 py-2 text-sm text-cream/30"
                    >
                      <Icon className="h-4 w-4 shrink-0" />
                      <span className="flex-1">{label}</span>
                      <span className="text-[10px] font-bold tracking-wider">SOON</span>
                    </span>
                  </li>
                );
              }
              return (
                <li key={href}>
                  <Link
                    href={href}
                    onClick={onNavigate}
                    aria-current={active ? "page" : undefined}
                    className={`flex items-center gap-3 border-l-2 px-3 py-2 text-sm font-bold transition-colors ${
                      active
                        ? "border-orange bg-orange/15 text-orange"
                        : "border-transparent text-cream/80 hover:bg-white/5 hover:text-cream"
                    }`}
                  >
                    <Icon className="h-4 w-4 shrink-0" />
                    {label}
                  </Link>
                </li>
              );
            })}
          </ul>
        </div>
      ))}
    </nav>
  );
}

function AccountMenu({ username, fullName }: { username: string; fullName: string | null }) {
  const router = useRouter();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return;
    const close = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) setOpen(false);
    };
    const escape = (event: KeyboardEvent) => event.key === "Escape" && setOpen(false);
    document.addEventListener("mousedown", close);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("mousedown", close);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);

  async function signOut(everywhere: boolean) {
    setBusy(true);
    if (everywhere) {
      try {
        await authed<void>("/api/admin/auth/logout-all", { method: "POST" });
      } catch {
        // the local sign-out below still happens
      }
    }
    await logout();
    router.replace("/admin/login");
  }

  return (
    <div ref={ref} className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-haspopup="menu"
        className="flex items-center gap-2 border border-orange/30 px-3 py-2 text-sm hover:border-orange"
      >
        <span className="flex h-7 w-7 items-center justify-center bg-orange text-xs font-black text-black">
          {(fullName ?? username).slice(0, 1).toUpperCase()}
        </span>
        <span className="hidden max-w-[12rem] truncate font-bold sm:inline">{fullName ?? username}</span>
        <ChevronDown className="h-4 w-4 text-cream/60" />
      </button>

      {open && (
        <div role="menu" className="absolute right-0 z-30 mt-2 w-60 border border-orange/30 bg-iron shadow-xl">
          <div className="border-b border-orange/20 px-4 py-3">
            <p className="truncate text-sm font-bold">{fullName ?? username}</p>
            <p className="truncate text-xs text-cream/50">@{username}</p>
          </div>
          <Link
            role="menuitem"
            href="/admin/change-password"
            className="flex items-center gap-3 px-4 py-2.5 text-sm hover:bg-white/5"
          >
            <KeyRound className="h-4 w-4 text-orange" /> Change password
          </Link>
          <Link role="menuitem" href="/" className="flex items-center gap-3 px-4 py-2.5 text-sm hover:bg-white/5">
            <Store className="h-4 w-4 text-orange" /> View shop
          </Link>
          <button
            role="menuitem"
            type="button"
            disabled={busy}
            onClick={() => void signOut(false)}
            className="flex w-full items-center gap-3 border-t border-orange/20 px-4 py-2.5 text-left text-sm hover:bg-white/5 disabled:opacity-50"
          >
            <LogOut className="h-4 w-4 text-orange" /> Sign out
          </button>
          <button
            role="menuitem"
            type="button"
            disabled={busy}
            onClick={() => void signOut(true)}
            title="Ends your sessions in every browser"
            className="flex w-full items-center gap-3 px-4 py-2.5 text-left text-sm text-cream/70 hover:bg-white/5 disabled:opacity-50"
          >
            <LogOut className="h-4 w-4" /> Sign out everywhere
          </button>
        </div>
      )}
    </div>
  );
}

/**
 * The staff panel frame: guards the session, then shows the sidebar and top bar around the page.
 * Anonymous visitors go to the login (with ?next=), and anyone who must change a temporary
 * password goes to /admin/change-password, since the backend refuses everything else until then.
 */
export default function AdminShell({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const session = useAdminSession();
  const [drawerOpen, setDrawerOpen] = useState(false);

  useEffect(() => {
    if (session.status === "anonymous") {
      router.replace(`/admin/login?next=${encodeURIComponent(pathname)}`);
    } else if (session.status === "authenticated" && session.passwordChangeRequired) {
      router.replace("/admin/change-password");
    }
  }, [session.status, session.passwordChangeRequired, pathname, router]);

  useEffect(() => setDrawerOpen(false), [pathname]);

  if (session.status !== "authenticated" || session.passwordChangeRequired) {
    return (
      <div className="flex min-h-screen items-center justify-center text-cream/60">
        <span className="h-5 w-5 animate-spin rounded-full border-2 border-orange border-t-transparent" />
        <span className="ml-3">Checking your session…</span>
      </div>
    );
  }

  const brand = (
    <Link href="/admin" className="flex items-center gap-3 px-3">
      <img src="/assets/main_tools.svg" alt="" className="h-9 w-9 shrink-0" />
      <div className="leading-tight">
        <div className="text-lg font-black tracking-tight">IRON & OAK</div>
        <div className="text-[10px] font-bold tracking-[0.25em] text-orange">STAFF PANEL</div>
      </div>
    </Link>
  );

  return (
    <div className="flex min-h-screen">
      {/* fixed sidebar on large screens */}
      <aside className="sticky top-0 hidden h-screen w-64 shrink-0 flex-col overflow-y-auto border-r border-orange/20 bg-iron py-5 lg:flex">
        {brand}
        <div className="mt-8 flex-1 px-2">
          <SideNav pathname={pathname} onNavigate={() => undefined} />
        </div>
      </aside>

      {/* drawer below lg */}
      {drawerOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <button
            type="button"
            aria-label="Close menu"
            onClick={() => setDrawerOpen(false)}
            className="absolute inset-0 bg-black/60"
          />
          <aside className="relative h-full w-72 max-w-[85vw] overflow-y-auto border-r border-orange/20 bg-iron py-5">
            <div className="flex items-center justify-between pr-3">
              {brand}
              <button type="button" aria-label="Close menu" onClick={() => setDrawerOpen(false)} className="p-2">
                <X className="h-5 w-5" />
              </button>
            </div>
            <div className="mt-8 px-2">
              <SideNav pathname={pathname} onNavigate={() => setDrawerOpen(false)} />
            </div>
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex items-center justify-between gap-3 border-b border-orange/20 bg-[#17110a]/95 px-4 py-3 backdrop-blur sm:px-6">
          <button
            type="button"
            aria-label="Open menu"
            onClick={() => setDrawerOpen(true)}
            className="border border-orange/30 p-2 lg:hidden"
          >
            <Menu className="h-5 w-5" />
          </button>
          <p className="hidden text-xs font-bold tracking-[0.2em] text-cream/40 lg:block">STAFF AREA</p>
          <AccountMenu username={session.username ?? ""} fullName={session.fullName} />
        </header>
        <main className="min-w-0 flex-1 px-4 py-6 sm:px-6 lg:px-8">{children}</main>
      </div>
    </div>
  );
}
