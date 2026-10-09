package com.ironoak.dto.request;

import java.time.LocalDate;

/** Query parameters of GET /api/admin/customers; q matches name, email or phone. */
public record CustomerFilter(LocalDate from, LocalDate to, String q) {
}
