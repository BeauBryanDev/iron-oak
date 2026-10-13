import { authed } from "@/lib/auth/adminSession";
import type {
  AdminProductResponse,
  DashboardKPIResponse,
  Paged,
} from "./types";

/** Staff dashboard data. Every call goes through authed() (token, refresh, one retry on 401). */

export function getDashboardKpis(): Promise<DashboardKPIResponse> {
  return authed("/api/dashboard/kpis");
}

export interface AdminProductQuery {
  q?: string;
  category?: string;
  active?: boolean;
  /** low-stock view: stock at or below this */
  maxStock?: number;
  page?: number;
  size?: number;
  sort?: string;
}

export function adminListProducts(query: AdminProductQuery = {}): Promise<Paged<AdminProductResponse>> {
  return authed("/api/admin/products", { query: { ...query } });
}
