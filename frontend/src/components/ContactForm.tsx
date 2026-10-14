"use client";

import { useState, type FormEvent } from "react";
import { CheckCircle2, Send } from "lucide-react";

import { ApiError } from "@/lib/api/http";
import { createSupportTicket } from "@/lib/api/support";

/** The topic becomes the ticket's reason, so staff can sort the queue at a glance. */
const TOPICS = [
  "Order or delivery question",
  "Product question",
  "Warranty claim",
  "Return or refund",
  "Technical service or machine problem",
  "Milling machine quotation",
  "Complaint",
  "Other",
];

const MAX_MESSAGE = 4000;

const fieldClass =
  "w-full border border-orange/40 bg-[#17110a] px-3 py-3 text-cream outline-none "
  + "placeholder:text-cream/30 focus:border-orange disabled:opacity-60";

/**
 * Opens a support ticket (POST /api/support-tickets): it lands in the staff support queue,
 * and Customer Service answers by email within 1 business day.
 */
export default function ContactForm() {
  const [email, setEmail] = useState("");
  const [topic, setTopic] = useState(TOPICS[0]);
  const [orderNumber, setOrderNumber] = useState("");
  const [message, setMessage] = useState("");
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [ticketNumber, setTicketNumber] = useState<string | null>(null);

  const valid = /^\S+@\S+\.\S+$/.test(email.trim()) && message.trim().length >= 10;

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!valid || sending) return;
    setSending(true);
    setError(null);

    const reference = orderNumber.trim() ? `Order ${orderNumber.trim().toUpperCase()}\n\n` : "";

    try {
      const ticket = await createSupportTicket({
        customerEmail: email.trim(),
        reason: topic,
        summary: reference + message.trim(),
      });
      setTicketNumber(ticket.ticketNumber);
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 429) {
        setError("Too many messages from this connection. Please wait a minute and try again.");
      } else {
        setError(cause instanceof ApiError ? cause.message : "We could not send your message. Please try again.");
      }
    } finally {
      setSending(false);
    }
  }

  if (ticketNumber !== null) {
    return (
      <div className="border border-orange/40 bg-iron p-8" role="status">
        <CheckCircle2 className="h-10 w-10 text-orange" />
        <h3 className="industrial-title mt-4 text-3xl">MESSAGE RECEIVED</h3>
        <p className="mt-3 text-cream/80">
          Your case number is <b className="text-orange-light">{ticketNumber}</b>. Customer Service will answer at{" "}
          <b>{email.trim()}</b> within 1 business day.
        </p>
        <button
          type="button"
          onClick={() => {
            setTicketNumber(null);
            setMessage("");
            setOrderNumber("");
          }}
          className="mt-6 text-sm font-black text-orange hover:text-orange-light"
        >
          SEND ANOTHER MESSAGE
        </button>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="space-y-5 border border-orange/30 bg-iron p-8">
      <div className="grid gap-5 sm:grid-cols-2">
        <label className="block">
          <span className="text-xs font-bold tracking-wide text-cream/60">YOUR EMAIL *</span>
          <input
            type="email"
            required
            maxLength={200}
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            disabled={sending}
            placeholder="you@company.com"
            className={`${fieldClass} mt-1`}
          />
        </label>
        <label className="block">
          <span className="text-xs font-bold tracking-wide text-cream/60">ORDER NUMBER (IF ANY)</span>
          <input
            maxLength={40}
            value={orderNumber}
            onChange={(e) => setOrderNumber(e.target.value)}
            disabled={sending}
            placeholder="IO-20261013-ABCD2345"
            className={`${fieldClass} mt-1 font-mono`}
          />
        </label>
      </div>

      <label className="block">
        <span className="text-xs font-bold tracking-wide text-cream/60">TOPIC</span>
        <select value={topic} onChange={(e) => setTopic(e.target.value)} disabled={sending} className={`${fieldClass} mt-1`}>
          {TOPICS.map((t) => <option key={t} value={t}>{t}</option>)}
        </select>
      </label>

      <label className="block">
        <span className="flex justify-between text-xs font-bold tracking-wide text-cream/60">
          <span>HOW CAN WE HELP? *</span>
          <span className="font-normal text-cream/40">{message.length}/{MAX_MESSAGE}</span>
        </span>
        <textarea
          required
          rows={6}
          maxLength={MAX_MESSAGE}
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          disabled={sending}
          placeholder="Tell us what happened, the product or machine involved, and how we can reach you."
          className={`${fieldClass} mt-1 resize-y`}
        />
      </label>

      <div aria-live="polite">
        {error && (
          <p role="alert" className="border border-red-400/50 bg-red-950/40 p-3 text-sm text-red-200">{error}</p>
        )}
      </div>

      <button
        type="submit"
        disabled={!valid || sending}
        className="flex items-center gap-2 bg-orange px-6 py-3 text-sm font-black text-black hover:bg-orange-light disabled:cursor-not-allowed disabled:opacity-50"
      >
        <Send className="h-4 w-4" /> {sending ? "SENDING…" : "SEND MESSAGE"}
      </button>
      <p className="text-xs text-cream/40">
        We use your email only to answer this message. Never send passwords or card numbers.
      </p>
    </form>
  );
}
