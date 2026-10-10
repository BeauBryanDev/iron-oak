package com.ironoak.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why staff cannot take the job; shown to the customer. */
public record DeclineServiceQuoteRequest(@NotBlank @Size(max = 2000) String note) {
}
