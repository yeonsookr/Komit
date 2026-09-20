package com.a501.escrow.admin;

import com.a501.escrow.config.AppProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminController {
    private final AdminDashboardService dashboardService;
    private final String externalPrefix;

    public AdminController(AdminDashboardService dashboardService, AppProperties properties) {
        this.dashboardService = dashboardService;
        String path = properties.publicBaseUrl().getPath();
        this.externalPrefix = path == null || path.isBlank() || "/".equals(path)
                ? "" : path.replaceFirst("/$", "");
    }

    @GetMapping("/")
    String root() {
        return "redirect:" + externalPrefix + "/admin";
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
