import { authed } from "@/lib/auth/adminSession";
import type { CustomerRequest, CustomerResponse, Paged } from "./types";

/**
 * Staff customer management (/api/admin/customers). A duplicate email is a 409; delete is a
 * hard delete and answers 409 while any order, booking, claim, ticket or chat refers to the customer.
 */

export interface AdminCustomerQuery {
  /** name, email or phone */
  q?: string;
  /** inclusive UTC dates on the creation date, yyyy-MM-dd */
  from?: string;
  to?: string;
  page?: number;
  size?: number;
  sort?: string;
}

export function adminListCustomers(query: AdminCustomerQuery = {}): Promise<Paged<CustomerResponse>> {
  return authed("/api/admin/customers", { query: { ...query } });
}

export function adminGetCustomer(id: number): Promise<CustomerResponse> {
  return authed(`/api/admin/customers/${id}`);
}

export function adminCreateCustomer(body: CustomerRequest): Promise<CustomerResponse> {
  return authed("/api/admin/customers", { method: "POST", body });
}

/** Full replacement: send every field, an omitted one is cleared. */
export function adminUpdateCustomer(id: number, body: CustomerRequest): Promise<CustomerResponse> {
  return authed(`/api/admin/customers/${id}`, { method: "PUT", body });
}

export function adminDeleteCustomer(id: number): Promise<void> {
  return authed<void>(`/api/admin/customers/${id}`, { method: "DELETE" });
}
