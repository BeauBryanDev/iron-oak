"use client";

import { useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Check, Eye, EyeOff, KeyRound, X } from "lucide-react";

import { ApiError } from "@/lib/api/http";
import { changePassword, logout } from "@/lib/auth/adminSession";
import { useAdminSession } from "@/lib/auth/useAdminSession";

/** Mirrors the backend's PasswordPolicy; the backend still has the final word (422). */
const MIN_LENGTH = 12;
const MAX_BYTES = 72;

const inputClass =
  "w-full border border-orange/40 bg-[#17110a] px-3 py-3 pr-12 text-cream outline-none focus:border-orange disabled:opacity-60";

function rules(next: string, confirm: string, current: string, username: string) {
  const length = [...next].length;
  return [
    { ok: length >= MIN_LENGTH, text: `At least ${MIN_LENGTH} characters` },
    { ok: next.length > 0 && new TextEncoder().encode(next).length <= MAX_BYTES, text: `At most ${MAX_BYTES} bytes (about ${MAX_BYTES} plain characters)` },
    { ok: next.length > 0 && next !== current, text: "Different from the current password" },
    { ok: next.length > 0 && (!username || !next.toLowerCase().includes(username.toLowerCase())), text: "Does not contain your username" },
    { ok: next.length > 0 && next === confirm, text: "Both new passwords match" },
  ];
}

function PasswordField({ label, value, onChange, autoComplete, disabled }: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete: string;
  disabled: boolean;
}) {
  const [visible, setVisible] = useState(false);
  return (
    <label className="block">
      <span className="text-xs font-bold tracking-wide text-cream/60">{label}</span>
      <div className="relative mt-1">
        <input
          type={visible ? "text" : "password"}
          required
          maxLength={128}
          autoComplete={autoComplete}
          value={value}
          onChange={(event) => onChange(event.target.value)}
          disabled={disabled}
          className={inputClass}
        />
        <button
          type="button"
          onClick={() => setVisible((v) => !v)}
          aria-label={visible ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
          className="absolute inset-y-0 right-0 px-3 text-cream/60 hover:text-orange"
        >
          {visible ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
        </button>
      </div>
    </label>
  );
}

/**
 * Required after the first sign-in with a temporary password (bootstrap admin, new or reset
 * staff); until then the backend refuses every other staff route. Also reachable from the
 * account menu at any time. A successful change revokes the other sessions and signs in again.
 */
export default function ChangePasswordForm() {
  const router = useRouter();
  const session = useAdminSession();

  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (session.status === "anonymous") router.replace("/admin/login?next=/admin/change-password");
  }, [session.status, router]);

  if (session.status !== "authenticated") {
    return <p className="p-8 text-cream/70">Checking your session…</p>;
  }

  const required = session.passwordChangeRequired;
  const checks = rules(next, confirm, current, session.username ?? "");
  const valid = current.length > 0 && checks.every((check) => check.ok);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!valid || submitting) return;
    setSubmitting(true);
    setError(null);
    try {
      await changePassword(current, next);
      router.replace("/admin");
    } catch (cause) {
      // a wrong current password is a 422 "Current password is incorrect"; an ended session a 401
      if (cause instanceof ApiError && cause.status === 429) {
        setError("Too many attempts. Please wait a moment and try again.");
      } else {
        setError(cause instanceof ApiError ? cause.message : "We could not change your password. Please try again.");
      }
      setSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        <div className="mb-8 flex items-center justify-center gap-3">
          <img src="/assets/main_tools.svg" alt="" className="h-12 w-12 shrink-0" />
          <div>
            <div className="text-3xl font-black tracking-tight text-cream">IRON & OAK</div>
            <div className="text-xs font-bold tracking-[0.25em] text-orange">STAFF AREA</div>
          </div>
        </div>

        <form onSubmit={handleSubmit} noValidate className="space-y-5 border border-orange/30 bg-iron p-8">
          <div className="flex items-center gap-3">
            <KeyRound className="h-6 w-6 text-orange" />
            <h1 className="industrial-title text-3xl">CHANGE PASSWORD</h1>
          </div>

          {required && (
            <p className="border border-orange/50 bg-orange/10 p-3 text-sm text-orange">
              You signed in with a temporary password. Choose your own to open the staff area.
            </p>
          )}

          <PasswordField label="CURRENT PASSWORD" value={current} onChange={setCurrent} autoComplete="current-password" disabled={submitting} />
          <PasswordField label="NEW PASSWORD" value={next} onChange={setNext} autoComplete="new-password" disabled={submitting} />
          <PasswordField label="REPEAT NEW PASSWORD" value={confirm} onChange={setConfirm} autoComplete="new-password" disabled={submitting} />

          <ul className="space-y-1 text-xs" aria-label="Password rules">
            {checks.map((check) => (
              <li key={check.text} className={`flex items-center gap-2 ${check.ok ? "text-emerald-300" : "text-cream/50"}`}>
                {check.ok ? <Check className="h-3.5 w-3.5" /> : <X className="h-3.5 w-3.5" />}
                {check.text}
              </li>
            ))}
          </ul>

          <div aria-live="polite">
            {error && (
              <p role="alert" className="border border-red-400/50 bg-red-950/40 p-3 text-sm text-red-200">{error}</p>
            )}
          </div>

          <button
            type="submit"
            disabled={!valid || submitting}
            className="w-full bg-orange px-6 py-4 text-sm font-black text-black transition-colors hover:bg-orange-light disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? "SAVING…" : "CHANGE PASSWORD"}
          </button>
          <p className="text-xs text-cream/40">Changing it signs you out of every other browser.</p>
        </form>

        <p className="mt-6 flex justify-center gap-6 text-sm">
          {!required && (
            <Link href="/admin" className="font-bold text-orange hover:text-orange-light">← Back to the dashboard</Link>
          )}
          <button
            type="button"
            onClick={async () => {
              await logout();
              router.replace("/admin/login");
            }}
            className="font-bold text-cream/60 hover:text-orange"
          >
            Sign out
          </button>
        </p>
      </div>
    </main>
  );
}
