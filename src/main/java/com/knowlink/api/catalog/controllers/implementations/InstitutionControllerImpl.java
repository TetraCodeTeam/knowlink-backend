package com.knowlink.api.catalog.controllers.implementations;

import com.knowlink.api.catalog.controllers.interfaces.IInstitutionController;
import com.knowlink.api.catalog.controllers.requests.CreateInstitutionRequest;
import com.knowlink.api.catalog.controllers.responses.InstitutionResponse;
import com.knowlink.api.catalog.data.models.Institution;
import com.knowlink.api.catalog.services.interfaces.IInstitutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InstitutionControllerImpl implements IInstitutionController {

    private final IInstitutionService institutionService;

    @Override
    public InstitutionResponse createInstitution(CreateInstitutionRequest request) {
        Institution institution = institutionService.createInstitution(request.name());
        return new InstitutionResponse(institution.getInstitutionId(), institution.getName());
    }

    @Override
    public List<InstitutionResponse> getInstitutions() {
        return institutionService.findAll().stream()
                .map(institution -> new InstitutionResponse(institution.getInstitutionId(), institution.getName()))
                .toList();
    }
}