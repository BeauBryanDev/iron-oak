package com.ironoak.agent;

import com.ironoak.agent.tools.SupportTools;
import com.ironoak.dto.response.SupportTicketResponse;
import com.ironoak.domain.enums.TicketStatus;
import com.ironoak.services.SupportTicketService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportToolsTest {

    @Test
    void createsSupportTicketAndReturnsTicketId() {
        SupportTicketService tickets = mock(SupportTicketService.class);
        SupportTools tools = new SupportTools(tickets);
        when(tickets.create(argThat(request ->
                "Refund request".equals(request.reason())
                        && "Customer requests a refund for order IO-123".equals(request.summary())
                        && "jane@example.com".equals(request.customerEmail())
                        && request.chatSessionId() == null)))
                .thenReturn(new SupportTicketResponse(91L, TicketStatus.OPEN, "Jane", "jane@example.com",
                        null, "Refund request", "Customer requests a refund for order IO-123",
                        OffsetDateTime.now()));

        SupportTools.EscalationResult result = tools.escalateToHuman(
                "Refund request", "Customer requests a refund for order IO-123", "jane@example.com");

        assertThat(result.ticketId()).isEqualTo(91L);
        verify(tickets).create(argThat(request -> "jane@example.com".equals(request.customerEmail())));
    }

    @Test
    void supportsGuestEscalationWithoutEmail() {
        SupportTicketService tickets = mock(SupportTicketService.class);
        SupportTools tools = new SupportTools(tickets);
        when(tickets.create(argThat(request -> request.customerEmail() == null)))
                .thenReturn(new SupportTicketResponse(92L, TicketStatus.OPEN, null, null, null,
                        "Complaint", "Unresolved complaint", OffsetDateTime.now()));

        assertThat(tools.escalateToHuman("Complaint", "Unresolved complaint", null).ticketId())
                .isEqualTo(92L);
        verify(tickets).create(argThat(request -> request.customerEmail() == null
                && request.chatSessionId() == null));
    }
}
