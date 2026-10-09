package com.ironoak.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Raised when a conversation is escalated to a person. Both ids and the email are optional. */
public record CreateSupportTicketRequest(
        @Email @Size(max = 200) String customerEmail,
        Long chatSessionId,
        @NotBlank @Size(max = 200) String reason,
        @NotBlank String summary) {
}
