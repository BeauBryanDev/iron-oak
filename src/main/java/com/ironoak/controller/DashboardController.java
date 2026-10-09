package com.ironoak.controller;

import com.ironoak.dto.response.DashboardKPIResponse;
import com.ironoak.services.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Staff-only; SecurityConfig requires ROLE_ADMIN for /api/dashboard/**. */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/kpis")
    public DashboardKPIResponse kpis() {
        return dashboard.kpis();
    }
}
