"use client";

import { useEffect, useRef, useState } from "react";
import { AlertTriangle } from "lucide-react";

/** Shared building blocks for the staff screens (brand colours, square corners). */

export const inputClass =
  "w-full border border-orange/40 bg-[#17110a] px-3 py-2 text-sm text-cream outline-none placeholder:text-cream/30 focus:border-orange disabled:opacity-60";

export const primaryButton =
  "bg-orange px-4 py-2 text-xs font-black tracking-wide text-black hover:bg-orange-light disabled:cursor-not-allowed disabled:opacity-50";

export const outlineButton =
  "border border-orange px-4 py-2 text-xs font-black tracking-wide text-orange hover:bg-orange hover:text-black disabled:cursor-not-allowed disabled:opacity-50";

export const dangerButton =
  "border border-red-400/70 px-4 py-2 text-xs font-black tracking-wide text-red-300 hover:bg-red-500 hover:text-black disabled:cursor-not-allowed disabled:opacity-50";

export function Panel({ title, subtitle, actions, children, className = "" }: {
  title: string;
  subtitle?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`border border-orange/20 bg-iron p-5 ${className}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <h2 className="text-sm font-black tracking-[0.15em] text-cream">{title}</h2>
          {subtitle && <p className="mt-1 text-xs text-cream/50">{subtitle}</p>}
        </div>
        {actions}
      </div>
      <div className="mt-4">{children}</div>
    </section>
  );
}

export function ErrorNote({ message }: { message: string }) {
  return (
    <p role="alert" className="flex items-start gap-2 border border-red-400/40 bg-red-950/30 p-3 text-sm text-red-200">
      <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {message}
    </p>
  );
}

export function Skeleton({ className }: { className: string }) {
  return <div className={`animate-pulse bg-white/5 ${className}`} />;
}

/** Label/value rows for detail panels; null values show a dash. */
export function Facts({ rows }: { rows: [string, React.ReactNode][] }) {
  return (
    <dl className="space-y-2 text-sm">
      {rows.map(([label, value]) => (
        <div key={label} className="flex gap-3">
          <dt className="w-32 shrink-0 text-cream/50">{label}</dt>
          <dd className="min-w-0 flex-1 break-words text-cream/90">{value ?? "—"}</dd>
        </div>
      ))}
    </dl>
  );
}

/**
 * A modal confirmation. onConfirm may throw; its message is shown inside the dialog and the
 * dialog stays open, so the backend's 409/422 text reaches the person who clicked.
 */
export function ConfirmDialog({ title, body, confirmLabel, danger = false, onConfirm, onClose, children }: {
  title: string;
  body: React.ReactNode;
  confirmLabel: string;
  danger?: boolean;
  onConfirm: () => Promise<void>;
  onClose: () => void;
  /** optional extra inputs (e.g. a refund reason) */
  children?: React.ReactNode;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const confirmRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    confirmRef.current?.focus();
    const escape = (event: KeyboardEvent) => event.key === "Escape" && !busy && onClose();
    document.addEventListener("keydown", escape);
    return () => document.removeEventListener("keydown", escape);
  }, [busy, onClose]);

  async function confirm() {
    setBusy(true);
    setError(null);
    try {
      await onConfirm();
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "That did not work. Please try again.");
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="confirm-title">
      <button type="button" aria-label="Close" onClick={() => !busy && onClose()} className="absolute inset-0 bg-black/70" />
      <div className="relative w-full max-w-md border border-orange/40 bg-iron p-6 shadow-2xl">
        <h2 id="confirm-title" className="industrial-title text-2xl">{title}</h2>
        <div className="mt-3 text-sm text-cream/80">{body}</div>
        {children && <div className="mt-4">{children}</div>}
        {error && <div className="mt-4"><ErrorNote message={error} /></div>}
        <div className="mt-6 flex justify-end gap-3">
          <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 text-xs font-black text-cream/70 hover:text-cream">
            BACK
          </button>
          <button ref={confirmRef} type="button" onClick={() => void confirm()} disabled={busy} className={danger ? dangerButton : primaryButton}>
            {busy ? "WORKING…" : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
