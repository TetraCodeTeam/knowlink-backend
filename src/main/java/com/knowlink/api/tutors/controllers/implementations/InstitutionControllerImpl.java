package com.knowlink.api.tutors.controllers.implementations;

import com.knowlink.api.tutors.controllers.interfaces.IInstitutionController;
import com.knowlink.api.tutors.controllers.responses.InstitutionResponse;
import com.knowlink.api.tutors.services.interfaces.IInstitutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class InstitutionControllerImpl implements IInstitutionController {

    private final IInstitutionService institutionService;

    @Override
    public List<InstitutionResponse> getAllInstitutions() {
        return institutionService.findAll().stream()
                .map(institution -> new InstitutionResponse(institution.getInstitutionId(), institution.getName()))
                .toList();
    }
}
