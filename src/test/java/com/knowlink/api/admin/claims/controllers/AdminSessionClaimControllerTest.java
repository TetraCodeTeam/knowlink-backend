package com.knowlink.api.admin.claims.controllers;

import com.knowlink.api.admin.claims.controllers.responses.ClaimContactInfoResponse;
import com.knowlink.api.admin.claims.controllers.responses.ClaimStatusCountsResponse;
import com.knowlink.api.admin.claims.controllers.responses.PartyContactResponse;
import com.knowlink.api.admin.claims.controllers.responses.SessionClaimListResponse;
import com.knowlink.api.admin.claims.services.interfaces.IAdminSessionClaimService;
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.exceptions.custom_exceptions.FundsExecutionException;
import com.knowlink.api.exceptions.custom_exceptions.NotAssignedToAdminException;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * HTTP contract of the session claim endpoints: status codes, error codes, body validation and the
 * no-store cache policy of the personal data endpoint. The service layer is mocked.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSessionClaimControllerTest {

    private static final String BASE = "/api/v1/admin/session-claims";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IAdminSessionClaimService adminSessionClaimService;

    private UserPrincipal adminPrincipal;
    private UUID claimId;

    @BeforeEach
    void setUp() {
        User admin = User.builder()
                .userId(UUID.randomUUID())
                .fullName("Admin Claims")
                .email("admin.claims." + UUID.randomUUID() + "@test.com")
                .password("hashed")
                .role(Role.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
        adminPrincipal = new UserPrincipal(admin);
        claimId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Listado: devuelve la página y los contadores por estado")
    void list_returnsPageAndStatusCounts() throws Exception {
        when(adminSessionClaimService.list(any(), anyBoolean(), any(), any(), any(), any(), any()))
                .thenReturn(new SessionClaimListResponse(
                        new PagedResponse<>(List.of(), 0, 10, 0, 0, false),
                        new ClaimStatusCountsResponse(2, 1, 5)));

        mockMvc.perform(get(BASE).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.content").isArray())
                .andExpect(jsonPath("$.statusCounts.open").value(2))
                .andExpect(jsonPath("$.statusCounts.resolved").value(5));
    }

    @Test
    @DisplayName("Listado: un estado desconocido devuelve 400 VALIDATION_ERROR")
    void list_withUnknownStatus_returns400() throws Exception {
        mockMvc.perform(get(BASE).param("status", "PENDING").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Detalle: reclamo inexistente devuelve 404 CLAIM_NOT_FOUND")
    void detail_whenClaimDoesNotExist_returns404() throws Exception {
        when(adminSessionClaimService.getDetail(any(), any()))
                .thenThrow(new ResourceNotFoundException(
                        "CLAIM_NOT_FOUND",
                        "El reclamo no existe.",
                        "Session claim not found for id: " + claimId));

        mockMvc.perform(get(BASE + "/" + claimId).with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("CLAIM_NOT_FOUND"));
    }

    @Test
    @DisplayName("Tomar: disputa ya tomada devuelve 409 CASE_ALREADY_TAKEN")
    void assign_whenDisputeAlreadyTaken_returns409() throws Exception {
        when(adminSessionClaimService.assign(any(), any()))
                .thenThrow(new BusinessConflictException(
                        "CASE_ALREADY_TAKEN",
                        "Otro administrador ya tomó esta solicitud.",
                        "Claim dispute is not open anymore"));

        mockMvc.perform(post(BASE + "/" + claimId + "/assign").with(admin()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("CASE_ALREADY_TAKEN"));
    }

    @Test
    @DisplayName("Resolver: falta favoredParty y devuelve 400 sin llamar al servicio")
    void resolve_withoutFavoredParty_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/" + claimId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"justification\":\"El tutor no se conectó\"}")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));

        verify(adminSessionClaimService, never()).resolve(any(), any(), any());
    }

    @Test
    @DisplayName("Resolver: justificación en blanco devuelve 400")
    void resolve_withBlankJustification_returns400() throws Exception {
        mockMvc.perform(post(BASE + "/" + claimId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoredParty\":\"TUTOR\",\"justification\":\"   \"}")
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Resolver: justificación de más de 1000 caracteres devuelve 400")
    void resolve_withTooLongJustification_returns400() throws Exception {
        String body = "{\"favoredParty\":\"STUDENT\",\"justification\":\"" + "x".repeat(1001) + "\"}";

        mockMvc.perform(post(BASE + "/" + claimId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Resolver: una falla de fondos devuelve 502 FUNDS_EXECUTION_FAILED")
    void resolve_whenFundsFail_returns502() throws Exception {
        when(adminSessionClaimService.resolve(any(), any(), any()))
                .thenThrow(new FundsExecutionException("Gateway rejected the transfer"));

        mockMvc.perform(post(BASE + "/" + claimId + "/resolve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"favoredParty\":\"TUTOR\",\"justification\":\"Clase dictada\"}")
                        .with(admin()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.detail").value("FUNDS_EXECUTION_FAILED"));
    }

    @Test
    @DisplayName("Contacto: devuelve ambas partes con Cache-Control no-store")
    void contactInfo_returnsContactsWithNoStore() throws Exception {
        when(adminSessionClaimService.getContactInfo(any(), any()))
                .thenReturn(new ClaimContactInfoResponse(
                        new PartyContactResponse("Tutor Demo", "tutor@test.com", "1155550000", "30111222", "TUT-1", "Sistemas"),
                        new PartyContactResponse("Alumno Demo", "alumno@test.com", "1155551111", "40111222", "ALU-1", "Sistemas")));

        mockMvc.perform(get(BASE + "/" + claimId + "/contact-info").with(admin()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.tutor.email").value("tutor@test.com"))
                .andExpect(jsonPath("$.student.email").value("alumno@test.com"));
    }

    @Test
    @DisplayName("Contacto: reclamo asignado a otro administrador devuelve 403")
    void contactInfo_whenAnotherAdminOwnsTheClaim_returns403() throws Exception {
        when(adminSessionClaimService.getContactInfo(any(), any()))
                .thenThrow(new NotAssignedToAdminException("Claim assigned to another admin"));

        mockMvc.perform(get(BASE + "/" + claimId + "/contact-info").with(admin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("NOT_ASSIGNED_TO_ADMIN"));
    }

    private RequestPostProcessor admin() {
        return authentication(new UsernamePasswordAuthenticationToken(
                adminPrincipal, null, adminPrincipal.getAuthorities()));
    }
}