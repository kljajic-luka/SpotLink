package com.spotlink.admin;

import com.spotlink.core.ApiPage;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping({"/admin/dashboard/summary", "/v1/admin/dashboard/summary"})
    AdminDtos.AdminDashboardSummary summary() {
        return adminService.summary();
    }

    @GetMapping({"/admin/users", "/v1/admin/users"})
    ApiPage<AdminDtos.AdminUserSummary> users(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminService.users(page, size);
    }

    @GetMapping({"/admin/audit-events", "/v1/admin/audit-events"})
    ApiPage<AdminDtos.AdminAuditEvent> auditEvents(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return adminService.auditEvents(page, size);
    }
}
