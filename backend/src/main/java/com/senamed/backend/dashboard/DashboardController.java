package com.senamed.backend.dashboard;

import com.senamed.backend.dashboard.dto.DashboardReportsResponse;
import com.senamed.backend.dashboard.dto.DashboardSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/reports")
    public DashboardReportsResponse reports(@RequestParam(defaultValue = "14") int days) {
        return dashboardService.getReports(days);
    }
}
