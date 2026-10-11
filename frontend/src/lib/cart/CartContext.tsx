"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from "react";

/** Services are not orderable from the cart: they need a service-area city and their own flow. */
export type CartItemType = "PRODUCT" | "MACHINE";

export interface CartItem {
  /** `${itemType}:${referenceId}` */
  key: string;
  itemType: CartItemType;
  /** product or milling machine id: what the backend orders by */
  referenceId: number;
  name: string;
  code: string;
  /** Display only. The backend prices every line from the catalog when the order is placed. */
  unitPrice: number;
  imageUrl: string | null;
  quantity: number;
  /** stock on hand when added (products); null for machines, which are not stock-tracked */
  maxQuantity: number | null;
}

export type NewCartItem = Omit<CartItem, "key" | "quantity">;

interface CartContextValue {
  items: CartItem[];
  /** false until localStorage has been read, so the first render matches the server */
  hydrated: boolean;
  count: number;
  subtotal: number;
  add: (item: NewCartItem, quantity?: number) => void;
  setQuantity: (key: string, quantity: number) => void;
  remove: (key: string) => void;
  clear: () => void;
}

const STORAGE_KEY = "ironoak.cart.v1";
const HARD_LIMIT = 99;

const CartContext = createContext<CartContextValue | null>(null);

export function cartKey(itemType: CartItemType, referenceId: number): string {
  return `${itemType}:${referenceId}`;
}

function clampQuantity(quantity: number, maxQuantity: number | null): number {
  const limit = maxQuantity !== null ? Math.min(maxQuantity, HARD_LIMIT) : HARD_LIMIT;
  return Math.max(1, Math.min(Math.floor(quantity), limit));
}

function isCartItem(value: unknown): value is CartItem {
  if (typeof value !== "object" || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v.key === "string" &&
    (v.itemType === "PRODUCT" || v.itemType === "MACHINE") &&
    typeof v.referenceId === "number" &&
    typeof v.name === "string" &&
    typeof v.code === "string" &&
    typeof v.unitPrice === "number" &&
    typeof v.quantity === "number" &&
    (typeof v.maxQuantity === "number" || v.maxQuantity === null) &&
    (typeof v.imageUrl === "string" || v.imageUrl === null)
  );
}

function readStorage(): CartItem[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed.filter(isCartItem) : [];
  } catch {
    return [];
  }
}

export function CartProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<CartItem[]>([]);
  const [hydrated, setHydrated] = useState(false);

  useEffect(() => {
    setItems(readStorage());
    setHydrated(true);
    // keep several tabs in step
    const onStorage = (event: StorageEvent) => {
      if (event.key === STORAGE_KEY) setItems(readStorage());
    };
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, []);

  useEffect(() => {
    if (!hydrated) return;
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(items));
    } catch {
      // storage full or blocked: the cart still works for this page view
    }
  }, [items, hydrated]);

  const add = useCallback((item: NewCartItem, quantity = 1) => {
    const key = cartKey(item.itemType, item.referenceId);
    setItems((current) => {
      const existing = current.find((line) => line.key === key);
      if (existing) {
        return current.map((line) =>
          line.key === key
            ? { ...line, ...item, key, quantity: clampQuantity(line.quantity + quantity, item.maxQuantity) }
            : line,
        );
      }
      return [...current, { ...item, key, quantity: clampQuantity(quantity, item.maxQuantity) }];
    });
  }, []);

  const setQuantity = useCallback((key: string, quantity: number) => {
    setItems((current) =>
      current.map((line) => (line.key === key ? { ...line, quantity: clampQuantity(quantity, line.maxQuantity) } : line)),
    );
  }, []);

  const remove = useCallback((key: string) => {
    setItems((current) => current.filter((line) => line.key !== key));
  }, []);

  const clear = useCallback(() => setItems([]), []);

  const value = useMemo<CartContextValue>(
    () => ({
      items,
      hydrated,
      count: items.reduce((sum, line) => sum + line.quantity, 0),
      subtotal: items.reduce((sum, line) => sum + line.unitPrice * line.quantity, 0),
      add,
      setQuantity,
      remove,
      clear,
    }),
    [items, hydrated, add, setQuantity, remove, clear],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartContextValue {
  const context = useContext(CartContext);
  if (!context) throw new Error("useCart must be used inside <CartProvider>");
  return context;
}
