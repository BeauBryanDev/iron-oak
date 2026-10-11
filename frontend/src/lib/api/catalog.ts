import { request } from "./http";
import type {
  MillingMachineResponse,
  Paged,
  ProductResponse,
  ServiceOfferingResponse,
  ToolCategoryResponse,
} from "./types";

export interface ProductQuery {
  /** storefront category name, e.g. "Hand Tools" */
  category?: string;
  /** name search; wins over category on the backend */
  q?: string;
  page?: number;
  size?: number;
  /** Spring sort, e.g. "price,asc"; the backend default is name */
  sort?: string;
}

export function listProducts(query: ProductQuery = {}): Promise<Paged<ProductResponse>> {
  return request("/api/products", {
    query: { category: query.category, q: query.q, page: query.page, size: query.size, sort: query.sort },
  });
}

export function getProduct(id: number): Promise<ProductResponse> {
  return request(`/api/products/${id}`);
}

/** Active storefront category names. */
export function listCategories(): Promise<string[]> {
  return request("/api/products/categories");
}

export function listToolCategories(): Promise<ToolCategoryResponse[]> {
  return request("/api/products/tool-categories");
}

export function listMachines(): Promise<MillingMachineResponse[]> {
  return request("/api/services/machines");
}

export function listServices(): Promise<ServiceOfferingResponse[]> {
  return request("/api/services");
}
