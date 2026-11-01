package com.knowlink.api.admin.dashboard.controllers.interfaces;

import com.knowlink.api.admin.dashboard.controllers.responses.AdminNavigationBadgesResponse;
import com.knowlink.api.admin.dashboard.controllers.responses.DashboardSummaryResponse;
import com.knowlink.api.admin.dashboard.data.enums.DashboardRange;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@RequestMapping("/api/v1/admin")
@Tag(name = "Admin navegación y home", description = "Contadores del navbar y resumen de la home del administrador")
public interface IAdminDashboardController {

    @GetMapping("/navigation/badges")
    @Operation(summary = "Consultar los contadores de pendientes del navbar")
    @ApiResponse(responseCode = "200", description = "Cantidad de denuncias y reclamos pendientes")
    @ApiResponse(responseCode = "403", description = "Operación exclusiva para administradores")
    AdminNavigationBadgesResponse getBadges();

    @GetMapping("/dashboard/summary")
    @Operation(summary = "Consultar el resumen de la home del administrador")
    @ApiResponse(responseCode = "200", description = "Resumen de reclamos, denuncias y rendimiento")
    @ApiResponse(responseCode = "400", description = "Rango inválido; debe ser WEEK o MONTH")
    @ApiResponse(responseCode = "403", description = "Operación exclusiva para administradores")
    DashboardSummaryResponse getSummary(
            @RequestParam(defaultValue = "WEEK") DashboardRange range);
}
