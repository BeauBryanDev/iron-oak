import { adminLogin, adminLogout, adminRefresh } from "@/lib/api/adminAuth";
import { ApiError, request, type RequestOptions } from "@/lib/api/http";
import type { AdminLoginResponse } from "@/lib/api/types";

/**
 * The staff session, kept outside React so every component and API call shares one copy.
 *
 * - The short-lived access JWT lives only in memory.
 * - The refresh token lives in sessionStorage: it survives a reload but not closing the tab.
 *   It is single-use (every refresh returns the next one), so concurrent refreshes MUST share one
 *   request: a second use of an old token makes the backend revoke the whole session family.
 * - The browser talks to the backend directly, not through a Next proxy, because the backend's
 *   login lockouts and rate limits count per client IP.
 */

export type SessionStatus = "loading" | "anonymous" | "authenticated";

export interface AdminSession {
  status: SessionStatus;
  username: string | null;
  fullName: string | null;
  /** the backend only lets this person change their password until they have */
  passwordChangeRequired: boolean;
}

const REFRESH_KEY = "ironoak.admin.refresh";
/** refresh this long before the access token expires */
const REFRESH_MARGIN_MS = 60_000;

const LOADING: AdminSession = {
  status: "loading",
  username: null,
  fullName: null,
  passwordChangeRequired: false
};

const ANONYMOUS: AdminSession = {
  status: "anonymous",
  username: null,
  fullName: null,
  passwordChangeRequired: false
};

let snapshot: AdminSession = LOADING;
let accessToken: string | null = null;
let accessExpiresAt = 0;
let refreshTimer: number | undefined;
let refreshInFlight: Promise<boolean> | null = null;
let initPromise: Promise<void> | null = null;
const listeners = new Set<() => void>();

// store plumbing for useSyncExternalStore 

export function subscribe(listener: () => void): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getSnapshot(): AdminSession {
  return snapshot;
}

export function getServerSnapshot(): AdminSession {
  return LOADING;
}

function publish(next: AdminSession): void {
  snapshot = next;
  listeners.forEach((listener) => listener());
}

// storage

function readRefreshToken(): string | null {
  try {
    return window.sessionStorage.getItem(REFRESH_KEY);
  } catch {
    return null;
  }
}

function writeRefreshToken(token: string | null): void {
  try {
    if (token) window.sessionStorage.setItem(REFRESH_KEY, token);
    else window.sessionStorage.removeItem(REFRESH_KEY);
  } catch {
    // storage blocked: the session then lasts only until the page reloads
  }
}

// core 

function clearSession(): void {
  accessToken = null;
  accessExpiresAt = 0;
  if (refreshTimer !== undefined) window.clearTimeout(refreshTimer);
  refreshTimer = undefined;
  writeRefreshToken(null);
  publish(ANONYMOUS);
}

function applyLogin(response: AdminLoginResponse): void {
  accessToken = response.token;
  accessExpiresAt = Date.now() + response.expiresInMinutes * 60_000;
  writeRefreshToken(response.refreshToken);

  if (refreshTimer !== undefined) window.clearTimeout(refreshTimer);
  const delay = Math.max(5_000, accessExpiresAt - Date.now() - REFRESH_MARGIN_MS);
  refreshTimer = window.setTimeout(() => void refreshSession(), delay);

  publish({
    status: "authenticated",
    username: response.username,
    fullName: response.fullName,
    passwordChangeRequired: response.passwordChangeRequired,
  });
}

/**
 * Trades the stored refresh token for a new pair. Returns true when the session is live.
 * A rejected token ends the session; a network or server error keeps it so a later call can retry.
 */
export function refreshSession(): Promise<boolean> {
  if (refreshInFlight) return refreshInFlight;

  const stored = readRefreshToken();
  if (!stored) {
    clearSession();
    return Promise.resolve(false);
  }

  refreshInFlight = (async () => {
    try {
      applyLogin(await adminRefresh(stored));
      return true;
    } catch (cause) {
      if (cause instanceof ApiError && (cause.status === 400 || cause.status === 401 || cause.status === 403)) {
        clearSession();
      } else if (snapshot.status === "loading") {
        publish(ANONYMOUS); // could not check; the token stays for a later retry
      }
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

/** Restores a session after a reload. Safe to call from many components: it runs once. */
export function initSession(): Promise<void> {
  if (typeof window === "undefined") return Promise.resolve();
  initPromise ??= (async () => {
    if (readRefreshToken()) {
      await refreshSession();
    } else {
      publish(ANONYMOUS);
    }
  })();
  return initPromise;
}

export async function login(username: string,
  password: string): Promise<AdminLoginResponse> {

  const response = await adminLogin(username, password);
  applyLogin(response);

  return response;
}

/** Ends the session here at once, then tells the backend to revoke the refresh token. */
export async function logout(): Promise<void> {
  const stored = readRefreshToken();
  clearSession();
  if (stored) {
    try {
      await adminLogout(stored);
    } catch {
      // the local session is already gone; an unrevoked token still expires on its own
    }
  }
}

/** An admin-API call with the access token attached, refreshed first if it is about to expire. */
export async function authed<T>(path: string,
  options: Omit<RequestOptions, "token"> = {}): Promise<T> {

  if (!accessToken || Date.now() > accessExpiresAt - 30_000) {

    if (!(await refreshSession()) || !accessToken) {
      throw new ApiError(401, "Your session has ended. Please sign in again.");
    }
  }
  try {
    return await request<T>(path, { ...options, token: accessToken });

  } catch (cause) {
    if (!(cause instanceof ApiError) || cause.status !== 401) throw cause;
    // the token was refused (revoked or expired early): one refresh, one retry
    if (!(await refreshSession()) || !accessToken) {
      throw new ApiError(401, "Your session has ended. Please sign in again.");
    }
    return request<T>(path, { ...options, token: accessToken });
  }
}

/** The new access/refresh pair is applied here, since a password change revokes the old ones. */
export async function changePassword(currentPassword: string,
  newPassword: string): Promise<void> {

  const response = await authed<AdminLoginResponse>("/api/admin/auth/change-password", {
    method: "POST",
    body: { currentPassword, newPassword },
  });

  applyLogin(response);
}
