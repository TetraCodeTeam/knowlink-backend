package com.knowlink.api.recursos.controller.implementations;

import com.knowlink.api.recursos.controller.interfaces.IMaterialController;
import com.knowlink.api.recursos.dto.MaterialResponse;
import com.knowlink.api.recursos.dto.MaterialUploadRequest;
import com.knowlink.api.recursos.service.interfaces.IMaterialService;
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
    @PreAuthorize("hasRole('STUDENT') or hasRole('TUTOR')")
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
}
