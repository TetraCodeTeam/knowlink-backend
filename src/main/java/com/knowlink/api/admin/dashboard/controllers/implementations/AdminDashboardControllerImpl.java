package com.knowlink.api.admin.dashboard.controllers.implementations;

import com.knowlink.api.admin.dashboard.controllers.interfaces.IAdminDashboardController;
import com.knowlink.api.admin.dashboard.controllers.responses.AdminNavigationBadgesResponse;
import com.knowlink.api.admin.dashboard.controllers.responses.DashboardSummaryResponse;
import com.knowlink.api.admin.dashboard.data.enums.DashboardRange;
import com.knowlink.api.admin.dashboard.services.interfaces.IAdminDashboardService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminDashboardControllerImpl implements IAdminDashboardController {

    private final IAdminDashboardService adminDashboardService;

    @Override
    public AdminNavigationBadgesResponse getBadges() {
        return adminDashboardService.getBadges();
    }

    @Override
    public DashboardSummaryResponse getSummary(DashboardRange range) {
        return adminDashboardService.getSummary(range);
    }
}
