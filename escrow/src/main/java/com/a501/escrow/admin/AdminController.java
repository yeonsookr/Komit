package com.a501.escrow.admin;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {
    private final AdminDashboardService dashboardService;

    public AdminController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/admin/login")
    String login() {
        return "admin/login";
    }

    @GetMapping("/admin")
    String dashboard(Model model) {
        model.addAttribute("dashboard", dashboardService.dashboard());
        return "admin/dashboard";
    }
}
