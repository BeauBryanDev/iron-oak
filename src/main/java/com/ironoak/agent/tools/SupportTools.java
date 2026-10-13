package com.ironoak.agent.tools;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ironoak.dto.request.CreateSupportTicketRequest;
import com.ironoak.dto.response.SupportTicketResponse;
import com.ironoak.services.SupportTicketService;
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

    @Tool(name = "escalate_to_human", description = "Create an OPEN human-support ticket for a complaint, refund request, or issue Piper cannot resolve. This only creates an escalation; it does not issue a refund, change a payment, or delete a record. Ask for customer confirmation before calling. Include the customer email when available so support can identify the customer. After success, provide contact@tensorgeek.com when relevant.")
    public EscalationResult escalateToHuman(
            @ToolParam(description = "Short reason for the escalation, for example complaint, refund request, or unresolved technical issue.") String reason,
            @ToolParam(description = "Concise summary of the customer's request and the relevant context for the human support team.") String summary,
            @ToolParam(description = "Optional customer email address. Include it when the customer has provided one.", required = false) String customer_email) {

        SupportTicketResponse ticket = tickets.create(
                new CreateSupportTicketRequest(customer_email, null, reason, summary));

        return new EscalationResult(ticket.id());
    }

    public record EscalationResult(@JsonProperty("ticket_id") Long ticketId) {
    }
}
