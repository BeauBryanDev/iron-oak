package com.ironoak.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ironoak.dto.request.CreateSupportTicketRequest;
import com.ironoak.dto.response.SupportTicketResponse;
import com.ironoak.services.SupportTicketService;
import com.ironoak.agent.PiperAssistant;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/** Piper's human-support escalation tool. */
@Component
public class SupportTools {

    private final SupportTicketService tickets;

    public SupportTools(SupportTicketService tickets) {
        this.tickets = tickets;
    }

    @Tool(name = "escalate_to_human", description = "Open a human-support ticket for a complaint, refund request or issue you cannot resolve (it never refunds, changes payments or deletes records). Confirm with the customer first. Include their email if given and the order number when the ticket is about an order. Give the customer the returned ticket_number.")
    public EscalationResult escalateToHuman(
            @ToolParam(description = "Short reason for the escalation, for example complaint, "
                    + "refund request, or unresolved technical issue.") String reason,
            @ToolParam(description = "Concise summary of the customer's request and the relevant "
                    + "context for the human support team.") String summary,
            @ToolParam(description = "Customer email address, only when the customer has "
                    + "provided one.", required = false) String customer_email,
            @ToolParam(description = "Order number (IO-...) when the ticket is about an order.",
                    required = false) String order_number,
            ToolContext toolContext) {

        CreateSupportTicketRequest request = new CreateSupportTicketRequest(
                customer_email,
                chatSessionId(toolContext),
                reason,
                summary);
        SupportTicketResponse ticket = tickets.createFromChat(request, order_number);

        return new EscalationResult(ticket.id(), ticket.ticketNumber());
    }

    /** The chat this call belongs to, put in the ToolContext by PiperAssistant. */
    private static Long chatSessionId(ToolContext toolContext) {

        if (toolContext == null || toolContext.getContext() == null) {
            return null;
        }
        Object id = toolContext.getContext().get(PiperAssistant.CHAT_SESSION_ID);
        return id instanceof Long sessionId ? sessionId : null;
    }

    public record EscalationResult(
            @JsonProperty("ticket_id") Long ticketId,
            @JsonProperty("ticket_number") String ticketNumber) {
    }
}
