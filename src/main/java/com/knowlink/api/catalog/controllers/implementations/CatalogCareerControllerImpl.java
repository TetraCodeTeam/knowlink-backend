package com.knowlink.api.catalog.controllers.implementations;

import com.knowlink.api.catalog.controllers.interfaces.ICatalogCareerController;
import com.knowlink.api.catalog.controllers.requests.CreateCareerRequest;
import com.knowlink.api.catalog.controllers.responses.CatalogCareerResponse;
import com.knowlink.api.catalog.services.interfaces.ICatalogCareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CatalogCareerControllerImpl implements ICatalogCareerController {

    private final ICatalogCareerService catalogCareerService;

    @Override
    public CatalogCareerResponse createCareer(UUID institutionId, CreateCareerRequest request) {
        return catalogCareerService.createCareer(institutionId, request);
    }

    @Override
    public List<CatalogCareerResponse> getCareers(UUID institutionId) {
        return catalogCareerService.findByInstitution(institutionId);
    }
}