package com.knowlink.api.materials.controller.implementations;

import com.knowlink.api.materials.controller.interfaces.IMaterialController;
import com.knowlink.api.materials.controller.responses.AccessCheckResponse;
import com.knowlink.api.materials.controller.responses.MaterialResponse;
import com.knowlink.api.materials.controller.requests.MaterialUploadRequest;
import com.knowlink.api.materials.controller.requests.MaterialReportRequest;
import com.knowlink.api.materials.controller.responses.MaterialReportResponse;
import com.knowlink.api.materials.service.interfaces.IMaterialAccessService;
import com.knowlink.api.materials.service.interfaces.IMaterialReportService;
import com.knowlink.api.materials.service.interfaces.IMaterialService;
import com.knowlink.api.security.models.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MaterialControllerImpl implements IMaterialController {

    private final IMaterialService materialService;
    private final IMaterialAccessService materialAccessService;
    private final IMaterialReportService materialReportService;

    @Override
    @PreAuthorize("hasRole('TUTOR')")
    public ResponseEntity<MaterialResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("subjectId") UUID subjectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UserPrincipal principal = (UserPrincipal) userDetails;
        UUID tutorUserId = principal.getUser().getUserId();

        MaterialUploadRequest request = new MaterialUploadRequest(name, subjectId);
        MaterialResponse response = materialService.upload(request, file, tutorUserId);

        return ResponseEntity.status(201)
                .body(response);
    }

    @Override
    @PreAuthorize("hasRole('STUDENT') or hasRole('TUTOR') or hasRole('ADMIN')")
    public ResponseEntity<List<MaterialResponse>> listBySubject(
            @RequestParam UUID subjectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UserPrincipal principal = (UserPrincipal) userDetails;
        UUID userId = principal.getUser().getUserId();
        String role = principal.getUser().getRole().name();

        List<MaterialResponse> materials = materialService.listBySubject(subjectId, userId, role);
        return ResponseEntity.ok(materials);
    }

    @Override
    @PreAuthorize("hasRole('STUDENT') or hasRole('TUTOR')")
    public ResponseEntity<String> download(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        UserPrincipal principal = (UserPrincipal) userDetails;
        UUID userId = principal.getUser().getUserId();
        String role = principal.getUser().getRole().name();

        String signedUrl = materialService.getDownloadUrl(id, userId, role);
        return ResponseEntity.ok(signedUrl);
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public MaterialReportResponse report(UUID id, MaterialReportRequest request, UserDetails userDetails) {
        UserPrincipal principal = (UserPrincipal) userDetails;
        return materialReportService.reportMaterial(principal.getUser().getUserId(), id, request);
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<AccessCheckResponse> checkAccess(
            @PathVariable UUID tutorId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UserPrincipal principal = (UserPrincipal) userDetails;
        UUID studentId = principal.getUser().getUserId();

        boolean accessEnabled = materialAccessService.hasAccess(studentId, tutorId);
        return ResponseEntity.ok(new AccessCheckResponse(accessEnabled));
    }

    @Override
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<List<MaterialResponse>> listAccessibleMaterials(
            @PathVariable UUID tutorId,
            @RequestParam(required = false) UUID subjectId,
            @AuthenticationPrincipal UserDetails userDetails) {

        UserPrincipal principal = (UserPrincipal) userDetails;
        UUID studentId = principal.getUser().getUserId();

        List<MaterialResponse> materials = materialAccessService
                .listAccessibleMaterials(studentId, tutorId, subjectId);
        return ResponseEntity.ok(materials);
    }
}
