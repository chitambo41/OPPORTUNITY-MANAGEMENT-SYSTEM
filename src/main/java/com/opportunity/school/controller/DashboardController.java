package com.opportunity.school.controller;

import com.opportunity.school.dto.DashboardDtos;
import com.opportunity.school.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/admin/dashboard")
    public DashboardDtos.AdminDashboardDto admin() {
        return dashboardService.adminDashboard();
    }

    @GetMapping("/teacher/dashboard")
    public DashboardDtos.TeacherDashboardDto teacher() {
        return dashboardService.teacherDashboard();
    }
}
