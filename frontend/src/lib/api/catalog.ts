import { request } from "./http";
import type {
  EndMillResponse,
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
    query: {
      category: query.category,
      q: query.q,
      page: query.page,
      size: query.size,
      sort: query.sort
    },
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

export function getMachine(modelCode: string): Promise<MillingMachineResponse> {
  return request(`/api/services/machines/${encodeURIComponent(modelCode)}`);
}

export function listServices(): Promise<ServiceOfferingResponse[]> {
  return request("/api/services");
}

//  end mills (milling cutters), public and read-only 

export interface EndMillQuery {
  /** e.g. "Ball Nose", "Fishtail" (any case); see listFluteTypes() */
  fluteType?: string;
  /** cutting diameter in mm, e.g. 2 */
  diameterMm?: number;
  /** name or SKU search */
  q?: string;
  page?: number;
  size?: number;
  /** Spring sort, e.g. "price,asc"; the backend default is name */
  sort?: string;
}

/** Active end mills only. */
export function listEndMills(query: EndMillQuery = {}): Promise<Paged<EndMillResponse>> {
  return request("/api/end-mills", {
    query: {
      fluteType: query.fluteType,
      diameterMm: query.diameterMm,
      q: query.q,
      page: query.page,
      size: query.size,
      sort: query.sort,
    },
  });
}

export function getEndMill(id: number): Promise<EndMillResponse> {
  return request(`/api/end-mills/${id}`);
}

export function getEndMillBySku(sku: string): Promise<EndMillResponse> {
  return request(`/api/end-mills/sku/${encodeURIComponent(sku)}`);
}

/** Flute types on sale, for a filter, e.g. ["Ball Nose", "Fishtail"]. */
export function listFluteTypes(): Promise<string[]> {
  return request("/api/end-mills/flute-types");
}

/** The end mill a YOLOv11 detection names, e.g. "ball_nose_end_mill_2mm". */
export function getEndMillByVisionLabel(visionLabel: string): Promise<EndMillResponse> {
  return request(`/api/end-mills/by-vision/${encodeURIComponent(visionLabel)}`);
}
