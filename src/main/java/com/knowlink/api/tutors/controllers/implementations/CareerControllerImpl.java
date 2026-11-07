package com.knowlink.api.tutors.controllers.implementations;

import com.knowlink.api.tutors.controllers.interfaces.ICareerController;
import com.knowlink.api.tutors.controllers.responses.CareerResponse;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CareerControllerImpl implements ICareerController {

    private final ICareerService careerService;

    @Override
    public List<CareerResponse> getAllCareers(UUID institutionId) {
        return careerService.findRegularByInstitution(institutionId).stream()
                .map(career -> new CareerResponse(career.getCareerId(), career.getName()))
                .toList();
    }
}
