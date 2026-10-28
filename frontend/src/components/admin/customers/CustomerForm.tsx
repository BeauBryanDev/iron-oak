"use client";

import { useEffect, useState, type FormEvent } from "react";

import { ErrorNote, inputClass, primaryButton } from "@/components/admin/ui";
import type { CustomerRequest, CustomerResponse } from "@/lib/api/types";

/**
 * Create or edit a customer in a modal. Edit is a full replacement (PUT), so every field is sent
 * and a blank one clears it. onSave may throw; the backend's 409/422 text stays in the dialog.
 */
export default function CustomerForm({ customer, onSave, onClose }: {
  /** null = new customer */
  customer: CustomerResponse | null;
  onSave: (body: CustomerRequest) => Promise<void>;
  onClose: () => void;
}) {
  const [name, setName] = useState(customer?.name ?? "");
  const [email, setEmail] = useState(customer?.email ?? "");
  const [phone, setPhone] = useState(customer?.phone ?? "");
  const [address, setAddress] = useState(customer?.address ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const escape = (event: KeyboardEvent) => event.key === "Escape" && !busy && onClose();
    document.addEventListener("keydown", escape);
    return () => document.removeEventListener("keydown", escape);
  }, [busy, onClose]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await onSave({
        name: name.trim(),
        email: email.trim() || null,
        phone: phone.trim() || null,
        address: address.trim() || null,
      });
      onClose();
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Could not save the customer.");
      setBusy(false);
    }
  }

  const label = "mb-1 block text-xs font-bold tracking-wider text-cream/60";

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="customer-form-title">
      <button type="button" aria-label="Close" onClick={() => !busy && onClose()} className="absolute inset-0 bg-black/70" />
      <form onSubmit={submit} className="relative w-full max-w-lg space-y-4 border border-orange/40 bg-iron p-6 shadow-2xl">
        <h2 id="customer-form-title" className="industrial-title text-2xl">
          {customer ? "EDIT CUSTOMER" : "NEW CUSTOMER"}
        </h2>
        <div>
          <label htmlFor="customer-name" className={label}>NAME *</label>
          <input
            id="customer-name"
            required
            maxLength={200}
            value={name}
            onChange={(e) => setName(e.target.value)}
            className={inputClass}
            autoFocus
          />
        </div>
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div>
            <label htmlFor="customer-email" className={label}>EMAIL</label>
            <input
              id="customer-email"
              type="email"
              maxLength={200}
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className={inputClass}
            />
          </div>
          <div>
            <label htmlFor="customer-phone" className={label}>PHONE</label>
            <input
              id="customer-phone"
              maxLength={50}
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              className={inputClass}
            />
          </div>
        </div>
        <div>
          <label htmlFor="customer-address" className={label}>ADDRESS</label>
          <textarea
            id="customer-address"
            rows={3}
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            className={inputClass}
          />
        </div>
        {customer && <p className="text-xs text-cream/50">A field left blank is cleared.</p>}
        {error && <ErrorNote message={error} />}
        <div className="flex justify-end gap-3 pt-2">
          <button type="button" onClick={onClose} disabled={busy} className="px-4 py-2 text-xs font-black text-cream/70 hover:text-cream">
            BACK
          </button>
          <button type="submit" disabled={busy || !name.trim()} className={primaryButton}>
            {busy ? "SAVING…" : customer ? "SAVE CHANGES" : "CREATE CUSTOMER"}
          </button>
        </div>
      </form>
    </div>
  );
}
