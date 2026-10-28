import { authed } from "@/lib/auth/adminSession";
import type { ManualRouteRequest, RouteRefreshResult, ShippingRouteResponse } from "./types";

/**
 * Stored road distances (/api/admin/shipping/routes). Air countries (US, MX, CR, PA) have no
 * routes, so setting one there is a 422; a Colombian city added here becomes deliverable.
 */

export function adminListRoutes(): Promise<ShippingRouteResponse[]> {
  return authed("/api/admin/shipping/routes");
}

export function adminSetRoute(body: ManualRouteRequest): Promise<ShippingRouteResponse> {
  return authed("/api/admin/shipping/routes", { method: "PUT", body });
}

/** Fetches the known cities not stored yet from Google, within the daily call cap. */
export function adminRefreshRoutes(): Promise<RouteRefreshResult> {
  return authed("/api/admin/shipping/routes/refresh", { method: "POST" });
}
