import { request } from "./http";
import type { CreateSupportTicketRequest, SupportTicketResponse } from "./types";

/**
 * Opens a support ticket (public, rate limited per IP). Staff see it in their queue; the
 * customer gets an answer by email within 1 business day.
 */
export function createSupportTicket(body: CreateSupportTicketRequest): Promise<SupportTicketResponse> {

  return request("/api/support-tickets",
    {
      method: "POST",
      body
    });
}
