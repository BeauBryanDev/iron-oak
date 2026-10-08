package com.ironoak.dto.request;

import com.ironoak.domain.enums.ComplaintStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateComplaintStatusRequest(@NotNull ComplaintStatus status) {
}
