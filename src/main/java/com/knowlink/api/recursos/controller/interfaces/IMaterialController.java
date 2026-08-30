package com.knowlink.api.recursos.controller.interfaces;

import com.knowlink.api.recursos.dto.MaterialResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RequestMapping("/api/v1/materials")
@Tag(name = "Academic Materials", description = "Endpoints for managing academic materials")
public interface IMaterialController {

    @Operation(summary = "Upload academic material", description = "Upload a file linked to a specific subject")
    @ApiResponse(responseCode = "201", description = "Material uploaded successfully")
    @ApiResponse(responseCode = "400", description = "Invalid input")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ResponseEntity<MaterialResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam("subjectId") UUID subjectId,
            @AuthenticationPrincipal UserDetails userDetails);

    @Operation(summary = "List materials by subject", description = "Get all active materials for a subject")
    @ApiResponse(responseCode = "200", description = "Materials retrieved successfully")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @GetMapping
    ResponseEntity<List<MaterialResponse>> listBySubject(
            @Parameter(description = "Subject ID") @RequestParam UUID subjectId,
            @AuthenticationPrincipal UserDetails userDetails);

    @Operation(summary = "Download material", description = "Get signed URL for downloading a material")
    @ApiResponse(responseCode = "200", description = "Download URL generated")
    @ApiResponse(responseCode = "403", description = "Access denied")
    @ApiResponse(responseCode = "404", description = "Material not found")
    @GetMapping("/{id}/download")
    ResponseEntity<String> download(
            @Parameter(description = "Material ID") @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails);
}
