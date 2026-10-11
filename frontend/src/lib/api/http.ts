import type { ProblemDetail } from "./types";

/** Backend origin. Public because the browser calls the API directly (CORS is allowed server-side). */
export const API_URL = (process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080").replace(/\/+$/, "");

/** A failed API call. status 0 means the server could not be reached at all. */
export class ApiError extends Error {
  readonly status: number;
  readonly problem?: ProblemDetail;
  /** Seconds to wait, from a 429 Retry-After header. */
  readonly retryAfterSeconds?: number;

  constructor(status: number, message: string, problem?: ProblemDetail, retryAfterSeconds?: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}

type QueryValue = string | number | boolean | null | undefined;

export interface RequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  query?: Record<string, QueryValue>;
  /** JSON body */
  body?: unknown;
  /** multipart body (do not set Content-Type: the browser adds the boundary) */
  formData?: FormData;
  headers?: Record<string, string>;
  /** Bearer access token for /api/admin and /api/dashboard routes */
  token?: string | null;
  signal?: AbortSignal;
}

function buildUrl(path: string, query?: RequestOptions["query"]): string {
  const url = new URL(API_URL + path);
  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== null && value !== "") {
        url.searchParams.set(key, String(value));
      }
    }
  }
  return url.toString();
}

const GENERIC: Record<number, string> = {
  400: "The request was not valid.",
  401: "You are not signed in, or the credentials are wrong.",
  403: "You do not have access to this.",
  404: "Not found.",
  409: "That conflicts with existing data. Please try again.",
  422: "That request is not allowed right now.",
  429: "Too many requests. Please wait a moment.",
  502: "The payment provider refused the request.",
  503: "This service is temporarily unavailable.",
};

/**
 * One fetch wrapper for server and client components. Errors carry the backend's problem
 * detail text, so UI code can show `error.message` directly.
 */
export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json", ...options.headers };
  let body: BodyInit | undefined;
  if (options.formData) {
    body = options.formData;
  } else if (options.body !== undefined) {
    headers["Content-Type"] = "application/json";
    body = JSON.stringify(options.body);
  }
  if (options.token) {
    headers.Authorization = `Bearer ${options.token}`;
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method: options.method ?? "GET",
      headers,
      body,
      signal: options.signal,
      cache: "no-store",
    });
  } catch (cause) {
    if (cause instanceof DOMException && cause.name === "AbortError") throw cause;
    throw new ApiError(0, "Cannot reach the server. Check your connection and try again.");
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  let parsed: unknown;
  if (text) {
    try {
      parsed = JSON.parse(text);
    } catch {
      parsed = undefined;
    }
  }

  if (!response.ok) {
    const problem = parsed as ProblemDetail | undefined;
    const retryHeader = response.headers.get("Retry-After");
    const retry = retryHeader ? Number.parseInt(retryHeader, 10) : undefined;
    throw new ApiError(
      response.status,
      problem?.detail || GENERIC[response.status] || `Request failed (${response.status}).`,
      problem,
      Number.isFinite(retry) ? retry : undefined,
    );
  }

  return parsed as T;
}
