package com.knowlink.api.admin.dashboard.services.interfaces;

import com.knowlink.api.admin.dashboard.controllers.responses.AdminNavigationBadgesResponse;
import com.knowlink.api.admin.dashboard.controllers.responses.DashboardSummaryResponse;
import com.knowlink.api.admin.dashboard.data.enums.DashboardRange;

public interface IAdminDashboardService {

    AdminNavigationBadgesResponse getBadges();

    DashboardSummaryResponse getSummary(DashboardRange range);
}
