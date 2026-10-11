import { authed } from "@/lib/auth/adminSession";
import type { AdminEndMillResponse, EndMillRequest, Paged } from "./types";

/**
 * Staff end-mill catalog (/api/admin/end-mills). Every call goes through authed(), which
 * attaches the access token, refreshes it when needed and retries a 401 once.
 */

export interface AdminEndMillQuery {
  /** name, SKU or vision label */
  q?: string;
  fluteType?: string;
  /** omit for all, true = on sale, false = deactivated */
  active?: boolean;
  /** low-stock view: stock at or below this */
  maxStock?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export function adminListEndMills(query: AdminEndMillQuery = {}): Promise<Paged<AdminEndMillResponse>> {
  return authed("/api/admin/end-mills", {
    query: {
      q: query.q,
      fluteType: query.fluteType,
      active: query.active,
      maxStock: query.maxStock,
      page: query.page,
      size: query.size,
      sort: query.sort,
    },
  });
}

export function adminGetEndMill(id: number): Promise<AdminEndMillResponse> {
  return authed(`/api/admin/end-mills/${id}`);
}

/** 409 for a duplicate SKU or vision label, 422 when radiusMm exceeds half the diameter. */
export function adminCreateEndMill(body: EndMillRequest): Promise<AdminEndMillResponse> {
  return authed("/api/admin/end-mills",
    { method: "POST", body });
}

/** Full replace of the editable fields; do not send stockQuantity (422). */
export function adminUpdateEndMill(id: number,
  body: Omit<EndMillRequest, "stockQuantity">): Promise<AdminEndMillResponse> {
  return authed(`/api/admin/end-mills/${id}`,
    { method: "PUT", body });
}

/** Deactivated end mills disappear from the storefront at once; nothing is ever deleted. */
export function adminSetEndMillActive(id: number,
  isActive: boolean): Promise<AdminEndMillResponse> {
  return authed(`/api/admin/end-mills/${id}/active`,
    {
      method: "PATCH",
      body: { isActive }
    });
}

/** delta > 0 adds stock, < 0 removes it; refused below zero (422). */
export function adminAdjustEndMillStock(id: number,
  delta: number): Promise<AdminEndMillResponse> {
  return authed(`/api/admin/end-mills/${id}/stock`,
    {
      method: "PATCH",
      body: { delta }
    });
}
