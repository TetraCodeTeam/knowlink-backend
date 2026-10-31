package com.knowlink.api.admin.materialreports.controllers;

import com.knowlink.api.admin.materialreports.controllers.responses.DownloadUrlResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialInfoResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseDetailResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseListItemResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.MaterialReportCaseListResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.PermissionsResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.StatusCountsResponse;
import com.knowlink.api.admin.materialreports.controllers.responses.TutorOwnerResponse;
import com.knowlink.api.admin.materialreports.data.enums.MaterialReportCaseStatus;
import com.knowlink.api.admin.materialreports.services.interfaces.IAdminMaterialDownloadService;
import com.knowlink.api.admin.materialreports.services.interfaces.IAdminMaterialReportService;
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.NotAssignedToAdminException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.materials.data.enums.MaterialType;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.security.models.UserPrincipal;
import com.knowlink.api.shared.responses.PagedResponse;
import com.knowlink.api.users.data.enums.AccountStatus;
import com.knowlink.api.users.data.models.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the material report endpoints: status codes, error codes and body validation.
 * The service layer is mocked, so this test only covers the web layer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminMaterialReportControllerTest {

    private static final String BASE = "/api/v1/admin/material-reports";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAdminMaterialReportService adminMaterialReportService;

    @MockitoBean
    private IAdminMaterialDownloadService adminMaterialDownloadService;

    private UserPrincipal adminPrincipal;
    private UUID caseId;

    @BeforeEach
    void setUp() {
        User admin = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Admin Material")
                .email("admin.material." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(Role.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        adminPrincipal = new UserPrincipal(admin);
        caseId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Listado: devuelve la página y los contadores por estado")
    void list_returnsPageAndStatusCounts() throws Exception {
        MaterialReportCaseListResponse response = new MaterialReportCaseListResponse(
                new PagedResponse<>(List.of(), 0, 10, 0, 0, false),
                new StatusCountsResponse(3, 1, 2, 4));
        when(adminMaterialReportService.listCases(
                any(), anyBoolean(), any(), any(), any(), any(), any())).thenReturn(response);

        mockMvc.perform(get(BASE).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.size").value(10))
                .andExpect(jsonPath("$.page.content").isArray())
                .andExpect(jsonPath("$.statusCounts.pending").value(3))
                .andExpect(jsonPath("$.statusCounts.dismissed").value(4));
    }

    @Test
    @DisplayName("Listado: un estado desconocido devuelve 400 VALIDATION_ERROR")
    void list_withUnknownStatus_returns400() throws Exception {
        mockMvc.perform(get(BASE).param("status", "ARCHIVED").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Detalle: caso inexistente devuelve 404 MATERIAL_REPORT_CASE_NOT_FOUND")
    void detail_whenCaseDoesNotExist_returns404() throws Exception {
        when(adminMaterialReportService.getCaseDetail(any(), any()))
                .thenThrow(new ResourceNotFoundException(
                        "MATERIAL_REPORT_CASE_NOT_FOUND",
                        "La denuncia no existe.",
                        "Material report case not found: " + caseId));

        mockMvc.perform(get(BASE + "/" + caseId).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("MATERIAL_REPORT_CASE_NOT_FOUND"));
    }

    @Test
    @DisplayName("Tomar: caso ya tomado devuelve 409 CASE_ALREADY_TAKEN")
    void assign_whenCaseAlreadyTaken_returns409() throws Exception {
        when(adminMaterialReportService.assignCase(any(), any()))
                .thenThrow(new BusinessConflictException(
                        "CASE_ALREADY_TAKEN",
                        "Otro administrador ya tomó esta solicitud.",
                        "Material report case is already in review"));

        mockMvc.perform(post(BASE + "/" + caseId + "/assign").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("CASE_ALREADY_TAKEN"));
    }

    @Test
    @DisplayName("Dar de baja: un motivo de más de 500 caracteres devuelve 400 y no llama al servicio")
    void remove_withTooLongReason_returns400() throws Exception {
        String body = "{\"reason\":\"" + "x".repeat(501) + "\"}";

        mockMvc.perform(post(BASE + "/" + caseId + "/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        verify(adminMaterialReportService, never()).removeMaterial(any(), anyString(), any());
    }

    @Test
    @DisplayName("Dar de baja: sin cuerpo envía motivo nulo al servicio")
    void remove_withoutBody_sendsNullReason() throws Exception {
        when(adminMaterialReportService.removeMaterial(any(), any(), any()))
                .thenReturn(detailResponse());

        mockMvc.perform(post(BASE + "/" + caseId + "/remove").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.status").value("REMOVED"));

        verify(adminMaterialReportService).removeMaterial(eq(caseId), isNull(), any());
    }

    @Test
    @DisplayName("Dar de baja: caso asignado a otro administrador devuelve 403 NOT_ASSIGNED_TO_ADMIN")
    void remove_whenAnotherAdminOwnsTheCase_returns403() throws Exception {
        when(adminMaterialReportService.removeMaterial(any(), any(), any()))
                .thenThrow(new NotAssignedToAdminException("Case assigned to another admin"));

        mockMvc.perform(post(BASE + "/" + caseId + "/remove")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Copyright\"}")
                        .with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("NOT_ASSIGNED_TO_ADMIN"));
    }

    @Test
    @DisplayName("Desestimar: caso ya resuelto devuelve 409 CASE_ALREADY_RESOLVED")
    void dismiss_whenCaseAlreadyResolved_returns409() throws Exception {
        when(adminMaterialReportService.dismissCase(any(), any()))
                .thenThrow(new BusinessConflictException(
                        "CASE_ALREADY_RESOLVED",
                        "Esta solicitud ya fue resuelta y no puede modificarse.",
                        "Material report case is already resolved"));

        mockMvc.perform(post(BASE + "/" + caseId + "/dismiss").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("CASE_ALREADY_RESOLVED"));
    }

    @Test
    @DisplayName("Descarga: devuelve la URL firmada y su vigencia")
    void downloadUrl_returnsSignedUrl() throws Exception {
        when(adminMaterialDownloadService.generateDownloadUrl(any(), any()))
                .thenReturn(new DownloadUrlResponse("https://files.test/audit.pdf", 300));

        mockMvc.perform(get(BASE + "/" + caseId + "/download-url").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://files.test/audit.pdf"))
                .andExpect(jsonPath("$.expiresInSeconds").value(300));
    }

    private RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(
                adminPrincipal, null, adminPrincipal.getAuthorities()));
    }

    private MaterialReportCaseDetailResponse detailResponse() {
        MaterialReportCaseListItemResponse summary = new MaterialReportCaseListItemResponse(
                caseId,
                UUID.randomUUID(),
                "material.pdf",
                MaterialType.PDF,
                "Algoritmos",
                "Tutor Demo",
                1,
                MaterialReportCaseStatus.REMOVED,
                LocalDateTime.of(2026, 10, 20, 10, 0),
                null,
                true,
                LocalDateTime.of(2026, 10, 21, 10, 0),
                null);
        return new MaterialReportCaseDetailResponse(
                summary,
                new TutorOwnerResponse("Tutor Demo", "Sistemas", "30111222", "TUT-1"),
                new MaterialInfoResponse("material.pdf", MaterialType.PDF, 1024L,
                        LocalDateTime.of(2026, 10, 19, 10, 0)),
                List.of(),
                new PermissionsResponse(false, false));
    }
}