package com.ironoak.agent;

import com.ironoak.agent.tools.SupportTools;
import com.ironoak.dto.response.SupportTicketResponse;
import com.ironoak.domain.enums.TicketStatus;
import com.ironoak.services.SupportTicketService;
import org.junit.jupiter.api.Test;
import com.ironoak.agent.PiperAssistant;
import org.springframework.ai.chat.model.ToolContext;
import java.util.Map;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

class SupportToolsTest {

    @Test
    void passesTheChatSessionAndOrderNumberAndReturnsTheTicketNumber() {
        SupportTicketService tickets = mock(SupportTicketService.class);
        SupportTools tools = new SupportTools(tickets);
        when(tickets.createFromChat(argThat(request ->
                        "Refund request".equals(request.reason())
                                && request.customerEmail() == null
                                && Long.valueOf(7L).equals(request.chatSessionId())),
                eq("IO-20261014-CBJFRZ97")))
                .thenReturn(new SupportTicketResponse(91L,
                        "ST-20261014-ABCD2345",
                        TicketStatus.OPEN,
                        "Jane",
                        "jane@example.com",
                        7L,
                        "Refund request",
                        "Customer asks for a refund",
                        OffsetDateTime.now()));

        SupportTools.EscalationResult result = tools.escalateToHuman(
                "Refund request",
                "Customer asks for a refund",
                null,
                "IO-20261014-CBJFRZ97",
                new ToolContext(Map.of(PiperAssistant.CHAT_SESSION_ID, 7L)));

        assertThat(result.ticketNumber()).isEqualTo("ST-20261014-ABCD2345");
    }

    @Test
    void supportsGuestEscalationWithoutEmailOrSession() {
        SupportTicketService tickets = mock(SupportTicketService.class);
        SupportTools tools = new SupportTools(tickets);
        when(tickets.createFromChat(argThat(request -> request.customerEmail() == null
                        && request.chatSessionId() == null),
                isNull()))
                .thenReturn(new SupportTicketResponse(92L,
                        "ST-20261014-EFGH6789",
                        TicketStatus.OPEN,
                        null,
                        null,
                        null,
                        "Complaint",
                        "Unresolved complaint",
                        OffsetDateTime.now()));

        assertThat(tools.escalateToHuman("Complaint", "Unresolved complaint", null, null, null).ticketId())
                .isEqualTo(92L);
    }
}
