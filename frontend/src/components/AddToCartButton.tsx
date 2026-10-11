"use client";

import { useState } from "react";
import { Check, Minus, Plus, ShoppingCart } from "lucide-react";

import { useCart, type NewCartItem } from "@/lib/cart/CartContext";

type Props = {
  item: NewCartItem;
  disabled?: boolean;
  /** show a quantity stepper next to the button (product page) */
  withQuantity?: boolean;
  label?: string;
  className?: string;
};

export default function AddToCartButton({ item, disabled = false, withQuantity = false, label = "ADD", className = "" }: Props) {
  const { add } = useCart();
  const [quantity, setQuantity] = useState(1);
  const [justAdded, setJustAdded] = useState(false);

  const max = item.maxQuantity ?? 99;

  function handleAdd() {
    add(item, quantity);
    setJustAdded(true);
    window.setTimeout(() => setJustAdded(false), 1500);
  }

  const button = (
    <button
      type="button"
      onClick={handleAdd}
      disabled={disabled}
      className={`flex flex-1 items-center justify-center gap-2 bg-orange px-3 py-3 text-sm font-black text-black transition-colors hover:bg-orange-light disabled:cursor-not-allowed disabled:bg-black/20 disabled:text-black/40 ${className}`}
    >
      {justAdded ? <Check className="h-4 w-4" /> : <ShoppingCart className="h-4 w-4" />}
      {disabled ? "UNAVAILABLE" : justAdded ? "ADDED" : label}
    </button>
  );

  if (!withQuantity) return button;

  return (
    <div className="flex gap-3">
      <div className="flex items-center border border-black/40 bg-white text-black">
        <button
          type="button"
          aria-label="Decrease quantity"
          onClick={() => setQuantity((q) => Math.max(1, q - 1))}
          className="px-3 py-3"
        >
          <Minus className="h-4 w-4" />
        </button>
        <span className="w-10 text-center font-black">{quantity}</span>
        <button
          type="button"
          aria-label="Increase quantity"
          onClick={() => setQuantity((q) => Math.min(max, q + 1))}
          className="px-3 py-3"
        >
          <Plus className="h-4 w-4" />
        </button>
      </div>
      {button}
    </div>
  );
}
