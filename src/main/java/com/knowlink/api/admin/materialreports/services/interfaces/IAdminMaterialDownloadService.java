package com.knowlink.api.admin.materialreports.services.interfaces;

import com.knowlink.api.admin.materialreports.controllers.responses.DownloadUrlResponse;

import java.util.UUID;

public interface IAdminMaterialDownloadService {

    DownloadUrlResponse generateDownloadUrl(UUID caseId, UUID adminUserId);
}