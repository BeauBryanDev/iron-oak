"use client";

import { useEffect, useState, type FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Eye, EyeOff, Lock } from "lucide-react";

import { ApiError } from "@/lib/api/http";
import { login } from "@/lib/auth/adminSession";
import { useAdminSession } from "@/lib/auth/useAdminSession";

const inputClass =
  "w-full border border-orange/40 bg-[#17110a] px-3 py-3 text-cream outline-none focus:border-orange disabled:opacity-60";

/** ?next= may only point inside the admin area (never an external URL). */
function afterLoginTarget(passwordChangeRequired: boolean): string {
  if (passwordChangeRequired) return "/admin/change-password";
  const next = new URLSearchParams(window.location.search).get("next");
  return next && next.startsWith("/admin") && !next.startsWith("//") ? next : "/admin";
}

function describeWait(seconds: number): string {
  if (seconds >= 120) return `${Math.ceil(seconds / 60)} minutes`;
  if (seconds >= 60) return "about a minute";
  return `${seconds} seconds`;
}

export default function AdminLoginForm() {
  const router = useRouter();
  const session = useAdminSession();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  /** epoch ms until which the backend has locked this sign-in (HTTP 429 + Retry-After) */
  const [lockedUntil, setLockedUntil] = useState<number | null>(null);
  const [now, setNow] = useState(() => Date.now());

  // someone who is already signed in has no reason to see this page
  useEffect(() => {
    if (session.status === "authenticated") {
      router.replace(afterLoginTarget(session.passwordChangeRequired));
    }
  }, [session.status, session.passwordChangeRequired, router]);

  // countdown while locked
  useEffect(() => {
    if (lockedUntil === null) return;
    const timer = window.setInterval(() => {
      setNow(Date.now());
      if (Date.now() >= lockedUntil) {
        setLockedUntil(null);
        setError(null);
      }
    }, 1000);
    return () => window.clearInterval(timer);
  }, [lockedUntil]);

  const secondsLeft = lockedUntil === null ? 0 : Math.max(0, Math.ceil((lockedUntil - now) / 1000));
  const locked = secondsLeft > 0;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitting || locked) return;
    setSubmitting(true);
    setError(null);

    try {
      const response = await login(username.trim(), password);
      router.replace(afterLoginTarget(response.passwordChangeRequired));
    } catch (cause) {
      setPassword("");
      if (cause instanceof ApiError && cause.status === 429) {
        const wait = cause.retryAfterSeconds ?? 60;
        setNow(Date.now());
        setLockedUntil(Date.now() + wait * 1000);
      } else if (cause instanceof ApiError && cause.status === 401) {
        // one message for a wrong password and an unknown user, as the backend does
        setError("Incorrect username or password.");
      } else if (cause instanceof ApiError && cause.status === 0) {
        setError(cause.message);
      } else if (cause instanceof ApiError && cause.status === 400) {
        setError("Enter your username and password.");
      } else {
        setError("We could not sign you in right now. Please try again.");
      }
      setSubmitting(false);
    }
  }

  return (
    <main className="flex min-h-screen items-center justify-center px-4 py-12">
      <div className="w-full max-w-md">
        <Link href="/" className="mb-8 flex items-center justify-center gap-3">
          <img src="/assets/main_tools.svg" alt="" className="h-12 w-12 shrink-0" />
          <div>
            <div className="text-3xl font-black tracking-tight text-cream">IRON & OAK</div>
            <div className="text-xs font-bold tracking-[0.25em] text-orange">STAFF AREA</div>
          </div>
        </Link>

        <form onSubmit={handleSubmit} className="space-y-5 border border-orange/30 bg-[#241b0e] p-8" noValidate>
          <div className="flex items-center gap-3">
            <Lock className="h-6 w-6 text-orange" />
            <h1 className="industrial-title text-3xl">SIGN IN</h1>
          </div>

          <label className="block">
            <span className="text-xs font-bold tracking-wide text-cream/60">USERNAME</span>
            <input
              required
              maxLength={100}
              autoComplete="username"
              autoCapitalize="none"
              autoCorrect="off"
              spellCheck={false}
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              disabled={submitting}
              className={`${inputClass} mt-1`}
            />
          </label>

          <label className="block">
            <span className="text-xs font-bold tracking-wide text-cream/60">PASSWORD</span>
            <div className="relative mt-1">
              <input
                required
                maxLength={128}
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                disabled={submitting}
                className={`${inputClass} pr-12`}
              />
              <button
                type="button"
                onClick={() => setShowPassword((visible) => !visible)}
                aria-label={showPassword ? "Hide password" : "Show password"}
                className="absolute inset-y-0 right-0 px-3 text-cream/60 hover:text-orange"
              >
                {showPassword ? <EyeOff className="h-5 w-5" /> : <Eye className="h-5 w-5" />}
              </button>
            </div>
          </label>

          <div aria-live="polite" className="min-h-[1.25rem]">
            {locked ? (
              <p role="alert" className="border border-orange/50 bg-orange/10 p-3 text-sm text-orange">
                Too many attempts. For security, sign-in is paused. Try again in {describeWait(secondsLeft)}.
              </p>
            ) : (
              error && (
                <p role="alert" className="border border-red-400/50 bg-red-950/40 p-3 text-sm text-red-200">
                  {error}
                </p>
              )
            )}
          </div>

          <button
            type="submit"
            disabled={submitting || locked || !username.trim() || !password}
            className="w-full bg-orange px-6 py-4 text-sm font-black text-black transition-colors hover:bg-orange-light disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? "SIGNING IN…" : "SIGN IN"}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-cream/50">
          <Link href="/" className="font-bold text-orange hover:text-orange-light">
            ← Back to the shop
          </Link>
        </p>
      </div>
    </main>
  );
}
