package com.knowlink.api.tutors.controllers.implementations;

import com.knowlink.api.tutors.controllers.interfaces.ICareerController;
import com.knowlink.api.tutors.controllers.responses.CareerResponse;
import com.knowlink.api.tutors.services.interfaces.ICareerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CareerControllerImpl implements ICareerController {

    private final ICareerService careerService;

    @Override
    public List<CareerResponse> getAllCareers() {
        return careerService.findAll().stream()
                .map(c -> new CareerResponse(c.getCareerId(), c.getName()))
                .toList();
    }
}